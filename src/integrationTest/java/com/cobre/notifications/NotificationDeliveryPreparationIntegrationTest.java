package com.cobre.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionUseCase;
import com.cobre.notifications.application.port.inbound.PrepareNotificationDeliveryUseCase;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import com.cobre.notifications.application.port.inbound.ReplayNotificationEventUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryGateway;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.NotificationSubscription;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(NotificationDeliveryPreparationIntegrationTest.FixedClockConfiguration.class)
class NotificationDeliveryPreparationIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final Instant SOURCE_DELIVERY_DATE = Instant.parse("2024-03-15T09:30:22Z");
    private static final String CLIENT_ID = "CLIENT001";
    private static final String EVENT_TYPE = "credit_payment";
    private static final String WORKER_ID = "worker-1";

    @Autowired
    ClaimNotificationDeliveriesUseCase claimUseCase;

    @Autowired
    PrepareNotificationDeliveryUseCase prepareUseCase;

    @Autowired
    CompleteNotificationDeliveryAttemptUseCase completeAttemptUseCase;

    @Autowired
    ConfigureNotificationSubscriptionUseCase configureSubscriptionUseCase;

    @Autowired
    ReplayNotificationEventUseCase replayUseCase;

    @Autowired
    ProcessNotificationDeliveryBatchUseCase processBatchUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDeliveryData() {
        jdbcTemplate.update("DELETE FROM notification_events");
        jdbcTemplate.update("DELETE FROM subscriptions");
    }

    @Test
    void bindsTheResolvedDestinationAndCreatesAnOpenInitialAttempt() {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("PREPARE_SUCCESS", "PENDING", NOW.minusSeconds(1), null, null);

        ClaimedNotificationDelivery claimed = claim("PREPARE_SUCCESS");
        PreparedNotificationDelivery prepared = prepareUseCase.prepare(claimed).orElseThrow();

        assertThat(prepared.eventId()).isEqualTo("PREPARE_SUCCESS");
        assertThat(prepared.destination().subscriptionId()).isEqualTo("SUB001");
        assertThat(prepared.destination().endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/notifications"));
        assertThat(prepared.deliveryCycle()).isEqualTo(1);
        assertThat(prepared.attemptNumber()).isEqualTo(1);
        assertThat(prepared.startedAt()).isEqualTo(NOW);
        assertThat(prepared.correlationId()).isEqualTo(prepared.attemptId().toString());

        PersistedEvent event = persistedEvent("PREPARE_SUCCESS");
        assertThat(event.status()).isEqualTo("PROCESSING");
        assertThat(event.subscriptionId()).isEqualTo("SUB001");
        assertThat(event.destinationUrl()).isEqualTo("https://hooks.example.com/notifications");
        assertThat(event.attemptHistoryComplete()).isFalse();
        assertThat(event.leaseOwner()).isEqualTo(WORKER_ID);

        PersistedAttempt attempt = onlyAttempt("PREPARE_SUCCESS");
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.origin()).isEqualTo("INITIAL");
        assertThat(attempt.result()).isNull();
        assertThat(attempt.finishedAt()).isNull();
        assertThat(attempt.failureCategory()).isNull();
    }

    @Test
    void failsPermanentlyWhenNoSubscriptionMatches() {
        insertEvent("NO_SUBSCRIPTION", "PENDING", NOW.minusSeconds(1), null, null);

        assertThat(prepareUseCase.prepare(claim("NO_SUBSCRIPTION"))).isEmpty();

        assertConfigurationFailure("NO_SUBSCRIPTION", "SUBSCRIPTION_NOT_FOUND");
    }

    @Test
    void replaysThePersistedEventAfterItsMissingSubscriptionIsConfigured() {
        String eventId = "CONFIGURE_THEN_REPLAY";
        URI sharedEndpoint = URI.create("https://hooks.example.com/shared");
        insertEvent(eventId, "PENDING", NOW.minusSeconds(1), null, null);

        assertThat(prepareUseCase.prepare(claim(eventId))).isEmpty();
        assertConfigurationFailure(eventId, "SUBSCRIPTION_NOT_FOUND");

        configureSubscriptionUseCase.configure(new ConfigureNotificationSubscriptionCommand(
                new NotificationSubscription("SUB_CONFIGURED", CLIENT_ID, sharedEndpoint), Set.of(EVENT_TYPE), NOW));
        replayUseCase.replay(new ReplayNotificationEventCommand(CLIENT_ID, eventId));

        PreparedNotificationDelivery replayed =
                prepareUseCase.prepare(claim(eventId)).orElseThrow();

        assertThat(replayed.deliveryCycle()).isEqualTo(2);
        assertThat(replayed.attemptNumber()).isEqualTo(1);
        assertThat(replayed.destination().endpointUrl()).isEqualTo(sharedEndpoint);
        assertThat(persistedEvent(eventId).destinationUrl()).isEqualTo(sharedEndpoint.toString());
        assertThat(onlyOpenAttempt(eventId).origin()).isEqualTo("MANUAL_REPLAY");
    }

    @Test
    void failsPermanentlyWhenSubscriptionConfigurationIsAmbiguous() {
        insertSubscription("SUB001", "https://hooks.example.com/first", true);
        insertSubscription("SUB002", "https://hooks.example.com/second", true);
        insertEvent("AMBIGUOUS", "PENDING", NOW.minusSeconds(1), null, null);

        assertThat(prepareUseCase.prepare(claim("AMBIGUOUS"))).isEmpty();

        assertConfigurationFailure("AMBIGUOUS", "AMBIGUOUS_SUBSCRIPTION");
    }

    @Test
    void failsPermanentlyWhenTheResolvedDestinationIsInvalid() {
        insertSubscription("SUB001", "http://hooks.example.com/insecure", true);
        insertEvent("INVALID_DESTINATION", "PENDING", NOW.minusSeconds(1), null, null);

        assertThat(prepareUseCase.prepare(claim("INVALID_DESTINATION"))).isEmpty();

        assertConfigurationFailure("INVALID_DESTINATION", "INVALID_DESTINATION");
    }

    @Test
    void usesTheUpdatedDestinationForNewDeliveriesWhileRetriesKeepTheirSnapshot() {
        insertSubscription("SUB001", "https://hooks.example.com/new-destination", true);
        insertEvent(
                "RETRY_SNAPSHOT",
                "RETRY_SCHEDULED",
                NOW.minusSeconds(1),
                "SUB001",
                "https://hooks.example.com/original-destination");
        insertFinishedAttempts("RETRY_SNAPSHOT", 1);

        ClaimedNotificationDelivery claimed = claim("RETRY_SNAPSHOT");
        PreparedNotificationDelivery prepared = prepareUseCase.prepare(claimed).orElseThrow();

        assertThat(prepared.destination().endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/original-destination"));
        assertThat(prepared.attemptNumber()).isEqualTo(2);
        assertThat(onlyOpenAttempt("RETRY_SNAPSHOT").origin()).isEqualTo("AUTOMATIC_RETRY");
        assertThat(persistedEvent("RETRY_SNAPSHOT").destinationUrl())
                .isEqualTo("https://hooks.example.com/original-destination");

        insertEvent("NEW_DELIVERY", "PENDING", NOW.minusSeconds(1), null, null);
        PreparedNotificationDelivery newDelivery =
                prepareUseCase.prepare(claim("NEW_DELIVERY")).orElseThrow();

        assertThat(newDelivery.destination().endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/new-destination"));
        assertThat(persistedEvent("NEW_DELIVERY").destinationUrl())
                .isEqualTo("https://hooks.example.com/new-destination");
    }

    @Test
    void preparesTheSameClaimOnlyOnceAcrossConcurrentCalls() throws Exception {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("DUPLICATE_PREPARATION", "PENDING", NOW.minusSeconds(1), null, null);
        ClaimedNotificationDelivery claimed = claim("DUPLICATE_PREPARATION");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Optional<PreparedNotificationDelivery>> first = executor.submit(() -> {
            await(start);
            return prepareUseCase.prepare(claimed);
        });
        Future<Optional<PreparedNotificationDelivery>> second = executor.submit(() -> {
            await(start);
            return prepareUseCase.prepare(claimed);
        });

        try {
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .filteredOn(Optional::isPresent)
                    .hasSize(1);
            assertThat(attemptCount("DUPLICATE_PREPARATION")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completesASuccessfulAttemptWithoutOverwritingTheSourceDeliveryDate() {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("DELIVERY_SUCCESS", "PENDING", NOW.minusSeconds(1), null, null);
        PreparedNotificationDelivery prepared =
                prepareUseCase.prepare(claim("DELIVERY_SUCCESS")).orElseThrow();
        WebhookDeliveryOutcome success = new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 204, null, null, 18);

        assertThat(completeAttemptUseCase.complete(prepared, success)).isTrue();

        PersistedEvent event = persistedEvent("DELIVERY_SUCCESS");
        assertThat(event.status()).isEqualTo("COMPLETED");
        assertThat(event.deliveryDate()).isEqualTo(SOURCE_DELIVERY_DATE);
        assertThat(event.deliveredAt()).isEqualTo(NOW);
        assertThat(event.nextAttemptAt()).isNull();
        assertThat(event.leaseOwner()).isNull();
        assertThat(event.leaseUntil()).isNull();

        PersistedAttempt attempt = onlyAttempt("DELIVERY_SUCCESS");
        assertThat(attempt.result()).isEqualTo("SUCCESS");
        assertThat(attempt.httpStatus()).isEqualTo(204);
        assertThat(attempt.finishedAt()).isEqualTo(NOW);
        assertThat(attempt.latencyMs()).isEqualTo(18);

        assertThat(completeAttemptUseCase.complete(prepared, success)).isFalse();
        assertThat(onlyAttempt("DELIVERY_SUCCESS")).isEqualTo(attempt);
    }

    @Test
    void schedulesARetryUsingTheConfiguredDelay() {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("DELIVERY_RETRY", "PENDING", NOW.minusSeconds(1), null, null);
        PreparedNotificationDelivery prepared =
                prepareUseCase.prepare(claim("DELIVERY_RETRY")).orElseThrow();

        assertThat(completeAttemptUseCase.complete(prepared, retryableOutcome()))
                .isTrue();

        PersistedEvent event = persistedEvent("DELIVERY_RETRY");
        assertThat(event.status()).isEqualTo("RETRY_SCHEDULED");
        assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.deliveredAt()).isNull();
        assertThat(event.leaseOwner()).isNull();
        assertThat(onlyAttempt("DELIVERY_RETRY").result()).isEqualTo("RETRYABLE_FAILURE");
    }

    @Test
    void failsWhenARetryableOutcomeExhaustsThePolicy() {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("DELIVERY_EXHAUSTED", "RETRY_SCHEDULED", NOW.minusSeconds(1), null, null);
        insertFinishedAttempts("DELIVERY_EXHAUSTED", 3);
        PreparedNotificationDelivery prepared =
                prepareUseCase.prepare(claim("DELIVERY_EXHAUSTED")).orElseThrow();

        assertThat(prepared.attemptNumber()).isEqualTo(4);
        assertThat(completeAttemptUseCase.complete(prepared, retryableOutcome()))
                .isTrue();

        PersistedEvent event = persistedEvent("DELIVERY_EXHAUSTED");
        assertThat(event.status()).isEqualTo("FAILED");
        assertThat(event.nextAttemptAt()).isNull();
        assertThat(event.deliveredAt()).isNull();
    }

    @Test
    void completesTheSameAttemptOnlyOnceAcrossConcurrentCalls() throws Exception {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("DUPLICATE_COMPLETION", "PENDING", NOW.minusSeconds(1), null, null);
        PreparedNotificationDelivery prepared =
                prepareUseCase.prepare(claim("DUPLICATE_COMPLETION")).orElseThrow();
        WebhookDeliveryOutcome success = new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 204, null, null, 18);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> first = executor.submit(() -> {
            await(start);
            return completeAttemptUseCase.complete(prepared, success);
        });
        Future<Boolean> second = executor.submit(() -> {
            await(start);
            return completeAttemptUseCase.complete(prepared, success);
        });

        try {
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .filteredOn(Boolean::booleanValue)
                    .hasSize(1);
            assertThat(persistedEvent("DUPLICATE_COMPLETION").status()).isEqualTo("COMPLETED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void processesAClaimedBatchFromPreparationThroughCompletion() {
        insertSubscription("SUB001", "https://hooks.example.com/notifications", true);
        insertEvent("BATCH_001", "PENDING", NOW.minusSeconds(2), null, null);
        insertEvent("BATCH_002", "PENDING", NOW.minusSeconds(1), null, null);

        NotificationDeliveryBatchResult result = processBatchUseCase.process(
                new ClaimNotificationDeliveriesCommand(WORKER_ID, 2, Duration.ofSeconds(30)));

        assertThat(result).isEqualTo(new NotificationDeliveryBatchResult(0, 2, 0, 2, 0, 0));
        assertThat(persistedEvent("BATCH_001").status()).isEqualTo("COMPLETED");
        assertThat(persistedEvent("BATCH_002").status()).isEqualTo("COMPLETED");
        assertThat(onlyAttempt("BATCH_001").result()).isEqualTo("SUCCESS");
        assertThat(onlyAttempt("BATCH_002").result()).isEqualTo("SUCCESS");
    }

    private ClaimedNotificationDelivery claim(String expectedEventId) {
        List<ClaimedNotificationDelivery> claimed =
                claimUseCase.claimDue(new ClaimNotificationDeliveriesCommand(WORKER_ID, 1, Duration.ofSeconds(30)));

        assertThat(claimed)
                .singleElement()
                .extracting(ClaimedNotificationDelivery::eventId)
                .isEqualTo(expectedEventId);
        return claimed.getFirst();
    }

    private void assertConfigurationFailure(String eventId, String expectedCategory) {
        PersistedEvent event = persistedEvent(eventId);
        assertThat(event.status()).isEqualTo("FAILED");
        assertThat(event.deliveryDate()).isEqualTo(SOURCE_DELIVERY_DATE);
        assertThat(event.leaseOwner()).isNull();
        assertThat(event.leaseUntil()).isNull();
        assertThat(event.attemptHistoryComplete()).isFalse();

        PersistedAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.origin()).isEqualTo("INITIAL");
        assertThat(attempt.result()).isEqualTo("PERMANENT_FAILURE");
        assertThat(attempt.finishedAt()).isEqualTo(NOW);
        assertThat(attempt.failureCategory()).isEqualTo(expectedCategory);
        assertThat(attempt.failureDescription()).isNotBlank();
    }

    private void insertSubscription(String subscriptionId, String endpointUrl, boolean active) {
        jdbcTemplate.update(
                """
                        INSERT INTO subscriptions (
                            subscription_id,
                            client_id,
                            endpoint_url,
                            active,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                subscriptionId,
                CLIENT_ID,
                endpointUrl,
                active,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(60)));
        jdbcTemplate.update("""
                        INSERT INTO subscription_event_types (subscription_id, event_type)
                        VALUES (?, ?)
                        """, subscriptionId, EVENT_TYPE);
    }

    private void insertEvent(
            String eventId, String status, Instant nextAttemptAt, String subscriptionId, String destinationUrl) {
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
                            next_attempt_at,
                            attempt_history_complete,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                eventId,
                CLIENT_ID,
                EVENT_TYPE,
                "Test event " + eventId,
                Timestamp.from(NOW.minusSeconds(120)),
                Timestamp.from(SOURCE_DELIVERY_DATE),
                status,
                subscriptionId,
                destinationUrl,
                Timestamp.from(nextAttemptAt),
                subscriptionId != null,
                Timestamp.from(NOW.minusSeconds(120)));
    }

    private void insertFinishedAttempts(String eventId, int count) {
        for (int attemptNumber = 1; attemptNumber <= count; attemptNumber++) {
            jdbcTemplate.update(
                    """
                            INSERT INTO delivery_attempts (
                                attempt_id,
                                event_id,
                                delivery_cycle,
                                attempt_number,
                                origin,
                                started_at,
                                finished_at,
                                result,
                                correlation_id
                            ) VALUES (?, ?, 1, ?, ?, ?, ?, 'RETRYABLE_FAILURE', ?)
                            """,
                    UUID.randomUUID(),
                    eventId,
                    attemptNumber,
                    attemptNumber == 1 ? "INITIAL" : "AUTOMATIC_RETRY",
                    Timestamp.from(NOW.minusSeconds(60L - attemptNumber)),
                    Timestamp.from(NOW.minusSeconds(59L - attemptNumber)),
                    "previous-attempt-" + attemptNumber);
        }
    }

    private PersistedEvent persistedEvent(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT delivery_status,
                               subscription_id,
                               destination_url_snapshot,
                               delivery_date,
                               delivered_at,
                               next_attempt_at,
                               lease_owner,
                               lease_until,
                               attempt_history_complete
                        FROM notification_events
                        WHERE event_id = ?
                        """,
                (resultSet, rowNumber) -> new PersistedEvent(
                        resultSet.getString("delivery_status"),
                        resultSet.getString("subscription_id"),
                        resultSet.getString("destination_url_snapshot"),
                        nullableInstant(resultSet.getTimestamp("delivery_date")),
                        nullableInstant(resultSet.getTimestamp("delivered_at")),
                        nullableInstant(resultSet.getTimestamp("next_attempt_at")),
                        resultSet.getString("lease_owner"),
                        nullableInstant(resultSet.getTimestamp("lease_until")),
                        resultSet.getBoolean("attempt_history_complete")),
                eventId);
    }

    private PersistedAttempt onlyAttempt(String eventId) {
        return jdbcTemplate.queryForObject("""
                        SELECT attempt_number,
                               origin,
                               result,
                               http_status,
                               finished_at,
                               latency_ms,
                               failure_category,
                               failure_description
                        FROM delivery_attempts
                        WHERE event_id = ?
                        """, (resultSet, rowNumber) -> attempt(resultSet), eventId);
    }

    private PersistedAttempt onlyOpenAttempt(String eventId) {
        return jdbcTemplate.queryForObject("""
                        SELECT attempt_number,
                               origin,
                               result,
                               http_status,
                               finished_at,
                               latency_ms,
                               failure_category,
                               failure_description
                        FROM delivery_attempts
                        WHERE event_id = ?
                          AND finished_at IS NULL
                        """, (resultSet, rowNumber) -> attempt(resultSet), eventId);
    }

    private PersistedAttempt attempt(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PersistedAttempt(
                resultSet.getInt("attempt_number"),
                resultSet.getString("origin"),
                resultSet.getString("result"),
                nullableInteger(resultSet, "http_status"),
                nullableInstant(resultSet.getTimestamp("finished_at")),
                nullableLong(resultSet, "latency_ms"),
                resultSet.getString("failure_category"),
                resultSet.getString("failure_description"));
    }

    private int attemptCount(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM delivery_attempts WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private WebhookDeliveryOutcome retryableOutcome() {
        return new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                503,
                NotificationDeliveryFailureCategory.HTTP_RESPONSE,
                "The webhook endpoint returned HTTP 503",
                25);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent preparation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating preparation", exception);
        }
    }

    private record PersistedEvent(
            String status,
            String subscriptionId,
            String destinationUrl,
            Instant deliveryDate,
            Instant deliveredAt,
            Instant nextAttemptAt,
            String leaseOwner,
            Instant leaseUntil,
            boolean attemptHistoryComplete) {}

    private record PersistedAttempt(
            int attemptNumber,
            String origin,
            String result,
            Integer httpStatus,
            Instant finishedAt,
            Long latencyMs,
            String failureCategory,
            String failureDescription) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        NotificationDeliveryGateway successfulDeliveryGateway() {
            return delivery -> new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 204, null, null, 18);
        }
    }
}
