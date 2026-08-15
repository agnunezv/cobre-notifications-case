package com.cobre.notifications.config;

import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryRetryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationDeliveryRetryConfiguration.class);

    @Test
    void bindsTheRetryPolicyFromExternalConfiguration() {
        contextRunner
                .withPropertyValues(
                        "notifications.delivery.retry.maximum-attempts=3",
                        "notifications.delivery.retry.delays=250ms,2s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RetryPolicy.class);
                    assertThat(context).hasSingleBean(DeliveryLifecycle.class);

                    RetryPolicy policy = context.getBean(RetryPolicy.class);
                    assertThat(policy.maximumAttempts()).isEqualTo(3);
                    assertThat(policy.retryDelays())
                            .containsExactly(Duration.ofMillis(250), Duration.ofSeconds(2));
                });
    }
}
