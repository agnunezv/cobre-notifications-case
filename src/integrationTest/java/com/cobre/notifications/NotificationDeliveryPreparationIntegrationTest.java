package com.cobre.notifications;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.inbound.PrepareNotificationDeliveryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(NotificationDeliveryPreparationIntegrationTest.FixedClockConfiguration.class)
class NotificationDeliveryPreparationIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final String CLIENT_ID = "CLIENT001";
    private static final String EVENT_TYPE = "credit_payment";
    private static final String WORKER_ID = "worker-1";

    @Autowired
    ClaimNotificationDeliveriesUseCase claimUseCase;

    @Autowired
    PrepareNotificationDeliveryUseCase prepareUseCase;

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
    void reusesTheBoundDestinationForAutomaticRetries() {
        insertSubscription("SUB001", "https://hooks.example.com/new-destination", false);
        insertEvent(
                "RETRY_SNAPSHOT",
                "RETRY_SCHEDULED",
                NOW.minusSeconds(1),
                "SUB001",
                "https://hooks.example.com/original-destination");
        insertFinishedAttempt("RETRY_SNAPSHOT");

        ClaimedNotificationDelivery claimed = claim("RETRY_SNAPSHOT");
        PreparedNotificationDelivery prepared = prepareUseCase.prepare(claimed).orElseThrow();

        assertThat(prepared.destination().endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/original-destination"));
        assertThat(prepared.attemptNumber()).isEqualTo(2);
        assertThat(onlyOpenAttempt("RETRY_SNAPSHOT").origin()).isEqualTo("AUTOMATIC_RETRY");
        assertThat(persistedEvent("RETRY_SNAPSHOT").destinationUrl())
                .isEqualTo("https://hooks.example.com/original-destination");
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

    private ClaimedNotificationDelivery claim(String expectedEventId) {
        List<ClaimedNotificationDelivery> claimed = claimUseCase.claimDue(
                new ClaimNotificationDeliveriesCommand(WORKER_ID, 1, Duration.ofSeconds(30)));

        assertThat(claimed).singleElement()
                .extracting(ClaimedNotificationDelivery::eventId)
                .isEqualTo(expectedEventId);
        return claimed.getFirst();
    }

    private void assertConfigurationFailure(String eventId, String expectedCategory) {
        PersistedEvent event = persistedEvent(eventId);
        assertThat(event.status()).isEqualTo("FAILED");
        assertThat(event.deliveryDate()).isEqualTo(NOW);
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
        jdbcTemplate.update("""
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
                        """,
                subscriptionId,
                EVENT_TYPE);
    }

    private void insertEvent(
            String eventId,
            String status,
            Instant nextAttemptAt,
            String subscriptionId,
            String destinationUrl) {
        jdbcTemplate.update("""
                        INSERT INTO notification_events (
                            event_id,
                            client_id,
                            event_type,
                            content,
                            created_at,
                            delivery_status,
                            subscription_id,
                            destination_url_snapshot,
                            next_attempt_at,
                            attempt_history_complete,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                eventId,
                CLIENT_ID,
                EVENT_TYPE,
                "Test event " + eventId,
                Timestamp.from(NOW.minusSeconds(120)),
                status,
                subscriptionId,
                destinationUrl,
                Timestamp.from(nextAttemptAt),
                subscriptionId != null,
                Timestamp.from(NOW.minusSeconds(120)));
    }

    private void insertFinishedAttempt(String eventId) {
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
                            correlation_id
                        ) VALUES (?, ?, 1, 1, 'INITIAL', ?, ?, 'RETRYABLE_FAILURE', ?)
                        """,
                UUID.randomUUID(),
                eventId,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(59)),
                "previous-attempt");
    }

    private PersistedEvent persistedEvent(String eventId) {
        return jdbcTemplate.queryForObject("""
                        SELECT delivery_status,
                               subscription_id,
                               destination_url_snapshot,
                               delivery_date,
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
                               finished_at,
                               failure_category,
                               failure_description
                        FROM delivery_attempts
                        WHERE event_id = ?
                        """,
                (resultSet, rowNumber) -> attempt(resultSet),
                eventId);
    }

    private PersistedAttempt onlyOpenAttempt(String eventId) {
        return jdbcTemplate.queryForObject("""
                        SELECT attempt_number,
                               origin,
                               result,
                               finished_at,
                               failure_category,
                               failure_description
                        FROM delivery_attempts
                        WHERE event_id = ?
                          AND finished_at IS NULL
                        """,
                (resultSet, rowNumber) -> attempt(resultSet),
                eventId);
    }

    private PersistedAttempt attempt(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PersistedAttempt(
                resultSet.getInt("attempt_number"),
                resultSet.getString("origin"),
                resultSet.getString("result"),
                nullableInstant(resultSet.getTimestamp("finished_at")),
                resultSet.getString("failure_category"),
                resultSet.getString("failure_description"));
    }

    private int attemptCount(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM delivery_attempts WHERE event_id = ?",
                Integer.class,
                eventId);
        return count == null ? 0 : count;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
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
            String leaseOwner,
            Instant leaseUntil,
            boolean attemptHistoryComplete) {
    }

    private record PersistedAttempt(
            int attemptNumber,
            String origin,
            String result,
            Instant finishedAt,
            String failureCategory,
            String failureDescription) {
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
