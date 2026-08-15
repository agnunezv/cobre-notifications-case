package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.NotificationDeliveryAttemptCompletion;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryCompletionRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;

@Repository
@Validated
public class PostgresqlNotificationDeliveryCompletionRepository
        implements NotificationDeliveryCompletionRepository {

    private static final String LOCK_CURRENT_ATTEMPT_SQL = """
            SELECT attempt.attempt_id
            FROM delivery_attempts AS attempt
            JOIN notification_events AS notification
              ON notification.event_id = attempt.event_id
             AND notification.delivery_cycle = attempt.delivery_cycle
            WHERE attempt.attempt_id = :attemptId
              AND attempt.event_id = :eventId
              AND attempt.delivery_cycle = :deliveryCycle
              AND attempt.attempt_number = :attemptNumber
              AND attempt.finished_at IS NULL
              AND notification.delivery_status = 'PROCESSING'
            FOR UPDATE OF notification, attempt
            """;

    private static final String COMPLETE_ATTEMPT_SQL = """
            UPDATE delivery_attempts
            SET finished_at = :finishedAt,
                result = :attemptResult,
                http_status = :httpStatus,
                failure_category = :failureCategory,
                failure_description = :failureDescription,
                latency_ms = :latencyMs
            WHERE attempt_id = :attemptId
              AND finished_at IS NULL
            """;

    private static final String COMPLETE_DELIVERY_SQL = """
            UPDATE notification_events AS notification
            SET delivery_status = :nextStatus,
                next_attempt_at = :nextAttemptAt,
                lease_owner = NULL,
                lease_until = NULL,
                delivered_at = :deliveredAt,
                version = notification.version + 1,
                updated_at = :finishedAt
            WHERE notification.event_id = :eventId
              AND notification.delivery_cycle = :deliveryCycle
              AND notification.delivery_status = 'PROCESSING'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationDeliveryCompletionRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean completeIfCurrent(NotificationDeliveryAttemptCompletion completion) {
        MapSqlParameterSource parameters = parameters(completion);
        if (!isCurrentAttempt(parameters)) {
            return false;
        }

        requireSingleUpdate(
                jdbcTemplate.update(COMPLETE_ATTEMPT_SQL, parameters),
                "The open notification attempt changed while completing it");
        requireSingleUpdate(
                jdbcTemplate.update(COMPLETE_DELIVERY_SQL, parameters),
                "The notification delivery changed while completing its attempt");
        return true;
    }

    private boolean isCurrentAttempt(MapSqlParameterSource parameters) {
        return !jdbcTemplate.query(
                LOCK_CURRENT_ATTEMPT_SQL,
                parameters,
                (resultSet, rowNumber) -> resultSet.getObject("attempt_id", UUID.class))
                .isEmpty();
    }

    private MapSqlParameterSource parameters(NotificationDeliveryAttemptCompletion completion) {
        var delivery = completion.delivery();
        var outcome = completion.outcome();

        return new MapSqlParameterSource()
                .addValue("attemptId", delivery.attemptId())
                .addValue("eventId", delivery.eventId())
                .addValue("deliveryCycle", delivery.deliveryCycle())
                .addValue("attemptNumber", delivery.attemptNumber())
                .addValue("finishedAt", Timestamp.from(completion.finishedAt()), Types.TIMESTAMP)
                .addValue("attemptResult", outcome.result().name())
                .addValue("httpStatus", outcome.httpStatus(), Types.INTEGER)
                .addValue(
                        "failureCategory",
                        outcome.failureCategory() == null ? null : outcome.failureCategory().name(),
                        Types.VARCHAR)
                .addValue("failureDescription", outcome.failureDescription(), Types.VARCHAR)
                .addValue("latencyMs", outcome.latencyMs())
                .addValue("nextStatus", completion.nextStatus().name())
                .addValue(
                        "nextAttemptAt",
                        completion.nextAttemptAt() == null
                                ? null
                                : Timestamp.from(completion.nextAttemptAt()),
                        Types.TIMESTAMP)
                .addValue(
                        "deliveredAt",
                        completion.nextStatus() == DeliveryStatus.COMPLETED
                                ? Timestamp.from(completion.finishedAt())
                                : null,
                        Types.TIMESTAMP);
    }

    private void requireSingleUpdate(int updatedRows, String message) {
        if (updatedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
