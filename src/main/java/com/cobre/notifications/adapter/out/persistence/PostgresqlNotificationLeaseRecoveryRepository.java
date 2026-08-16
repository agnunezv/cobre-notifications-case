package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.ExpiredNotificationLease;
import com.cobre.notifications.application.model.NotificationLeaseRecovery;
import com.cobre.notifications.application.port.outbound.NotificationLeaseRecoveryRepository;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
public class PostgresqlNotificationLeaseRecoveryRepository implements NotificationLeaseRecoveryRepository {

    private static final String LOCK_EXPIRED_LEASES_SQL = """
            SELECT notification.event_id,
                   notification.delivery_cycle,
                   notification.lease_owner,
                   notification.lease_until,
                   attempt.attempt_id,
                   attempt.attempt_number,
                   attempt.started_at
            FROM notification_events AS notification
            LEFT JOIN delivery_attempts AS attempt
              ON attempt.event_id = notification.event_id
             AND attempt.delivery_cycle = notification.delivery_cycle
             AND attempt.finished_at IS NULL
            WHERE notification.delivery_status = 'PROCESSING'
              AND notification.lease_until IS NOT NULL
              AND notification.lease_until <= :expiredAt
            ORDER BY notification.lease_until, notification.event_id
            LIMIT :batchSize
            FOR UPDATE OF notification SKIP LOCKED
            """;

    private static final String CLOSE_ABANDONED_ATTEMPT_SQL = """
            UPDATE delivery_attempts
            SET finished_at = :recoveredAt,
                result = :attemptResult,
                http_status = NULL,
                failure_category = 'WORKER_LEASE_EXPIRED',
                failure_description = 'The worker lease expired before the delivery result was persisted',
                latency_ms = :latencyMs
            WHERE attempt_id = :attemptId
              AND event_id = :eventId
              AND delivery_cycle = :deliveryCycle
              AND attempt_number = :attemptNumber
              AND finished_at IS NULL
            """;

    private static final String RECOVER_DELIVERY_SQL = """
            UPDATE notification_events AS notification
            SET delivery_status = :nextStatus,
                next_attempt_at = :nextAttemptAt,
                lease_owner = NULL,
                lease_until = NULL,
                lease_recovery_pending = :leaseRecoveryPending,
                delivered_at = NULL,
                version = notification.version + 1,
                updated_at = :recoveredAt
            WHERE notification.event_id = :eventId
              AND notification.delivery_cycle = :deliveryCycle
              AND notification.delivery_status = 'PROCESSING'
            """;

    private static final RowMapper<ExpiredNotificationLease> ROW_MAPPER =
            (resultSet, rowNumber) -> new ExpiredNotificationLease(
                    resultSet.getString("event_id"),
                    resultSet.getInt("delivery_cycle"),
                    resultSet.getString("lease_owner"),
                    resultSet.getTimestamp("lease_until").toInstant(),
                    resultSet.getObject("attempt_id", UUID.class),
                    resultSet.getObject("attempt_number", Integer.class),
                    nullableInstant(resultSet.getTimestamp("started_at")));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationLeaseRecoveryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExpiredNotificationLease> lockExpired(Instant expiredAt, int batchSize) {
        return jdbcTemplate.query(
                LOCK_EXPIRED_LEASES_SQL,
                new MapSqlParameterSource()
                        .addValue("expiredAt", Timestamp.from(expiredAt))
                        .addValue("batchSize", batchSize),
                ROW_MAPPER);
    }

    @Override
    public void recover(NotificationLeaseRecovery recovery) {
        MapSqlParameterSource parameters = parameters(recovery);
        if (recovery.expiredLease().hasOpenAttempt()) {
            requireSingleUpdate(
                    jdbcTemplate.update(CLOSE_ABANDONED_ATTEMPT_SQL, parameters),
                    "The abandoned notification attempt changed while recovering its lease");
        }
        requireSingleUpdate(
                jdbcTemplate.update(RECOVER_DELIVERY_SQL, parameters),
                "The notification delivery changed while recovering its lease");
    }

    private MapSqlParameterSource parameters(NotificationLeaseRecovery recovery) {
        ExpiredNotificationLease expiredLease = recovery.expiredLease();
        return new MapSqlParameterSource()
                .addValue("eventId", expiredLease.eventId())
                .addValue("deliveryCycle", expiredLease.deliveryCycle())
                .addValue("attemptId", expiredLease.openAttemptId())
                .addValue("attemptNumber", expiredLease.openAttemptNumber())
                .addValue("attemptResult", DeliveryAttemptResult.RETRYABLE_FAILURE.name())
                .addValue("recoveredAt", Timestamp.from(recovery.recoveredAt()))
                .addValue("latencyMs", latencyMs(expiredLease, recovery.recoveredAt()))
                .addValue("nextStatus", recovery.nextStatus().name())
                .addValue(
                        "nextAttemptAt",
                        recovery.nextAttemptAt() == null ? null : Timestamp.from(recovery.nextAttemptAt()),
                        Types.TIMESTAMP)
                .addValue("leaseRecoveryPending", recovery.nextStatus() == DeliveryStatus.RETRY_SCHEDULED);
    }

    private long latencyMs(ExpiredNotificationLease expiredLease, Instant recoveredAt) {
        if (!expiredLease.hasOpenAttempt()) {
            return 0L;
        }
        return Math.max(
                0L,
                Duration.between(expiredLease.openAttemptStartedAt(), recoveredAt)
                        .toMillis());
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void requireSingleUpdate(int updatedRows, String message) {
        if (updatedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
