package com.cobre.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cobre.notifications.application.model.NotificationEventReplayNotAllowedException;
import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.port.inbound.ReplayNotificationEventUseCase;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "notifications.security.clients[0].client-id=CLIENT001",
            "notifications.security.clients[0].token=client-001-integration-token",
            "notifications.security.clients[1].client-id=CLIENT002",
            "notifications.security.clients[1].token=client-002-integration-token"
        })
@AutoConfigureMockMvc
@Import(NotificationEventReplayIntegrationTest.FixedClockConfiguration.class)
class NotificationEventReplayIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReplayNotificationEventUseCase replayUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedNotificationEvents() {
        clearData();
        insertSubscription("SUB_CLIENT001", "CLIENT001");
        insertSubscription("SUB_CLIENT002", "CLIENT002");
        insertEvent("REPLAY_FAILED", "CLIENT001", "SUB_CLIENT001", "FAILED");
        insertEvent("REPLAY_COMPLETED", "CLIENT001", "SUB_CLIENT001", "COMPLETED");
        insertEvent("REPLAY_OTHER", "CLIENT002", "SUB_CLIENT002", "FAILED");
    }

    @AfterEach
    void clearNotificationEvents() {
        clearData();
    }

    @Test
    void acceptsReplayAndSchedulesANewCycle() throws Exception {
        mockMvc.perform(post("/notification_events/REPLAY_FAILED/replay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        var event = jdbcTemplate.queryForMap("""
                SELECT delivery_status,
                       subscription_id,
                       destination_url_snapshot,
                       signing_key_version,
                       delivery_cycle,
                       next_attempt_at,
                       lease_owner,
                       lease_until,
                       delivered_at,
                       delivery_date,
                       version,
                       updated_at
                FROM notification_events
                WHERE event_id = 'REPLAY_FAILED'
                """);

        assertThat(event.get("delivery_status")).isEqualTo("PENDING");
        assertThat(event.get("delivery_cycle")).isEqualTo(2);
        assertThat(event.get("next_attempt_at")).isEqualTo(Timestamp.from(NOW));
        assertThat(event.get("updated_at")).isEqualTo(Timestamp.from(NOW));
        assertThat(event.get("delivery_date")).isEqualTo(Timestamp.from(NOW.minusSeconds(30)));
        assertThat(event.get("version")).isEqualTo(1L);
        assertThat(event)
                .containsEntry("subscription_id", null)
                .containsEntry("destination_url_snapshot", null)
                .containsEntry("signing_key_version", null)
                .containsEntry("lease_owner", null)
                .containsEntry("lease_until", null)
                .containsEntry("delivered_at", null);
    }

    @Test
    void rejectsReplayWhenTheEventHasNotFailed() throws Exception {
        mockMvc.perform(post("/notification_events/REPLAY_COMPLETED/replay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Notification event cannot be replayed"))
                .andExpect(jsonPath("$.detail").value("Only failed notification events can be replayed"));

        assertEventState("REPLAY_COMPLETED", "COMPLETED", 1);
    }

    @Test
    void doesNotRevealWhetherAnEventBelongsToAnotherClient() throws Exception {
        assertNotFound("REPLAY_OTHER");
        assertNotFound("DOES_NOT_EXIST");
    }

    @Test
    void requiresAuthenticationAndAValidEventIdentifier() throws Exception {
        mockMvc.perform(post("/notification_events/REPLAY_FAILED/replay")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/notification_events/{notification_event_id}/replay", "E".repeat(65))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("notificationEventId must not exceed 64 characters"));
    }

    @Test
    void acceptsOnlyOneOfTwoConcurrentReplayRequests() throws Exception {
        ReplayNotificationEventCommand command = new ReplayNotificationEventCommand("CLIENT001", "REPLAY_FAILED");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> firstReplay = executor.submit(() -> replay(start, command));
        Future<Boolean> secondReplay = executor.submit(() -> replay(start, command));

        try {
            start.countDown();
            assertThat(List.of(firstReplay.get(5, TimeUnit.SECONDS), secondReplay.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertEventState("REPLAY_FAILED", "PENDING", 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean replay(CountDownLatch start, ReplayNotificationEventCommand command) {
        await(start);
        try {
            replayUseCase.replay(command);
            return true;
        } catch (NotificationEventReplayNotAllowedException exception) {
            return false;
        }
    }

    private void assertNotFound(String eventId) throws Exception {
        mockMvc.perform(post("/notification_events/{notification_event_id}/replay", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-integration-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Notification event not found"))
                .andExpect(jsonPath("$.detail").value("Notification event was not found"));
    }

    private void assertEventState(String eventId, String status, int cycle) {
        var event = jdbcTemplate.queryForMap("""
                SELECT delivery_status, delivery_cycle
                FROM notification_events
                WHERE event_id = ?
                """, eventId);
        assertThat(event).containsEntry("delivery_status", status).containsEntry("delivery_cycle", cycle);
    }

    private void insertSubscription(String subscriptionId, String clientId) {
        jdbcTemplate.update(
                """
                        INSERT INTO subscriptions (
                            subscription_id,
                            client_id,
                            endpoint_url,
                            active,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, TRUE, ?, ?)
                        """,
                subscriptionId,
                clientId,
                "https://old.example.com/notifications",
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(60)));
    }

    private void insertEvent(String eventId, String clientId, String subscriptionId, String deliveryStatus) {
        jdbcTemplate.update(
                """
                        INSERT INTO notification_events (
                            event_id,
                            client_id,
                            event_type,
                            content,
                            created_at,
                            delivery_date,
                            delivery_status,
                            subscription_id,
                            destination_url_snapshot,
                            signing_key_version,
                            delivery_cycle,
                            delivered_at,
                            attempt_history_complete,
                            updated_at
                        ) VALUES (?, ?, 'credit_payment', ?, ?, ?, ?, ?, ?, 'key-v1', 1, NULL, TRUE, ?)
                        """,
                eventId,
                clientId,
                "Test event " + eventId,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(30)),
                deliveryStatus,
                subscriptionId,
                "https://old.example.com/notifications",
                Timestamp.from(NOW.minusSeconds(30)));
    }

    private void clearData() {
        jdbcTemplate.update("DELETE FROM delivery_attempts");
        jdbcTemplate.update("DELETE FROM notification_events");
        jdbcTemplate.update("DELETE FROM subscription_event_types");
        jdbcTemplate.update("DELETE FROM subscriptions");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating replay requests", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
