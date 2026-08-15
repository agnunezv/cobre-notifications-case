package com.cobre.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgresqlFoundationIntegrationTest extends PostgresqlIntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheBaselineMigrationToPostgresql() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success",
                Integer.class);
        String eventsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.notification_events')::text",
                String.class);
        List<String> eventIndexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'notification_events'",
                String.class);

        assertThat(migrationCount).isEqualTo(3);
        assertThat(eventsTable).isEqualTo("notification_events");
        assertThat(eventIndexes).contains(
                "idx_notification_events_client_created",
                "idx_notification_events_client_status_created",
                "idx_notification_events_subscription",
                "idx_notification_events_claimable");
    }
}
