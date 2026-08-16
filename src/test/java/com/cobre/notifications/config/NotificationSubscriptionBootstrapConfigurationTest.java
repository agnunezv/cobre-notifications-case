package com.cobre.notifications.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.adapter.in.bootstrap.NotificationSubscriptionBootstrap;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionsUseCase;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationSubscriptionBootstrapConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationSubscriptionBootstrapConfiguration.class)
            .withBean(ConfigureNotificationSubscriptionsUseCase.class, () -> commands -> {})
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void keepsSubscriptionBootstrapDisabledUnlessExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("notifications.subscription-bootstrap.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationSubscriptionBootstrap.class);
                });
    }

    @Test
    void createsTheBootstrapAndBindsMultipleSubscriptionsWhenEnabled() {
        contextRunner.withPropertyValues(validProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationSubscriptionBootstrap.class);

            NotificationSubscriptionBootstrapProperties properties =
                    context.getBean(NotificationSubscriptionBootstrapProperties.class);
            assertThat(properties.subscriptions())
                    .extracting(NotificationSubscriptionBootstrapProperties.SubscriptionProperties::subscriptionId)
                    .containsExactly("SUB001", "SUB002", "SUB003");
            assertThat(properties.subscriptions().get(0).eventTypes())
                    .containsExactly("credit_transfer", "debit_transfer");
            assertThat(properties.subscriptions().get(0).endpointUrl())
                    .isEqualTo(properties.subscriptions().get(1).endpointUrl());
            assertThat(properties.subscriptions().get(1).endpointUrl())
                    .isEqualTo(properties.subscriptions().get(2).endpointUrl());
        });
    }

    @Test
    void rejectsAnIncompleteEnabledConfiguration() {
        contextRunner
                .withPropertyValues(
                        "notifications.subscription-bootstrap.enabled=true",
                        "notifications.subscription-bootstrap.subscriptions[0].subscription-id=SUB001",
                        "notifications.subscription-bootstrap.subscriptions[0].client-id=CLIENT001",
                        "notifications.subscription-bootstrap.subscriptions[0].endpoint-url=http://hooks.example.com",
                        "notifications.subscription-bootstrap.subscriptions[0].event-types=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDuplicateSubscriptionIds() {
        contextRunner
                .withPropertyValues(validProperties())
                .withPropertyValues("notifications.subscription-bootstrap.subscriptions[1].subscription-id=SUB001")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDuplicateClientAndEventTypeRoutes() {
        contextRunner
                .withPropertyValues(validProperties())
                .withPropertyValues(
                        "notifications.subscription-bootstrap.subscriptions[1].client-id=CLIENT001",
                        "notifications.subscription-bootstrap.subscriptions[1].event-types=credit_transfer")
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] validProperties() {
        return new String[] {
            "notifications.subscription-bootstrap.enabled=true",
            "notifications.subscription-bootstrap.subscriptions[0].subscription-id=SUB001",
            "notifications.subscription-bootstrap.subscriptions[0].client-id=CLIENT001",
            "notifications.subscription-bootstrap.subscriptions[0].endpoint-url=https://hooks.example.com/shared",
            "notifications.subscription-bootstrap.subscriptions[0].event-types=credit_transfer,debit_transfer",
            "notifications.subscription-bootstrap.subscriptions[1].subscription-id=SUB002",
            "notifications.subscription-bootstrap.subscriptions[1].client-id=CLIENT002",
            "notifications.subscription-bootstrap.subscriptions[1].endpoint-url=https://hooks.example.com/shared",
            "notifications.subscription-bootstrap.subscriptions[1].event-types=credit_transfer",
            "notifications.subscription-bootstrap.subscriptions[2].subscription-id=SUB003",
            "notifications.subscription-bootstrap.subscriptions[2].client-id=CLIENT003",
            "notifications.subscription-bootstrap.subscriptions[2].endpoint-url=https://hooks.example.com/shared",
            "notifications.subscription-bootstrap.subscriptions[2].event-types=credit_refund"
        };
    }
}
