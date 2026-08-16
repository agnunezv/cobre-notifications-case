package com.cobre.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "notifications.delivery.worker.enabled=false",
                "notifications.security.clients[0].client-id=CLIENT001",
                "notifications.security.clients[0].token=client-001-test-token",
                "notifications.security.monitoring.token=monitoring-test-token"
        })
@AutoConfigureMockMvc
class NotificationDeliveryMonitoringIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant CREATED_AT = Instant.parse("2026-08-15T11:55:00Z");
    private static final Instant FIRST_ATTEMPT_AT = Instant.parse("2026-08-15T11:56:00Z");
    private static final Instant SECOND_ATTEMPT_AT = Instant.parse("2026-08-15T11:57:00Z");
    private static final Instant DELIVERED_AT = Instant.parse("2026-08-15T11:57:00.042Z");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void prepareInvestigationHistory() {
        jdbcTemplate.update("DELETE FROM notification_events");
        jdbcTemplate.update("""
                        INSERT INTO notification_events (
                            event_id,
                            client_id,
                            event_type,
                            content,
                            created_at,
                            delivery_status,
                            delivery_cycle,
                            delivered_at,
                            delivery_date,
                            attempt_history_complete,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'COMPLETED', 1, ?, ?, TRUE, ?)
                        """,
                "EVT-MONITOR-001",
                "CLIENT001",
                "credit_payment",
                "Sensitive payload omitted from the monitoring response",
                Timestamp.from(CREATED_AT),
                Timestamp.from(DELIVERED_AT),
                Timestamp.from(DELIVERED_AT),
                Timestamp.from(DELIVERED_AT));
        insertAttempt(
                1,
                "INITIAL",
                FIRST_ATTEMPT_AT,
                FIRST_ATTEMPT_AT.plusMillis(125),
                "RETRYABLE_FAILURE",
                null,
                "TIMEOUT",
                "The webhook request timed out",
                125L);
        insertAttempt(
                2,
                "AUTOMATIC_RETRY",
                SECOND_ATTEMPT_AT,
                DELIVERED_AT,
                "SUCCESS",
                202,
                null,
                null,
                42L);
    }

    @Test
    void returnsAClientScopedAttemptTimelineToTheMonitoringIdentity() throws Exception {
        mockMvc.perform(get("/internal/monitoring/notification_events/EVT-MONITOR-001")
                        .queryParam("client_id", "CLIENT001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer monitoring-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value("EVT-MONITOR-001"))
                .andExpect(jsonPath("$.client_id").value("CLIENT001"))
                .andExpect(jsonPath("$.delivery_status").value("COMPLETED"))
                .andExpect(jsonPath("$.webhook_delivered_at").value(DELIVERED_AT.toString()))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.attempts.length()").value(2))
                .andExpect(jsonPath("$.attempts[0].attempt_number").value(1))
                .andExpect(jsonPath("$.attempts[0].result").value("RETRYABLE_FAILURE"))
                .andExpect(jsonPath("$.attempts[0].failure_category").value("TIMEOUT"))
                .andExpect(jsonPath("$.attempts[1].attempt_number").value(2))
                .andExpect(jsonPath("$.attempts[1].result").value("SUCCESS"))
                .andExpect(jsonPath("$.attempts[1].http_status").value(202));
    }

    @Test
    void concealsAnotherClientsEventAndRejectsClientCredentials() throws Exception {
        mockMvc.perform(get("/internal/monitoring/notification_events/EVT-MONITOR-001")
                        .queryParam("client_id", "CLIENT002")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer monitoring-test-token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/internal/monitoring/notification_events/EVT-MONITOR-001")
                        .queryParam("client_id", "CLIENT001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-test-token"))
                .andExpect(status().isForbidden());
    }

    private void insertAttempt(
            int attemptNumber,
            String origin,
            Instant startedAt,
            Instant finishedAt,
            String result,
            Integer httpStatus,
            String failureCategory,
            String failureDescription,
            long latencyMs) {
        jdbcTemplate.update("""
                        INSERT INTO delivery_attempts (
                            attempt_id,
                            event_id,
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
                        ) VALUES (?, 'EVT-MONITOR-001', 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                attemptNumber,
                origin,
                Timestamp.from(startedAt),
                Timestamp.from(finishedAt),
                result,
                httpStatus,
                failureCategory,
                failureDescription,
                latencyMs,
                "corr-monitor-" + attemptNumber);
    }
}
