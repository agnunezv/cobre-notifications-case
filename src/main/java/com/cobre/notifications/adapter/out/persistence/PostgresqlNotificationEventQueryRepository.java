package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.model.NotificationEventSummary;
import com.cobre.notifications.application.port.outbound.NotificationEventQueryRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class PostgresqlNotificationEventQueryRepository implements NotificationEventQueryRepository {

    private static final String BASE_QUERY = """
            SELECT event_id, event_type, created_at, delivery_date, delivery_status
            FROM notification_events
            WHERE client_id = :clientId
            """;

    private static final RowMapper<NotificationEventSummary> ROW_MAPPER = (resultSet, rowNumber) -> {
        Timestamp deliveryDate = resultSet.getTimestamp("delivery_date");
        return new NotificationEventSummary(
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getTimestamp("created_at").toInstant(),
                deliveryDate == null ? null : deliveryDate.toInstant(),
                DeliveryStatus.valueOf(resultSet.getString("delivery_status")));
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresqlNotificationEventQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public NotificationEventPage findPage(NotificationEventQuery query) {
        StringBuilder sql = new StringBuilder(BASE_QUERY);
        MapSqlParameterSource parameters = new MapSqlParameterSource("clientId", query.clientId());

        if (query.createdFrom() != null) {
            sql.append("AND created_at >= :createdFrom\n");
            parameters.addValue("createdFrom", Timestamp.from(query.createdFrom()));
        }
        if (query.createdTo() != null) {
            sql.append("AND created_at < :createdTo\n");
            parameters.addValue("createdTo", Timestamp.from(query.createdTo()));
        }
        if (query.deliveryStatus() != null) {
            sql.append("AND delivery_status = :deliveryStatus\n");
            parameters.addValue("deliveryStatus", query.deliveryStatus().name());
        }

        sql.append("ORDER BY created_at DESC, event_id DESC\n");
        sql.append("LIMIT :limit OFFSET :offset");
        parameters.addValue("limit", query.size() + 1);
        parameters.addValue("offset", (long) query.page() * query.size());

        List<NotificationEventSummary> rows = jdbcTemplate.query(sql.toString(), parameters, ROW_MAPPER);
        boolean hasNext = rows.size() > query.size();
        List<NotificationEventSummary> items = hasNext ? rows.subList(0, query.size()) : rows;

        return new NotificationEventPage(items, query.page(), query.size(), hasNext);
    }
}
