package com.cobre.notifications.adapter.out.observability;

import com.cobre.notifications.application.port.outbound.NotificationDeliveryBacklogRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class NotificationDeliveryBacklogMeterBinder implements MeterBinder {

    static final String DUE_BACKLOG = "cobre.notifications.delivery.backlog.due";
    static final String OLDEST_DUE_AGE = "cobre.notifications.delivery.backlog.oldest.age";

    private final NotificationDeliveryBacklogRepository repository;
    private final Clock clock;

    public NotificationDeliveryBacklogMeterBinder(
            NotificationDeliveryBacklogRepository repository,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(DUE_BACKLOG, this, ignored -> dueCount())
                .description("Notification events currently due for delivery")
                .baseUnit("events")
                .register(registry);
        Gauge.builder(OLDEST_DUE_AGE, this, ignored -> oldestDueAgeSeconds())
                .description("Age of the oldest notification event currently due for delivery")
                .baseUnit("seconds")
                .register(registry);
    }

    private double dueCount() {
        try {
            return repository.countDue(clock.instant());
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }

    private double oldestDueAgeSeconds() {
        try {
            return repository.oldestDueAge(clock.instant()).toMillis() / 1_000.0;
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }
}
