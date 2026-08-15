package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.ExpiredNotificationLease;
import com.cobre.notifications.application.model.NotificationLeaseRecovery;
import com.cobre.notifications.application.port.outbound.NotificationLeaseRecoveryRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class NotificationLeaseRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final RetryPolicy RETRY_POLICY = new RetryPolicy(
            4,
            List.of(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30)));

    @Test
    void recoversClaimsWithoutAttemptsAndAppliesTheRetryPolicyToAbandonedAttempts() {
        List<ExpiredNotificationLease> expiredLeases = List.of(
                expiredLease("CLAIM_ONLY", null),
                expiredLease("OPEN_RETRY", 2),
                expiredLease("OPEN_EXHAUSTED", 4));
        CapturingRepository repository = new CapturingRepository(expiredLeases);
        NotificationLeaseRecoveryService service = new NotificationLeaseRecoveryService(
                repository,
                new DeliveryLifecycle(RETRY_POLICY),
                RETRY_POLICY,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int recoveredCount = service.recoverExpired(10);

        assertThat(recoveredCount).isEqualTo(3);
        assertThat(repository.requestedBatchSize).isEqualTo(10);
        assertThat(repository.recoveries)
                .extracting(
                        recovery -> recovery.expiredLease().eventId(),
                        NotificationLeaseRecovery::nextStatus,
                        NotificationLeaseRecovery::nextAttemptAt)
                .containsExactly(
                        tuple(
                                "CLAIM_ONLY",
                                DeliveryStatus.RETRY_SCHEDULED,
                                NOW),
                        tuple(
                                "OPEN_RETRY",
                                DeliveryStatus.RETRY_SCHEDULED,
                                NOW.plusSeconds(5)),
                        tuple(
                                "OPEN_EXHAUSTED",
                                DeliveryStatus.FAILED,
                                null));
    }

    private ExpiredNotificationLease expiredLease(String eventId, Integer attemptNumber) {
        boolean hasAttempt = attemptNumber != null;
        return new ExpiredNotificationLease(
                eventId,
                1,
                "worker-dead",
                NOW.minusSeconds(1),
                hasAttempt ? UUID.randomUUID() : null,
                attemptNumber,
                hasAttempt ? NOW.minusSeconds(30) : null);
    }

    private static final class CapturingRepository implements NotificationLeaseRecoveryRepository {

        private final List<ExpiredNotificationLease> expiredLeases;
        private final List<NotificationLeaseRecovery> recoveries = new ArrayList<>();
        private int requestedBatchSize;

        private CapturingRepository(List<ExpiredNotificationLease> expiredLeases) {
            this.expiredLeases = expiredLeases;
        }

        @Override
        public List<ExpiredNotificationLease> lockExpired(Instant expiredAt, int batchSize) {
            assertThat(expiredAt).isEqualTo(NOW);
            requestedBatchSize = batchSize;
            return expiredLeases;
        }

        @Override
        public void recover(NotificationLeaseRecovery recovery) {
            recoveries.add(recovery);
        }
    }
}
