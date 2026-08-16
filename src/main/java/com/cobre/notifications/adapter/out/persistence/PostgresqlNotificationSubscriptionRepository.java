package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.port.outbound.NotificationSubscriptionConfigurationRepository;
import com.cobre.notifications.application.port.outbound.NotificationSubscriptionRepository;
import com.cobre.notifications.domain.model.NotificationSubscription;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresqlNotificationSubscriptionRepository
        implements NotificationSubscriptionRepository, NotificationSubscriptionConfigurationRepository {

    private static final String FIND_ACTIVE_MATCHES = """
            SELECT subscriptions.subscription_id,
                   subscriptions.client_id,
                   subscriptions.endpoint_url
            FROM subscriptions
            INNER JOIN subscription_event_types
                    ON subscription_event_types.subscription_id = subscriptions.subscription_id
            WHERE subscriptions.client_id = :clientId
              AND subscriptions.active = TRUE
              AND subscription_event_types.event_type = :eventType
            ORDER BY subscriptions.subscription_id
            LIMIT 2
            """;

    private static final String UPSERT_SUBSCRIPTION = """
            INSERT INTO subscriptions (
                subscription_id,
                client_id,
                endpoint_url,
                active,
                created_at,
                updated_at
            ) VALUES (
                :subscriptionId,
                :clientId,
                :endpointUrl,
                TRUE,
                :configuredAt,
                :configuredAt
            )
            ON CONFLICT (subscription_id) DO UPDATE
            SET endpoint_url = EXCLUDED.endpoint_url,
                active = TRUE,
                updated_at = EXCLUDED.updated_at
            WHERE subscriptions.client_id = EXCLUDED.client_id
            """;

    private static final String DELETE_EVENT_TYPES = """
            DELETE FROM subscription_event_types
            WHERE subscription_id = :subscriptionId
            """;

    private static final String INSERT_EVENT_TYPE = """
            INSERT INTO subscription_event_types (subscription_id, event_type)
            VALUES (:subscriptionId, :eventType)
            """;

    private static final RowMapper<NotificationSubscription> ROW_MAPPER =
            (resultSet, rowNumber) -> NotificationSubscription.fromStoredValues(
                    resultSet.getString("subscription_id"),
                    resultSet.getString("client_id"),
                    resultSet.getString("endpoint_url"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationSubscriptionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<NotificationSubscription> findActiveMatches(NotificationSubscriptionQuery query) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("clientId", query.clientId())
                .addValue("eventType", query.eventType());

        return jdbcTemplate.query(FIND_ACTIVE_MATCHES, parameters, ROW_MAPPER);
    }

    @Override
    public void save(ConfigureNotificationSubscriptionCommand command) {
        var subscription = command.subscription();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("subscriptionId", subscription.subscriptionId())
                .addValue("clientId", subscription.clientId())
                .addValue("endpointUrl", subscription.endpointUrl().toString())
                .addValue("configuredAt", Timestamp.from(command.configuredAt()));

        if (jdbcTemplate.update(UPSERT_SUBSCRIPTION, parameters) != 1) {
            throw new IllegalStateException(
                    "Subscription %s is already assigned to another client".formatted(subscription.subscriptionId()));
        }

        jdbcTemplate.update(DELETE_EVENT_TYPES, parameters);
        SqlParameterSource[] eventTypeParameters = command.eventTypes().stream()
                .map(eventType -> new MapSqlParameterSource()
                        .addValue("subscriptionId", subscription.subscriptionId())
                        .addValue("eventType", eventType))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_EVENT_TYPE, eventTypeParameters);
    }
}
