package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.port.outbound.NotificationDeliveryBacklogRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresqlNotificationDeliveryBacklogRepository implements NotificationDeliveryBacklogRepository {

    private static final String COUNT_DUE_SQL = """
            SELECT count(*)
            FROM notification_events
            WHERE delivery_status IN ('PENDING', 'RETRY_SCHEDULED')
              AND next_attempt_at IS NOT NULL
              AND next_attempt_at <= :observedAt
            """;

    private static final String OLDEST_DUE_AGE_SQL = """
            SELECT COALESCE(
                       GREATEST(
                           EXTRACT(EPOCH FROM (
                               CAST(:observedAt AS TIMESTAMPTZ) - MIN(next_attempt_at)
                           )),
                           0
                       ),
                       0
                   )::DOUBLE PRECISION
            FROM notification_events
            WHERE delivery_status IN ('PENDING', 'RETRY_SCHEDULED')
              AND next_attempt_at IS NOT NULL
              AND next_attempt_at <= :observedAt
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationDeliveryBacklogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long countDue(Instant observedAt) {
        Long count = jdbcTemplate.queryForObject(COUNT_DUE_SQL, parameters(observedAt), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Duration oldestDueAge(Instant observedAt) {
        Double ageSeconds = jdbcTemplate.queryForObject(OLDEST_DUE_AGE_SQL, parameters(observedAt), Double.class);
        if (ageSeconds == null) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.round(ageSeconds * 1_000));
    }

    private MapSqlParameterSource parameters(Instant observedAt) {
        return new MapSqlParameterSource().addValue("observedAt", Timestamp.from(observedAt));
    }
}
