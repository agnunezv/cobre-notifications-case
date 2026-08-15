package com.cobre.notifications;

import com.cobre.notifications.adapter.in.bootstrap.NotificationSubscriptionBootstrap;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.port.inbound.ResolveNotificationSubscriptionUseCase;
import com.cobre.notifications.domain.model.NotificationSubscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "notifications.subscription-bootstrap.enabled=true",
                "notifications.subscription-bootstrap.subscription-id=BOOTSTRAP_SUB",
                "notifications.subscription-bootstrap.client-id=CLIENT002",
                "notifications.subscription-bootstrap.endpoint-url=https://hooks.example.com/demo",
                "notifications.subscription-bootstrap.event-types=credit_transfer,debit_transfer"
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
    void configuresTheDemoSubscriptionAtStartupWithoutCreatingDuplicates() {
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        NotificationSubscription creditTransfer = resolveUseCase.resolve(
                        new NotificationSubscriptionQuery("CLIENT002", "credit_transfer"))
                .orElseThrow();
        NotificationSubscription debitTransfer = resolveUseCase.resolve(
                        new NotificationSubscriptionQuery("CLIENT002", "debit_transfer"))
                .orElseThrow();

        assertThat(creditTransfer).isEqualTo(debitTransfer);
        assertThat(creditTransfer.subscriptionId()).isEqualTo("BOOTSTRAP_SUB");
        assertThat(creditTransfer.endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com/demo"));
        assertThat(subscriptionCount()).isEqualTo(1);
        assertThat(eventTypeCount()).isEqualTo(2);
    }

    private int subscriptionCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE subscription_id = 'BOOTSTRAP_SUB'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private int eventTypeCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM subscription_event_types
                WHERE subscription_id = 'BOOTSTRAP_SUB'
                """,
                Integer.class);
        return count == null ? 0 : count;
    }
}
