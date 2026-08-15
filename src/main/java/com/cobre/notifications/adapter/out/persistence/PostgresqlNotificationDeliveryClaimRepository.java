package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryClaimRepository;
import com.cobre.notifications.domain.model.InvalidNotificationDestinationException;
import com.cobre.notifications.domain.model.NotificationDestination;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class PostgresqlNotificationDeliveryClaimRepository implements NotificationDeliveryClaimRepository {

    private static final String CLAIM_DUE_SQL = """
            WITH claimable AS (
                SELECT event_id
                FROM notification_events
                WHERE delivery_status IN ('PENDING', 'RETRY_SCHEDULED')
                  AND next_attempt_at IS NOT NULL
                  AND next_attempt_at <= :claimedAt
                ORDER BY next_attempt_at, event_id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE notification_events AS notification
            SET delivery_status = 'PROCESSING',
                next_attempt_at = NULL,
                lease_owner = :workerId,
                lease_until = :leaseUntil,
                version = notification.version + 1,
                updated_at = :claimedAt
            FROM claimable
            WHERE notification.event_id = claimable.event_id
            RETURNING notification.event_id,
                      notification.client_id,
                      notification.event_type,
                      notification.content,
                      notification.delivery_cycle,
                      notification.lease_owner,
                      notification.lease_until,
                      notification.subscription_id,
                      notification.destination_url_snapshot
            """;

    private static final RowMapper<ClaimedNotificationDelivery> ROW_MAPPER = (resultSet, rowNumber) ->
            new ClaimedNotificationDelivery(
                    resultSet.getString("event_id"),
                    resultSet.getString("client_id"),
                    resultSet.getString("event_type"),
                    resultSet.getString("content"),
                    resultSet.getInt("delivery_cycle"),
                    resultSet.getString("lease_owner"),
                    resultSet.getTimestamp("lease_until").toInstant(),
                    destination(
                            resultSet.getString("subscription_id"),
                            resultSet.getString("destination_url_snapshot")));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationDeliveryClaimRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ClaimedNotificationDelivery> claimDue(
            String workerId,
            Instant claimedAt,
            Instant leaseUntil,
            int batchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("workerId", workerId)
                .addValue("claimedAt", Timestamp.from(claimedAt))
                .addValue("leaseUntil", Timestamp.from(leaseUntil))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.query(CLAIM_DUE_SQL, parameters, ROW_MAPPER);
    }

    private static NotificationDestination destination(String subscriptionId, String endpointUrl) {
        if (subscriptionId == null && endpointUrl == null) {
            return null;
        }
        if (subscriptionId == null || endpointUrl == null) {
            throw new InvalidNotificationDestinationException(
                    "The stored notification destination is incomplete");
        }
        return NotificationDestination.fromStoredValues(subscriptionId, endpointUrl);
    }
}
