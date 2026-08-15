package com.cobre.notifications;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.inbound.ImportNotificationEventsUseCase;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(NotificationDeliveryClaimIntegrationTest.FixedClockConfiguration.class)
class NotificationDeliveryClaimIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    @Autowired
    ClaimNotificationDeliveriesUseCase claimUseCase;

    @Autowired
    ImportNotificationEventsUseCase importUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearNotificationEvents() {
        jdbcTemplate.update("DELETE FROM notification_events");
    }

    @Test
    void claimsOnlyDeliveriesThatAreReadyAtTheCurrentTime() {
        importUseCase.importIfAbsent(List.of(new NotificationEvent(
                "PENDING_DUE",
                "CLIENT001",
                "credit_payment",
                "Test event PENDING_DUE",
                NOW.minusSeconds(60),
                null,
                DeliveryStatus.PENDING)));
        insertEvent("RETRY_DUE", "RETRY_SCHEDULED", NOW.minusSeconds(30));
        insertEvent("RETRY_FUTURE", "RETRY_SCHEDULED", NOW.plusSeconds(60));
        insertEvent("PROCESSING_DUE", "PROCESSING", NOW.minusSeconds(90));
        insertEvent("FAILED_DUE", "FAILED", NOW.minusSeconds(120));

        List<ClaimedNotificationDelivery> claimed = claimUseCase.claimDue(command("worker-a", 10));

        assertThat(claimed)
                .extracting(ClaimedNotificationDelivery::eventId)
                .containsExactlyInAnyOrder("PENDING_DUE", "RETRY_DUE");
        assertThat(claimed)
                .allSatisfy(delivery -> {
                    assertThat(delivery.clientId()).isEqualTo("CLIENT001");
                    assertThat(delivery.deliveryCycle()).isEqualTo(1);
                    assertThat(delivery.leaseUntil()).isEqualTo(NOW.plus(LEASE_DURATION));
                });

        assertClaimedBy("PENDING_DUE", "worker-a");
        assertClaimedBy("RETRY_DUE", "worker-a");
        assertStatus("RETRY_FUTURE", "RETRY_SCHEDULED");
        assertStatus("PROCESSING_DUE", "PROCESSING");
        assertStatus("FAILED_DUE", "FAILED");
    }

    @Test
    void respectsTheBatchSizeAndClaimsTheOldestDueDeliveriesFirst() {
        insertEvent("OLDEST", "PENDING", NOW.minusSeconds(30));
        insertEvent("MIDDLE", "PENDING", NOW.minusSeconds(20));
        insertEvent("NEWEST", "PENDING", NOW.minusSeconds(10));

        List<ClaimedNotificationDelivery> claimed = claimUseCase.claimDue(command("worker-a", 2));

        assertThat(claimed)
                .extracting(ClaimedNotificationDelivery::eventId)
                .containsExactlyInAnyOrder("OLDEST", "MIDDLE");
        assertStatus("NEWEST", "PENDING");
    }

    @Test
    void skipsRowsLockedByAnotherWorkerInsteadOfWaiting() throws Exception {
        insertEvent("LOCKED", "PENDING", NOW.minusSeconds(30));
        insertEvent("AVAILABLE", "PENDING", NOW.minusSeconds(20));

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> lockOwner = executor.submit(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT event_id FROM notification_events WHERE event_id = ? FOR UPDATE",
                    String.class,
                    "LOCKED");
            rowLocked.countDown();
            await(releaseLock);
        }));

        try {
            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<ClaimedNotificationDelivery>> otherWorker = executor.submit(
                    () -> claimUseCase.claimDue(command("worker-b", 1)));

            assertThat(otherWorker.get(5, TimeUnit.SECONDS))
                    .extracting(ClaimedNotificationDelivery::eventId)
                    .containsExactly("AVAILABLE");
        } finally {
            releaseLock.countDown();
            lockOwner.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void doesNotClaimTheSameDeliveryAcrossConcurrentWorkers() throws Exception {
        for (int index = 0; index < 20; index++) {
            insertEvent("CONCURRENT_" + index, "PENDING", NOW.minusSeconds(20L - index));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<List<ClaimedNotificationDelivery>> firstWorker = executor.submit(() -> {
            await(start);
            return claimUseCase.claimDue(command("worker-a", 10));
        });
        Future<List<ClaimedNotificationDelivery>> secondWorker = executor.submit(() -> {
            await(start);
            return claimUseCase.claimDue(command("worker-b", 10));
        });

        try {
            start.countDown();
            Set<String> firstIds = eventIds(firstWorker.get(10, TimeUnit.SECONDS));
            Set<String> secondIds = eventIds(secondWorker.get(10, TimeUnit.SECONDS));
            Set<String> allIds = new HashSet<>(firstIds);
            allIds.addAll(secondIds);

            assertThat(firstIds).hasSize(10).doesNotContainAnyElementsOf(secondIds);
            assertThat(secondIds).hasSize(10);
            assertThat(allIds).hasSize(20);
            assertThat(claimCount("worker-a")).isEqualTo(10);
            assertThat(claimCount("worker-b")).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAnInvalidClaimCommandAtTheApplicationBoundary() {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                " ",
                0,
                Duration.ZERO);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> claimUseCase.claimDue(command))
                .withMessageContaining("workerId is required")
                .withMessageContaining("batchSize must be between 1 and 100")
                .withMessageContaining("leaseDuration must be positive");
    }

    @Test
    void requiresAClaimCommandAtTheApplicationBoundary() {
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> claimUseCase.claimDue(null))
                .withMessageContaining("must not be null");
    }

    private ClaimNotificationDeliveriesCommand command(String workerId, int batchSize) {
        return new ClaimNotificationDeliveriesCommand(workerId, batchSize, LEASE_DURATION);
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
                        ) VALUES (?, 'CLIENT001', 'credit_payment', ?, ?, ?, ?, FALSE, ?)
                        """,
                eventId,
                "Test event " + eventId,
                Timestamp.from(NOW.minusSeconds(300)),
                status,
                Timestamp.from(nextAttemptAt),
                Timestamp.from(NOW.minusSeconds(300)));
    }

    private void assertClaimedBy(String eventId, String workerId) {
        PersistedDelivery delivery = persistedDelivery(eventId);

        assertThat(delivery.status()).isEqualTo("PROCESSING");
        assertThat(delivery.leaseOwner()).isEqualTo(workerId);
        assertThat(delivery.leaseUntil()).isEqualTo(NOW.plus(LEASE_DURATION));
        assertThat(delivery.nextAttemptAt()).isNull();
        assertThat(delivery.version()).isEqualTo(1L);
        assertThat(delivery.updatedAt()).isEqualTo(NOW);
    }

    private void assertStatus(String eventId, String expectedStatus) {
        assertThat(persistedDelivery(eventId).status()).isEqualTo(expectedStatus);
    }

    private PersistedDelivery persistedDelivery(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT delivery_status,
                               lease_owner,
                               lease_until,
                               next_attempt_at,
                               version,
                               updated_at
                        FROM notification_events
                        WHERE event_id = ?
                        """,
                (resultSet, rowNumber) -> new PersistedDelivery(
                        resultSet.getString("delivery_status"),
                        resultSet.getString("lease_owner"),
                        nullableInstant(resultSet.getTimestamp("lease_until")),
                        nullableInstant(resultSet.getTimestamp("next_attempt_at")),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("updated_at").toInstant()),
                eventId);
    }

    private Long claimCount(String workerId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_events WHERE lease_owner = ?",
                Long.class,
                workerId);
    }

    private Set<String> eventIds(List<ClaimedNotificationDelivery> deliveries) {
        Set<String> eventIds = new HashSet<>();
        deliveries.forEach(delivery -> eventIds.add(delivery.eventId()));
        return eventIds;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent test", exception);
        }
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record PersistedDelivery(
            String status,
            String leaseOwner,
            Instant leaseUntil,
            Instant nextAttemptAt,
            long version,
            Instant updatedAt) {
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
