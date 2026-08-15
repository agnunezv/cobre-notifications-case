package com.cobre.notifications.adapter.out.observability;

import com.cobre.notifications.application.port.outbound.NotificationDeliveryBacklogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryBacklogMeterBinderTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Test
    void exposesCurrentBacklogAndOldestDueAge() {
        NotificationDeliveryBacklogRepository repository = new NotificationDeliveryBacklogRepository() {
            @Override
            public long countDue(Instant observedAt) {
                assertThat(observedAt).isEqualTo(NOW);
                return 3;
            }

            @Override
            public Duration oldestDueAge(Instant observedAt) {
                assertThat(observedAt).isEqualTo(NOW);
                return Duration.ofMillis(7_500);
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new NotificationDeliveryBacklogMeterBinder(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC))
                .bindTo(registry);

        assertThat(registry.get(NotificationDeliveryBacklogMeterBinder.DUE_BACKLOG)
                .gauge()
                .value()).isEqualTo(3);
        assertThat(registry.get(NotificationDeliveryBacklogMeterBinder.OLDEST_DUE_AGE)
                .gauge()
                .value()).isEqualTo(7.5);
    }

    @Test
    void reportsAnUnavailableBacklogAsNotANumber() {
        NotificationDeliveryBacklogRepository repository = new NotificationDeliveryBacklogRepository() {
            @Override
            public long countDue(Instant observedAt) {
                throw new IllegalStateException("PostgreSQL unavailable");
            }

            @Override
            public Duration oldestDueAge(Instant observedAt) {
                throw new IllegalStateException("PostgreSQL unavailable");
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new NotificationDeliveryBacklogMeterBinder(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC))
                .bindTo(registry);

        assertThat(registry.get(NotificationDeliveryBacklogMeterBinder.DUE_BACKLOG)
                .gauge()
                .value()).isNaN();
        assertThat(registry.get(NotificationDeliveryBacklogMeterBinder.OLDEST_DUE_AGE)
                .gauge()
                .value()).isNaN();
    }
}
