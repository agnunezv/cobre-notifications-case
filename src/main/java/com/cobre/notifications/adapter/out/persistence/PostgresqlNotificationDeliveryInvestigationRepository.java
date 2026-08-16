package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.NotificationDeliveryAttemptDetails;
import com.cobre.notifications.application.model.NotificationDeliveryInvestigation;
import com.cobre.notifications.application.model.NotificationDeliveryInvestigationQuery;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryInvestigationRepository;
import com.cobre.notifications.domain.model.DeliveryAttemptOrigin;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresqlNotificationDeliveryInvestigationRepository
        implements NotificationDeliveryInvestigationRepository {

    private static final String FIND_EVENT_SQL = """
            SELECT event_id,
                   client_id,
                   event_type,
                   created_at,
                   delivery_status,
                   delivery_cycle,
                   next_attempt_at,
                   delivery_date,
                   delivered_at,
                   subscription_id,
                   attempt_history_complete,
                   updated_at
            FROM notification_events
            WHERE event_id = :eventId
              AND client_id = :clientId
            """;

    private static final String FIND_ATTEMPTS_SQL = """
            SELECT attempt_id,
                   delivery_cycle,
                   attempt_number,
                   origin,
                   started_at,
                   finished_at,
                   result,
                   http_status,
                   failure_category,
                   failure_description,
                   latency_ms,
                   correlation_id
            FROM delivery_attempts
            WHERE event_id = :eventId
            ORDER BY delivery_cycle, attempt_number
            """;

    private static final RowMapper<NotificationDeliveryAttemptDetails> ATTEMPT_ROW_MAPPER =
            (resultSet, rowNumber) -> new NotificationDeliveryAttemptDetails(
                    resultSet.getObject("attempt_id", UUID.class),
                    resultSet.getInt("delivery_cycle"),
                    resultSet.getInt("attempt_number"),
                    DeliveryAttemptOrigin.valueOf(resultSet.getString("origin")),
                    resultSet.getTimestamp("started_at").toInstant(),
                    nullableInstant(resultSet, "finished_at"),
                    nullableEnum(resultSet.getString("result"), DeliveryAttemptResult.class),
                    resultSet.getObject("http_status", Integer.class),
                    resultSet.getString("failure_category"),
                    resultSet.getString("failure_description"),
                    resultSet.getObject("latency_ms", Long.class),
                    resultSet.getString("correlation_id"));

    private static final RowMapper<EventSnapshot> EVENT_ROW_MAPPER = (resultSet, rowNumber) ->
            new EventSnapshot(
                    resultSet.getString("event_id"),
                    resultSet.getString("client_id"),
                    resultSet.getString("event_type"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    DeliveryStatus.valueOf(resultSet.getString("delivery_status")),
                    resultSet.getInt("delivery_cycle"),
                    nullableInstant(resultSet, "next_attempt_at"),
                    nullableInstant(resultSet, "delivery_date"),
                    nullableInstant(resultSet, "delivered_at"),
                    resultSet.getString("subscription_id"),
                    resultSet.getBoolean("attempt_history_complete"),
                    resultSet.getTimestamp("updated_at").toInstant());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationDeliveryInvestigationRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<NotificationDeliveryInvestigation> find(
            NotificationDeliveryInvestigationQuery query) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", query.eventId())
                .addValue("clientId", query.clientId());

        return jdbcTemplate.query(FIND_EVENT_SQL, parameters, EVENT_ROW_MAPPER)
                .stream()
                .findFirst()
                .map(event -> event.toInvestigation(findAttempts(parameters)));
    }

    private List<NotificationDeliveryAttemptDetails> findAttempts(
            MapSqlParameterSource parameters) {
        return jdbcTemplate.query(FIND_ATTEMPTS_SQL, parameters, ATTEMPT_ROW_MAPPER);
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static <T extends Enum<T>> T nullableEnum(String value, Class<T> enumType) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    private record EventSnapshot(
            String eventId,
            String clientId,
            String eventType,
            Instant createdAt,
            DeliveryStatus deliveryStatus,
            int deliveryCycle,
            Instant nextAttemptAt,
            Instant deliveryDate,
            Instant webhookDeliveredAt,
            String subscriptionId,
            boolean attemptHistoryComplete,
            Instant updatedAt) {

        private NotificationDeliveryInvestigation toInvestigation(
                List<NotificationDeliveryAttemptDetails> attempts) {
            return new NotificationDeliveryInvestigation(
                    eventId,
                    clientId,
                    eventType,
                    createdAt,
                    deliveryStatus,
                    deliveryCycle,
                    nextAttemptAt,
                    deliveryDate,
                    webhookDeliveredAt,
                    subscriptionId,
                    attemptHistoryComplete,
                    updatedAt,
                    attempts);
        }
    }
}
