package com.cobre.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "notifications.security.clients[0].client-id=CLIENT001",
        "notifications.security.clients[0].token=client-001-integration-token",
        "notifications.security.clients[1].client-id=CLIENT002",
        "notifications.security.clients[1].token=client-002-integration-token"
})
@AutoConfigureMockMvc
@Transactional
@Rollback
class NotificationEventListIntegrationTest extends PostgresqlIntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedNotificationEvents() {
        jdbcTemplate.update("DELETE FROM notification_events");
        insertEvent("LIST001", "CLIENT001", "credit_payment", "2026-08-14T10:00:00Z", "COMPLETED");
        insertEvent("LIST002", "CLIENT001", "debit_payment", "2026-08-15T10:00:00Z", "FAILED");
        insertEvent("LIST003", "CLIENT001", "credit_transfer", "2026-08-15T11:00:00Z", "FAILED");
        insertEvent("LIST004", "CLIENT002", "debit_transfer", "2026-08-15T12:00:00Z", "FAILED");
    }

    @Test
    void listsOnlyTheAuthenticatedClientsEvents() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[*].event_id", contains("LIST003", "LIST002", "LIST001")))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[0].client_id").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.has_next").value(false));

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-002-integration-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].event_id", contains("LIST004")));
    }

    @Test
    void filtersAndPaginatesWithAStableOrder() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token")
                        .param("created_from", "2026-08-15T09:00:00Z")
                        .param("created_to", "2026-08-15T12:00:00Z")
                        .param("delivery_status", "FAILED")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].event_id", contains("LIST003")))
                .andExpect(jsonPath("$.has_next").value(true));

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token")
                        .param("created_from", "2026-08-15T09:00:00Z")
                        .param("created_to", "2026-08-15T12:00:00Z")
                        .param("delivery_status", "FAILED")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].event_id", contains("LIST002")))
                .andExpect(jsonPath("$.has_next").value(false));
    }

    @Test
    void rejectsAnInvalidCreationDateRange() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token")
                        .param("created_from", "2026-08-15T12:00:00Z")
                        .param("created_to", "2026-08-15T11:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("created_from must be earlier than created_to"));
    }

    @Test
    void returnsAnEmptyPageWhenNoEventsMatch() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token")
                        .param("delivery_status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.has_next").value(false));
    }

    @Test
    void rejectsUnsupportedPaginationAndStatusValues() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token")
                        .param("delivery_status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private void insertEvent(
            String eventId,
            String clientId,
            String eventType,
            String createdAt,
            String deliveryStatus) {
        Instant timestamp = Instant.parse(createdAt);
        jdbcTemplate.update("""
                        INSERT INTO notification_events (
                            event_id,
                            client_id,
                            event_type,
                            content,
                            created_at,
                            delivery_date,
                            delivery_status,
                            attempt_history_complete,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?)
                        """,
                eventId,
                clientId,
                eventType,
                "Test event " + eventId,
                Timestamp.from(timestamp),
                Timestamp.from(timestamp.plusSeconds(30)),
                deliveryStatus,
                Timestamp.from(timestamp));
    }
}
