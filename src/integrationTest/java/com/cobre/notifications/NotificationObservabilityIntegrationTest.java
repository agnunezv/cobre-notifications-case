package com.cobre.notifications;

import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryBacklogRepository;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "notifications.delivery.worker.enabled=false",
                "notifications.security.clients[0].client-id=CLIENT001",
                "notifications.security.clients[0].token=metrics-test-token",
                "notifications.security.clients[1].client-id=CLIENT002",
                "notifications.security.clients[1].token=client-002-test-token",
                "notifications.security.clients[2].client-id=CLIENT003",
                "notifications.security.clients[2].token=client-003-test-token"
        })
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import(NotificationObservabilityIntegrationTest.FixedClockConfiguration.class)
class NotificationObservabilityIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired
    NotificationDeliveryBacklogRepository backlogRepository;

    @Autowired
    NotificationDeliveryMetrics metrics;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void clearNotificationEvents() {
        jdbcTemplate.update("DELETE FROM notification_events");
    }

    @Test
    void calculatesOnlyTheBacklogThatIsReadyForDelivery() {
        insertEvent("OLDEST_DUE", "PENDING", NOW.minusSeconds(30));
        insertEvent("RETRY_DUE", "RETRY_SCHEDULED", NOW.minusSeconds(10));
        insertEvent("FUTURE", "PENDING", NOW.plusSeconds(60));
        insertEvent("PROCESSING", "PROCESSING", null);

        assertThat(backlogRepository.countDue(NOW)).isEqualTo(2);
        assertThat(backlogRepository.oldestDueAge(NOW)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void exposesPrometheusMetricsOnlyToAuthenticatedRequests() throws Exception {
        insertEvent("DUE", "PENDING", NOW.minusSeconds(20));
        metrics.recordAttempt(new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                null,
                NotificationDeliveryFailureCategory.TIMEOUT,
                "The webhook request timed out",
                125));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer metrics-test-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "cobre_notifications_delivery_backlog_due_events")))
                .andExpect(content().string(containsString(
                        "cobre_notifications_delivery_backlog_oldest_age_seconds")))
                .andExpect(content().string(containsString(
                        "cobre_notifications_delivery_attempts_total")))
                .andExpect(content().string(containsString(
                        "cobre_notifications_delivery_attempt_duration_seconds_bucket")));
    }

    private void insertEvent(String eventId, String status, Instant nextAttemptAt) {
        jdbcTemplate.update("""
                        INSERT INTO notification_events (
                            event_id,
                            client_id,
                            event_type,
                            content,
                            created_at,
                            delivery_status,
                            next_attempt_at,
                            attempt_history_complete,
                            updated_at
                        ) VALUES (?, 'CLIENT001', 'credit_payment', ?, ?, ?, ?, TRUE, ?)
                        """,
                eventId,
                "Test event " + eventId,
                Timestamp.from(NOW.minusSeconds(60)),
                status,
                nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt),
                Timestamp.from(NOW.minusSeconds(60)));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
