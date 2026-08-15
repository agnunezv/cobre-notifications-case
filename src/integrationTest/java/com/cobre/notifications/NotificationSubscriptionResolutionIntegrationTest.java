package com.cobre.notifications;

import com.cobre.notifications.application.model.AmbiguousNotificationSubscriptionException;
import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionUseCase;
import com.cobre.notifications.application.port.inbound.ResolveNotificationSubscriptionUseCase;
import com.cobre.notifications.domain.model.InvalidNotificationSubscriptionException;
import com.cobre.notifications.domain.model.NotificationSubscription;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationSubscriptionResolutionIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired
    ResolveNotificationSubscriptionUseCase resolveUseCase;

    @Autowired
    ConfigureNotificationSubscriptionUseCase configureUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSubscriptions() {
        jdbcTemplate.update("DELETE FROM notification_events");
        jdbcTemplate.update("DELETE FROM subscriptions");
    }

    @Test
    void resolvesOnlyTheActiveSubscriptionForTheExactClientAndEventType() {
        insertSubscription("MATCH", "CLIENT001", "https://hooks.example.com/match", true, "credit_payment");
        insertSubscription("INACTIVE", "CLIENT001", "https://hooks.example.com/inactive", false, "credit_payment");
        insertSubscription("OTHER_CLIENT", "CLIENT002", "https://hooks.example.com/client", true, "credit_payment");
        insertSubscription("OTHER_EVENT", "CLIENT001", "https://hooks.example.com/event", true, "debit_payment");

        NotificationSubscription resolved = resolveUseCase.resolve(
                        new NotificationSubscriptionQuery("CLIENT001", "credit_payment"))
                .orElseThrow();

        assertThat(resolved.subscriptionId()).isEqualTo("MATCH");
        assertThat(resolved.clientId()).isEqualTo("CLIENT001");
        assertThat(resolved.endpointUrl()).isEqualTo(URI.create("https://hooks.example.com/match"));
    }

    @Test
    void returnsEmptyWhenThereIsNoActiveMatch() {
        insertSubscription("INACTIVE", "CLIENT001", "https://hooks.example.com/inactive", false, "credit_payment");

        assertThat(resolveUseCase.resolve(
                new NotificationSubscriptionQuery("CLIENT001", "credit_payment"))).isEmpty();
    }

    @Test
    void reportsAmbiguousConfigurationInsteadOfChoosingAnArbitrarySubscription() {
        insertSubscription("FIRST", "CLIENT001", "https://hooks.example.com/first", true, "credit_payment");
        insertSubscription("SECOND", "CLIENT001", "https://hooks.example.com/second", true, "credit_payment");

        assertThatThrownBy(() -> resolveUseCase.resolve(
                new NotificationSubscriptionQuery("CLIENT001", "credit_payment")))
                .isInstanceOf(AmbiguousNotificationSubscriptionException.class)
                .hasMessageContaining("CLIENT001")
                .hasMessageContaining("credit_payment");
    }

    @Test
    void rejectsAnInvalidStoredEndpoint() {
        insertSubscription("INVALID", "CLIENT001", "http://hooks.example.com/invalid", true, "credit_payment");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> resolveUseCase.resolve(
                        new NotificationSubscriptionQuery("CLIENT001", "credit_payment")))
                .withMessageContaining("endpointUrl must be an absolute HTTPS URL");
    }

    @Test
    void rejectsAnInvalidResolutionQueryAtTheApplicationBoundary() {
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> resolveUseCase.resolve(
                        new NotificationSubscriptionQuery(" ", "credit_payment")))
                .withMessageContaining("clientId");
    }

    @Test
    void requiresAResolutionQueryAtTheApplicationBoundary() {
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> resolveUseCase.resolve(null))
                .withMessageContaining("must not be null");
    }

    @Test
    void configuresASubscriptionIdempotentlyAndReplacesItsEventTypes() {
        ConfigureNotificationSubscriptionCommand initial = configuration(
                "CONFIGURED",
                "CLIENT001",
                "https://hooks.example.com/initial",
                Set.of("credit_payment", "debit_payment"),
                NOW);

        configureUseCase.configure(initial);
        configureUseCase.configure(initial);

        assertThat(subscriptionCount("CONFIGURED")).isEqualTo(1);
        assertThat(eventTypes("CONFIGURED"))
                .containsExactlyInAnyOrder("credit_payment", "debit_payment");

        configureUseCase.configure(configuration(
                "CONFIGURED",
                "CLIENT001",
                "https://hooks.example.com/updated",
                Set.of("credit_refund"),
                NOW.plusSeconds(60)));

        assertThat(resolveUseCase.resolve(
                new NotificationSubscriptionQuery("CLIENT001", "credit_payment"))).isEmpty();
        NotificationSubscription updated = resolveUseCase.resolve(
                        new NotificationSubscriptionQuery("CLIENT001", "credit_refund"))
                .orElseThrow();
        assertThat(updated.endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/updated"));
        assertThat(eventTypes("CONFIGURED")).containsExactly("credit_refund");
    }

    @Test
    void doesNotReassignAnExistingSubscriptionToAnotherClient() {
        insertSubscription(
                "OWNED",
                "CLIENT002",
                "https://hooks.example.com/original",
                true,
                "credit_payment");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> configureUseCase.configure(configuration(
                        "OWNED",
                        "CLIENT001",
                        "https://hooks.example.com/reassigned",
                        Set.of("debit_payment"),
                        NOW.plusSeconds(60))))
                .withMessage("Subscription OWNED is already assigned to another client");

        NotificationSubscription original = resolveUseCase.resolve(
                        new NotificationSubscriptionQuery("CLIENT002", "credit_payment"))
                .orElseThrow();
        assertThat(original.endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/original"));
        assertThat(resolveUseCase.resolve(
                new NotificationSubscriptionQuery("CLIENT001", "debit_payment"))).isEmpty();
    }

    private ConfigureNotificationSubscriptionCommand configuration(
            String subscriptionId,
            String clientId,
            String endpointUrl,
            Set<String> eventTypes,
            Instant configuredAt) {
        return new ConfigureNotificationSubscriptionCommand(
                new NotificationSubscription(
                        subscriptionId,
                        clientId,
                        URI.create(endpointUrl)),
                eventTypes,
                configuredAt);
    }

    private int subscriptionCount(String subscriptionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE subscription_id = ?",
                Integer.class,
                subscriptionId);
        return count == null ? 0 : count;
    }

    private java.util.List<String> eventTypes(String subscriptionId) {
        return jdbcTemplate.queryForList(
                """
                SELECT event_type
                FROM subscription_event_types
                WHERE subscription_id = ?
                ORDER BY event_type
                """,
                String.class,
                subscriptionId);
    }

    private void insertSubscription(
            String subscriptionId,
            String clientId,
            String endpointUrl,
            boolean active,
            String eventType) {
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
                clientId,
                endpointUrl,
                active,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbcTemplate.update("""
                        INSERT INTO subscription_event_types (subscription_id, event_type)
                        VALUES (?, ?)
                        """,
                subscriptionId,
                eventType);
    }
}
