package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.port.outbound.NotificationSubscriptionRepository;
import com.cobre.notifications.domain.model.NotificationSubscription;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresqlNotificationSubscriptionRepository implements NotificationSubscriptionRepository {

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

    private static final RowMapper<NotificationSubscription> ROW_MAPPER = (resultSet, rowNumber) ->
            NotificationSubscription.fromStoredValues(
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
}
