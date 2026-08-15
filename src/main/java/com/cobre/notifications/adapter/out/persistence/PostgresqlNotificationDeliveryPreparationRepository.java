package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.DeliveryPreparationFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryPreparationRepository;
import com.cobre.notifications.domain.model.DeliveryAttemptOrigin;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.NotificationDestination;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresqlNotificationDeliveryPreparationRepository
        implements NotificationDeliveryPreparationRepository {

    private static final String LOCK_CLAIM_SQL = """
            SELECT event_id
            FROM notification_events
            WHERE event_id = :eventId
              AND delivery_cycle = :deliveryCycle
              AND delivery_status = 'PROCESSING'
              AND lease_owner = :workerId
              AND lease_until > :operationAt
            FOR UPDATE
            """;

    private static final String HAS_OPEN_ATTEMPT_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM delivery_attempts
                WHERE event_id = :eventId
                  AND delivery_cycle = :deliveryCycle
                  AND finished_at IS NULL
            )
            """;

    private static final String BIND_DESTINATION_SQL = """
            UPDATE notification_events AS notification
            SET subscription_id = :subscriptionId,
                destination_url_snapshot = :destinationUrl,
                lease_recovery_pending = FALSE,
                version = notification.version + 1,
                updated_at = :operationAt
            WHERE notification.event_id = :eventId
              AND notification.delivery_cycle = :deliveryCycle
              AND notification.delivery_status = 'PROCESSING'
              AND notification.lease_owner = :workerId
              AND notification.lease_until > :operationAt
            """;

    private static final String FAIL_CONFIGURATION_SQL = """
            UPDATE notification_events AS notification
            SET delivery_status = 'FAILED',
                next_attempt_at = NULL,
                lease_owner = NULL,
                lease_until = NULL,
                lease_recovery_pending = FALSE,
                delivered_at = NULL,
                version = notification.version + 1,
                updated_at = :operationAt
            WHERE notification.event_id = :eventId
              AND notification.delivery_cycle = :deliveryCycle
              AND notification.delivery_status = 'PROCESSING'
              AND notification.lease_owner = :workerId
              AND notification.lease_until > :operationAt
            """;

    private static final String NEXT_ATTEMPT_NUMBER_SQL = """
            SELECT COALESCE(MAX(attempt_number), 0) + 1
            FROM delivery_attempts
            WHERE event_id = :eventId
              AND delivery_cycle = :deliveryCycle
            """;

    private static final String INSERT_ATTEMPT_SQL = """
            INSERT INTO delivery_attempts (
                attempt_id,
                event_id,
                delivery_cycle,
                attempt_number,
                origin,
                started_at,
                finished_at,
                result,
                failure_category,
                failure_description,
                latency_ms,
                correlation_id
            ) VALUES (
                :attemptId,
                :eventId,
                :deliveryCycle,
                :attemptNumber,
                :origin,
                :startedAt,
                :finishedAt,
                :result,
                :failureCategory,
                :failureDescription,
                :latencyMs,
                :correlationId
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationDeliveryPreparationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PreparedNotificationDelivery> prepare(
            ClaimedNotificationDelivery claimedDelivery,
            NotificationDestination destination,
            UUID attemptId,
            Instant startedAt) {
        MapSqlParameterSource parameters = claimParameters(claimedDelivery, startedAt)
                .addValue("subscriptionId", destination.subscriptionId())
                .addValue("destinationUrl", destination.endpointUrl().toString());

        if (!lockAvailableClaim(parameters) || hasOpenAttempt(parameters)) {
            return Optional.empty();
        }
        requireSingleUpdate(jdbcTemplate.update(BIND_DESTINATION_SQL, parameters));

        int attemptNumber = nextAttemptNumber(claimedDelivery);
        String correlationId = attemptId.toString();
        insertAttempt(
                claimedDelivery,
                attemptId,
                attemptNumber,
                startedAt,
                null,
                null,
                correlationId);

        return Optional.of(new PreparedNotificationDelivery(
                attemptId,
                claimedDelivery.eventId(),
                claimedDelivery.clientId(),
                claimedDelivery.eventType(),
                claimedDelivery.content(),
                destination,
                claimedDelivery.deliveryCycle(),
                attemptNumber,
                correlationId,
                startedAt));
    }

    @Override
    public void failConfigurationIfClaimIsCurrent(
            ClaimedNotificationDelivery claimedDelivery,
            DeliveryPreparationFailureCategory failureCategory,
            UUID attemptId,
            Instant finishedAt) {
        MapSqlParameterSource parameters = claimParameters(claimedDelivery, finishedAt);
        if (!lockAvailableClaim(parameters) || hasOpenAttempt(parameters)) {
            return;
        }
        requireSingleUpdate(jdbcTemplate.update(FAIL_CONFIGURATION_SQL, parameters));

        int attemptNumber = nextAttemptNumber(claimedDelivery);
        insertAttempt(
                claimedDelivery,
                attemptId,
                attemptNumber,
                finishedAt,
                finishedAt,
                failureCategory,
                attemptId.toString());
    }

    private boolean lockAvailableClaim(MapSqlParameterSource parameters) {
        return !jdbcTemplate.queryForList(LOCK_CLAIM_SQL, parameters, String.class).isEmpty();
    }

    private boolean hasOpenAttempt(MapSqlParameterSource parameters) {
        Boolean hasOpenAttempt = jdbcTemplate.queryForObject(
                HAS_OPEN_ATTEMPT_SQL,
                parameters,
                Boolean.class);
        return Boolean.TRUE.equals(hasOpenAttempt);
    }

    private void requireSingleUpdate(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("The claimed notification changed while preparing its delivery");
        }
    }

    private int nextAttemptNumber(ClaimedNotificationDelivery claimedDelivery) {
        Integer attemptNumber = jdbcTemplate.queryForObject(
                NEXT_ATTEMPT_NUMBER_SQL,
                new MapSqlParameterSource()
                        .addValue("eventId", claimedDelivery.eventId())
                        .addValue("deliveryCycle", claimedDelivery.deliveryCycle()),
                Integer.class);
        if (attemptNumber == null) {
            throw new IllegalStateException("PostgreSQL did not return an attempt number");
        }
        return attemptNumber;
    }

    private void insertAttempt(
            ClaimedNotificationDelivery claimedDelivery,
            UUID attemptId,
            int attemptNumber,
            Instant startedAt,
            Instant finishedAt,
            DeliveryPreparationFailureCategory failureCategory,
            String correlationId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("attemptId", attemptId)
                .addValue("eventId", claimedDelivery.eventId())
                .addValue("deliveryCycle", claimedDelivery.deliveryCycle())
                .addValue("attemptNumber", attemptNumber)
                .addValue("origin", origin(claimedDelivery, attemptNumber).name())
                .addValue("startedAt", Timestamp.from(startedAt))
                .addValue(
                        "finishedAt",
                        finishedAt == null ? null : Timestamp.from(finishedAt),
                        Types.TIMESTAMP)
                .addValue(
                        "result",
                        failureCategory == null ? null : DeliveryAttemptResult.PERMANENT_FAILURE.name(),
                        Types.VARCHAR)
                .addValue(
                        "failureCategory",
                        failureCategory == null ? null : failureCategory.name(),
                        Types.VARCHAR)
                .addValue(
                        "failureDescription",
                        failureCategory == null ? null : failureCategory.description(),
                        Types.VARCHAR)
                .addValue("latencyMs", failureCategory == null ? null : 0L, Types.BIGINT)
                .addValue("correlationId", correlationId);
        jdbcTemplate.update(INSERT_ATTEMPT_SQL, parameters);
    }

    private MapSqlParameterSource claimParameters(
            ClaimedNotificationDelivery claimedDelivery,
            Instant preparedAt) {
        return new MapSqlParameterSource()
                .addValue("eventId", claimedDelivery.eventId())
                .addValue("deliveryCycle", claimedDelivery.deliveryCycle())
                .addValue("workerId", claimedDelivery.workerId())
                .addValue("operationAt", Timestamp.from(preparedAt));
    }

    private DeliveryAttemptOrigin origin(
            ClaimedNotificationDelivery claimedDelivery,
            int attemptNumber) {
        if (claimedDelivery.leaseRecovery()) {
            return DeliveryAttemptOrigin.LEASE_RECOVERY;
        }
        if (attemptNumber > 1) {
            return DeliveryAttemptOrigin.AUTOMATIC_RETRY;
        }
        return claimedDelivery.deliveryCycle() == 1
                ? DeliveryAttemptOrigin.INITIAL
                : DeliveryAttemptOrigin.MANUAL_REPLAY;
    }
}
