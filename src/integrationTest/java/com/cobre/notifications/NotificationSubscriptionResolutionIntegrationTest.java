package com.cobre.notifications;

import com.cobre.notifications.application.model.AmbiguousNotificationSubscriptionException;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationSubscriptionResolutionIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired
    ResolveNotificationSubscriptionUseCase resolveUseCase;

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
