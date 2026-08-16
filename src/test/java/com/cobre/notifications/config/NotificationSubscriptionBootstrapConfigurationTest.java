package com.cobre.notifications.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.adapter.in.bootstrap.NotificationSubscriptionBootstrap;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionUseCase;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationSubscriptionBootstrapConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationSubscriptionBootstrapConfiguration.class)
            .withBean(ConfigureNotificationSubscriptionUseCase.class, () -> command -> {})
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
    void createsTheBootstrapAndBindsEventTypesWhenEnabled() {
        contextRunner.withPropertyValues(validProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationSubscriptionBootstrap.class);

            NotificationSubscriptionBootstrapProperties properties =
                    context.getBean(NotificationSubscriptionBootstrapProperties.class);
            assertThat(properties.eventTypes()).containsExactly("credit_transfer", "debit_transfer");
        });
    }

    @Test
    void rejectsAnIncompleteEnabledConfiguration() {
        contextRunner
                .withPropertyValues(
                        "notifications.subscription-bootstrap.enabled=true",
                        "notifications.subscription-bootstrap.subscription-id=SUB001",
                        "notifications.subscription-bootstrap.client-id=CLIENT001",
                        "notifications.subscription-bootstrap.endpoint-url=http://hooks.example.com",
                        "notifications.subscription-bootstrap.event-types=")
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] validProperties() {
        return new String[] {
            "notifications.subscription-bootstrap.enabled=true",
            "notifications.subscription-bootstrap.subscription-id=SUB001",
            "notifications.subscription-bootstrap.client-id=CLIENT001",
            "notifications.subscription-bootstrap.endpoint-url=https://hooks.example.com/notifications",
            "notifications.subscription-bootstrap.event-types=credit_transfer,debit_transfer"
        };
    }
}
