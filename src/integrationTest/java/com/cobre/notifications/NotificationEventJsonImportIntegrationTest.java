package com.cobre.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.adapter.in.bootstrap.NotificationEventJsonImporter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notifications.json-import.enabled=true")
@Import(NotificationEventJsonImportIntegrationTest.FixedClockConfiguration.class)
class NotificationEventJsonImportIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NotificationEventJsonImporter jsonImporter;

    @BeforeEach
    void resetImportedEvents() throws Exception {
        jdbcTemplate.update("DELETE FROM notification_events");
        jsonImporter.run(new DefaultApplicationArguments(new String[0]));
    }

    @Test
    void importsTheJsonFileIdempotentlyAndPreservesItsDeliveryDate() throws Exception {
        Integer initialCount = eventCount();
        Integer distinctCreationDates = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT created_at) FROM notification_events", Integer.class);
        Instant createdAtBefore = createdAt("EVT001");
        Instant deliveryDate = deliveryDate("EVT001");

        jsonImporter.run(new DefaultApplicationArguments(new String[0]));

        assertThat(initialCount).isEqualTo(10);
        assertThat(distinctCreationDates).isEqualTo(1);
        assertThat(createdAtBefore).isEqualTo(ACCEPTED_AT);
        assertThat(deliveryDate).isEqualTo(Instant.parse("2024-03-15T09:30:22Z"));
        assertThat(eventCount()).isEqualTo(10);
        assertThat(createdAt("EVT001")).isEqualTo(createdAtBefore);
    }

    private Integer eventCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notification_events", Integer.class);
    }

    private Instant createdAt(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT created_at FROM notification_events WHERE event_id = ?", Instant.class, eventId);
    }

    private Instant deliveryDate(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT delivery_date FROM notification_events WHERE event_id = ?", Instant.class, eventId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC);
        }
    }
}
