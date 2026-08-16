package com.cobre.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.inbound.PrepareNotificationDeliveryUseCase;
import com.cobre.notifications.application.port.inbound.RecoverExpiredNotificationLeasesUseCase;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notifications.delivery.worker.enabled=false")
@Import(NotificationLeaseRecoveryIntegrationTest.FixedClockConfiguration.class)
class NotificationLeaseRecoveryIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final String SUBSCRIPTION_ID = "SUB_RECOVERY";
    private static final String DESTINATION_URL = "https://hooks.example.com/recovery";

    @Autowired
    RecoverExpiredNotificationLeasesUseCase recoveryUseCase;

    @Autowired
    ClaimNotificationDeliveriesUseCase claimUseCase;

    @Autowired
    PrepareNotificationDeliveryUseCase prepareUseCase;

    @Autowired
    CompleteNotificationDeliveryAttemptUseCase completeAttemptUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM notification_events");
        jdbcTemplate.update("DELETE FROM subscriptions");
        insertSubscription();
    }

    @Test
    void immediatelyRecoversAClaimThatNeverOpenedAnHttpAttempt() {
        insertProcessingEvent("CLAIM_ONLY", NOW.minusSeconds(1));

        assertThat(recoveryUseCase.recoverExpired(10)).isEqualTo(1);
        assertThat(recoveryUseCase.recoverExpired(10)).isZero();

        PersistedDelivery recovered = persistedDelivery("CLAIM_ONLY");
        assertThat(recovered.status()).isEqualTo("RETRY_SCHEDULED");
        assertThat(recovered.nextAttemptAt()).isEqualTo(NOW);
        assertThat(recovered.leaseOwner()).isNull();
        assertThat(recovered.leaseUntil()).isNull();
        assertThat(recovered.leaseRecoveryPending()).isTrue();

        ClaimedNotificationDelivery claimed = claimUseCase
                .claimDue(new ClaimNotificationDeliveriesCommand("worker-recovery", 1, LEASE_DURATION))
                .getFirst();
        assertThat(claimed.eventId()).isEqualTo("CLAIM_ONLY");
        assertThat(claimed.leaseRecovery()).isTrue();

        PreparedNotificationDelivery prepared = prepareUseCase.prepare(claimed).orElseThrow();
        assertThat(attempt(prepared.attemptId()).origin()).isEqualTo("LEASE_RECOVERY");
        assertThat(persistedDelivery("CLAIM_ONLY").leaseRecoveryPending()).isFalse();
    }

    @Test
    void closesAnAbandonedAttemptAndRejectsItsLateCompletion() {
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = NOW.minusSeconds(10);
        insertProcessingEvent("OPEN_ATTEMPT", NOW.minusSeconds(1));
        insertOpenAttempt("OPEN_ATTEMPT", attemptId, 2, startedAt, "AUTOMATIC_RETRY");

        assertThat(recoveryUseCase.recoverExpired(10)).isEqualTo(1);

        PersistedAttempt recoveredAttempt = attempt(attemptId);
        assertThat(recoveredAttempt.finishedAt()).isEqualTo(NOW);
        assertThat(recoveredAttempt.result()).isEqualTo("RETRYABLE_FAILURE");
        assertThat(recoveredAttempt.failureCategory()).isEqualTo("WORKER_LEASE_EXPIRED");
        assertThat(recoveredAttempt.latencyMs()).isEqualTo(10_000L);

        PersistedDelivery recovered = persistedDelivery("OPEN_ATTEMPT");
        assertThat(recovered.status()).isEqualTo("RETRY_SCHEDULED");
        assertThat(recovered.nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(recovered.leaseRecoveryPending()).isTrue();

        PreparedNotificationDelivery abandonedDelivery = preparedDelivery("OPEN_ATTEMPT", attemptId, 2, startedAt);
        WebhookDeliveryOutcome lateSuccess =
                new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 200, null, null, 10_000);

        assertThat(completeAttemptUseCase.complete(abandonedDelivery, lateSuccess))
                .isFalse();
        assertThat(persistedDelivery("OPEN_ATTEMPT").status()).isEqualTo("RETRY_SCHEDULED");
    }

    @Test
    void rejectsACompletionAfterLeaseExpiryEvenBeforeRecoveryRuns() {
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = NOW.minusSeconds(10);
        insertProcessingEvent("LATE_COMPLETION", NOW.minusSeconds(1));
        insertOpenAttempt("LATE_COMPLETION", attemptId, 1, startedAt, "INITIAL");

        WebhookDeliveryOutcome lateSuccess =
                new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 200, null, null, 10_000);

        assertThat(completeAttemptUseCase.complete(
                        preparedDelivery("LATE_COMPLETION", attemptId, 1, startedAt), lateSuccess))
                .isFalse();
        assertThat(persistedDelivery("LATE_COMPLETION").status()).isEqualTo("PROCESSING");
        assertThat(attempt(attemptId).finishedAt()).isNull();

        assertThat(recoveryUseCase.recoverExpired(10)).isEqualTo(1);
        assertThat(persistedDelivery("LATE_COMPLETION").status()).isEqualTo("RETRY_SCHEDULED");
    }

    @Test
    void failsTheDeliveryWhenTheAbandonedAttemptExhaustedTheRetryPolicy() {
        UUID attemptId = UUID.randomUUID();
        insertProcessingEvent("EXHAUSTED", NOW.minusSeconds(1));
        insertOpenAttempt("EXHAUSTED", attemptId, 4, NOW.minusSeconds(10), "AUTOMATIC_RETRY");

        assertThat(recoveryUseCase.recoverExpired(10)).isEqualTo(1);

        PersistedDelivery recovered = persistedDelivery("EXHAUSTED");
        assertThat(recovered.status()).isEqualTo("FAILED");
        assertThat(recovered.nextAttemptAt()).isNull();
        assertThat(recovered.leaseRecoveryPending()).isFalse();
        assertThat(attempt(attemptId).result()).isEqualTo("RETRYABLE_FAILURE");
    }

    @Test
    void ignoresActiveLeasesAndValidatesTheRecoveryBatchSize() {
        insertProcessingEvent("ACTIVE", NOW.plusSeconds(1));

        assertThat(recoveryUseCase.recoverExpired(10)).isZero();
        assertThat(persistedDelivery("ACTIVE").status()).isEqualTo("PROCESSING");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> recoveryUseCase.recoverExpired(0))
                .withMessageContaining("batchSize must be between 1 and 100");
    }

    @Test
    void partitionsExpiredLeasesAcrossConcurrentRecoveryTransactions() throws Exception {
        for (int index = 0; index < 20; index++) {
            insertProcessingEvent("EXPIRED_" + index, NOW.minusSeconds(index + 1L));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> firstWorker = executor.submit(() -> {
            await(start);
            return recoveryUseCase.recoverExpired(10);
        });
        Future<Integer> secondWorker = executor.submit(() -> {
            await(start);
            return recoveryUseCase.recoverExpired(10);
        });

        try {
            start.countDown();
            assertThat(List.of(firstWorker.get(10, TimeUnit.SECONDS), secondWorker.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(10, 10);
            assertThat(recoveredEventCount()).isEqualTo(20);
            assertThat(totalEventVersions()).isEqualTo(20);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertSubscription() {
        jdbcTemplate.update(
                """
                        INSERT INTO subscriptions (
                            subscription_id,
                            client_id,
                            endpoint_url,
                            active,
                            created_at,
                            updated_at
                        ) VALUES (?, 'CLIENT001', ?, TRUE, ?, ?)
                        """,
                SUBSCRIPTION_ID,
                DESTINATION_URL,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(60)));
    }

    private void insertProcessingEvent(String eventId, Instant leaseUntil) {
        jdbcTemplate.update(
                """
                        INSERT INTO notification_events (
                            event_id,
                            client_id,
                            event_type,
                            content,
                            created_at,
                            delivery_status,
                            subscription_id,
                            destination_url_snapshot,
                            delivery_cycle,
                            next_attempt_at,
                            lease_owner,
                            lease_until,
                            attempt_history_complete,
                            version,
                            updated_at
                        ) VALUES (
                            ?, 'CLIENT001', 'credit_payment', ?, ?, 'PROCESSING', ?, ?,
                            1, NULL, 'worker-dead', ?, TRUE, 0, ?
                        )
                        """,
                eventId,
                "Test event " + eventId,
                Timestamp.from(NOW.minusSeconds(60)),
                SUBSCRIPTION_ID,
                DESTINATION_URL,
                Timestamp.from(leaseUntil),
                Timestamp.from(NOW.minusSeconds(60)));
    }

    private void insertOpenAttempt(
            String eventId, UUID attemptId, int attemptNumber, Instant startedAt, String origin) {
        jdbcTemplate.update(
                """
                        INSERT INTO delivery_attempts (
                            attempt_id,
                            event_id,
                            delivery_cycle,
                            attempt_number,
                            origin,
                            started_at,
                            correlation_id
                        ) VALUES (?, ?, 1, ?, ?, ?, ?)
                        """, attemptId, eventId, attemptNumber, origin, Timestamp.from(startedAt), attemptId.toString());
    }

    private PreparedNotificationDelivery preparedDelivery(
            String eventId, UUID attemptId, int attemptNumber, Instant startedAt) {
        return new PreparedNotificationDelivery(
                attemptId,
                eventId,
                "CLIENT001",
                "credit_payment",
                "Test event " + eventId,
                new com.cobre.notifications.domain.model.NotificationDestination(
                        SUBSCRIPTION_ID, URI.create(DESTINATION_URL)),
                1,
                attemptNumber,
                attemptId.toString(),
                startedAt);
    }

    private PersistedDelivery persistedDelivery(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT delivery_status,
                       next_attempt_at,
                       lease_owner,
                       lease_until,
                       lease_recovery_pending,
                       version
                FROM notification_events
                WHERE event_id = ?
                """,
                (resultSet, rowNumber) -> new PersistedDelivery(
                        resultSet.getString("delivery_status"),
                        nullableInstant(resultSet.getTimestamp("next_attempt_at")),
                        resultSet.getString("lease_owner"),
                        nullableInstant(resultSet.getTimestamp("lease_until")),
                        resultSet.getBoolean("lease_recovery_pending"),
                        resultSet.getLong("version")),
                eventId);
    }

    private PersistedAttempt attempt(UUID attemptId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT origin,
                       finished_at,
                       result,
                       failure_category,
                       latency_ms
                FROM delivery_attempts
                WHERE attempt_id = ?
                """,
                (resultSet, rowNumber) -> new PersistedAttempt(
                        resultSet.getString("origin"),
                        nullableInstant(resultSet.getTimestamp("finished_at")),
                        resultSet.getString("result"),
                        resultSet.getString("failure_category"),
                        resultSet.getObject("latency_ms", Long.class)),
                attemptId);
    }

    private long recoveredEventCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM notification_events
                WHERE delivery_status = 'RETRY_SCHEDULED'
                  AND lease_recovery_pending = TRUE
                """, Long.class);
        return count == null ? 0L : count;
    }

    private long totalEventVersions() {
        Long versions = jdbcTemplate.queryForObject("SELECT sum(version) FROM notification_events", Long.class);
        return versions == null ? 0L : versions;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent recovery");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent recovery", exception);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private record PersistedDelivery(
            String status,
            Instant nextAttemptAt,
            String leaseOwner,
            Instant leaseUntil,
            boolean leaseRecoveryPending,
            long version) {}

    private record PersistedAttempt(
            String origin, Instant finishedAt, String result, String failureCategory, Long latencyMs) {}
}
