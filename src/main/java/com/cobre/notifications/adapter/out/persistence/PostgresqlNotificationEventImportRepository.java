package com.cobre.notifications.adapter.out.persistence;

import com.cobre.notifications.application.port.outbound.NotificationEventImportRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresqlNotificationEventImportRepository implements NotificationEventImportRepository {

    private static final int BATCH_SIZE = 500;
    private static final String INSERT_IF_ABSENT_SQL = """
            INSERT INTO notification_events (
                event_id,
                client_id,
                event_type,
                content,
                created_at,
                delivery_date,
                delivery_status,
                next_attempt_at,
                attempt_history_complete,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresqlNotificationEventImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int insertIfAbsent(List<NotificationEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }

        int[][] affectedRows =
                jdbcTemplate.batchUpdate(INSERT_IF_ABSENT_SQL, events, BATCH_SIZE, (statement, event) -> {
                    statement.setString(1, event.eventId());
                    statement.setString(2, event.clientId());
                    statement.setString(3, event.eventType());
                    statement.setString(4, event.content());
                    statement.setTimestamp(5, Timestamp.from(event.createdAt()));
                    if (event.deliveryDate() == null) {
                        statement.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE);
                    } else {
                        statement.setTimestamp(6, Timestamp.from(event.deliveryDate()));
                    }
                    statement.setString(7, event.deliveryStatus().name());
                    if (event.deliveryStatus() == DeliveryStatus.PENDING) {
                        statement.setTimestamp(8, Timestamp.from(event.createdAt()));
                    } else {
                        statement.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
                    }
                    statement.setTimestamp(9, Timestamp.from(event.createdAt()));
                });

        int inserted = 0;
        for (int[] batch : affectedRows) {
            for (int affected : batch) {
                if (affected > 0 || affected == Statement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }
        }
        return inserted;
    }
}
