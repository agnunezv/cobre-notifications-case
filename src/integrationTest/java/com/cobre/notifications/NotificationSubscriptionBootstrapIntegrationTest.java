package com.cobre.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.adapter.in.bootstrap.NotificationSubscriptionBootstrap;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.port.inbound.ResolveNotificationSubscriptionUseCase;
import com.cobre.notifications.domain.model.NotificationSubscription;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "notifications.subscription-bootstrap.enabled=true",
            "notifications.subscription-bootstrap.subscriptions[0].subscription-id=CLIENT001_DEFAULT",
            "notifications.subscription-bootstrap.subscriptions[0].client-id=CLIENT001",
            "notifications.subscription-bootstrap.subscriptions[0].endpoint-url=https://hooks.example.com/shared",
            "notifications.subscription-bootstrap.subscriptions[0].event-types=credit_transfer,credit_refund",
            "notifications.subscription-bootstrap.subscriptions[1].subscription-id=CLIENT002_DEFAULT",
            "notifications.subscription-bootstrap.subscriptions[1].client-id=CLIENT002",
            "notifications.subscription-bootstrap.subscriptions[1].endpoint-url=https://hooks.example.com/shared",
            "notifications.subscription-bootstrap.subscriptions[1].event-types=credit_transfer",
            "notifications.subscription-bootstrap.subscriptions[2].subscription-id=CLIENT003_DEFAULT",
            "notifications.subscription-bootstrap.subscriptions[2].client-id=CLIENT003",
            "notifications.subscription-bootstrap.subscriptions[2].endpoint-url=https://hooks.example.com/shared",
            "notifications.subscription-bootstrap.subscriptions[2].event-types=credit_refund"
        })
class NotificationSubscriptionBootstrapIntegrationTest extends PostgresqlIntegrationTestSupport {

    @Autowired
    NotificationSubscriptionBootstrap bootstrap;

    @Autowired
    ResolveNotificationSubscriptionUseCase resolveUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM delivery_attempts");
        jdbcTemplate.update("DELETE FROM notification_events");
        jdbcTemplate.update("DELETE FROM subscriptions");
    }

    @Test
    void configuresAllKnownSubscriptionsWithOneSharedEndpointWithoutCreatingDuplicates() {
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        NotificationSubscription clientOneCreditTransfer = resolveUseCase
                .resolve(new NotificationSubscriptionQuery("CLIENT001", "credit_transfer"))
                .orElseThrow();
        NotificationSubscription clientOneCreditRefund = resolveUseCase
                .resolve(new NotificationSubscriptionQuery("CLIENT001", "credit_refund"))
                .orElseThrow();
        NotificationSubscription clientTwoCreditTransfer = resolveUseCase
                .resolve(new NotificationSubscriptionQuery("CLIENT002", "credit_transfer"))
                .orElseThrow();
        NotificationSubscription clientThreeCreditRefund = resolveUseCase
                .resolve(new NotificationSubscriptionQuery("CLIENT003", "credit_refund"))
                .orElseThrow();

        assertThat(clientOneCreditTransfer).isEqualTo(clientOneCreditRefund);
        assertThat(clientOneCreditTransfer.subscriptionId()).isEqualTo("CLIENT001_DEFAULT");
        assertThat(clientOneCreditTransfer.endpointUrl()).isEqualTo(URI.create("https://hooks.example.com/shared"));
        assertThat(clientTwoCreditTransfer.subscriptionId()).isEqualTo("CLIENT002_DEFAULT");
        assertThat(clientTwoCreditTransfer.endpointUrl()).isEqualTo(URI.create("https://hooks.example.com/shared"));
        assertThat(clientThreeCreditRefund.subscriptionId()).isEqualTo("CLIENT003_DEFAULT");
        assertThat(clientThreeCreditRefund.endpointUrl()).isEqualTo(URI.create("https://hooks.example.com/shared"));
        assertThat(subscriptionCount()).isEqualTo(3);
        assertThat(eventTypeCount()).isEqualTo(4);
    }

    private int subscriptionCount() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM subscriptions
                WHERE subscription_id IN ('CLIENT001_DEFAULT', 'CLIENT002_DEFAULT', 'CLIENT003_DEFAULT')
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private int eventTypeCount() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM subscription_event_types
                WHERE subscription_id IN ('CLIENT001_DEFAULT', 'CLIENT002_DEFAULT', 'CLIENT003_DEFAULT')
                """, Integer.class);
        return count == null ? 0 : count;
    }
}
