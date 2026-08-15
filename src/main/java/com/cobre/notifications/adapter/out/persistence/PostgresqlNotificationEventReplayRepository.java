package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.port.outbound.NotificationEventReplayRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class PostgresqlNotificationEventReplayRepository implements NotificationEventReplayRepository {

    private static final String LOCK_DELIVERY_STATUS_SQL = """
            SELECT delivery_status
            FROM notification_events
            WHERE event_id = :notificationEventId
              AND client_id = :clientId
            FOR UPDATE
            """;

    private static final String SCHEDULE_REPLAY_SQL = """
            UPDATE notification_events
            SET delivery_status = :nextStatus,
                subscription_id = NULL,
                destination_url_snapshot = NULL,
                signing_key_version = NULL,
                delivery_cycle = delivery_cycle + 1,
                next_attempt_at = :replayedAt,
                lease_owner = NULL,
                lease_until = NULL,
                delivered_at = NULL,
                version = version + 1,
                updated_at = :replayedAt
            WHERE event_id = :notificationEventId
              AND client_id = :clientId
              AND delivery_status = 'FAILED'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationEventReplayRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DeliveryStatus> lockDeliveryStatus(ReplayNotificationEventCommand command) {
        return jdbcTemplate.query(
                        LOCK_DELIVERY_STATUS_SQL,
                        parameters(command),
                        (resultSet, rowNumber) -> DeliveryStatus.valueOf(
                                resultSet.getString("delivery_status")))
                .stream()
                .findFirst();
    }

    @Override
    public boolean scheduleReplay(
            ReplayNotificationEventCommand command,
            DeliveryStatus nextStatus,
            Instant replayedAt) {
        MapSqlParameterSource parameters = parameters(command)
                .addValue("nextStatus", nextStatus.name())
                .addValue("replayedAt", Timestamp.from(replayedAt));

        return jdbcTemplate.update(SCHEDULE_REPLAY_SQL, parameters) == 1;
    }

    private MapSqlParameterSource parameters(ReplayNotificationEventCommand command) {
        return new MapSqlParameterSource()
                .addValue("notificationEventId", command.notificationEventId())
                .addValue("clientId", command.clientId());
    }
}
