package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.AmbiguousNotificationSubscriptionException;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.DeliveryPreparationFailureCategory;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.port.inbound.PrepareNotificationDeliveryUseCase;
import com.cobre.notifications.application.port.inbound.ResolveNotificationSubscriptionUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryPreparationRepository;
import com.cobre.notifications.domain.model.InvalidNotificationDestinationException;
import com.cobre.notifications.domain.model.InvalidNotificationSubscriptionException;
import com.cobre.notifications.domain.model.NotificationDestination;
import com.cobre.notifications.domain.model.NotificationSubscription;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
public class NotificationDeliveryPreparationService implements PrepareNotificationDeliveryUseCase {

    private final ResolveNotificationSubscriptionUseCase resolveSubscription;
    private final NotificationDeliveryPreparationRepository repository;
    private final Clock clock;

    public NotificationDeliveryPreparationService(
            ResolveNotificationSubscriptionUseCase resolveSubscription,
            NotificationDeliveryPreparationRepository repository,
            Clock clock) {
        this.resolveSubscription = resolveSubscription;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<PreparedNotificationDelivery> prepare(ClaimedNotificationDelivery claimedDelivery) {
        Instant preparedAt = clock.instant();
        UUID attemptId = UUID.randomUUID();

        if (claimedDelivery.destination() != null) {
            return repository.prepare(
                    claimedDelivery,
                    claimedDelivery.destination(),
                    attemptId,
                    preparedAt);
        }

        try {
            Optional<NotificationSubscription> subscription = resolveSubscription.resolve(
                    new NotificationSubscriptionQuery(
                            claimedDelivery.clientId(),
                            claimedDelivery.eventType()));
            if (subscription.isEmpty()) {
                return reject(
                        claimedDelivery,
                        DeliveryPreparationFailureCategory.SUBSCRIPTION_NOT_FOUND,
                        attemptId,
                        preparedAt);
            }

            return repository.prepare(
                    claimedDelivery,
                    NotificationDestination.from(subscription.orElseThrow()),
                    attemptId,
                    preparedAt);
        } catch (AmbiguousNotificationSubscriptionException exception) {
            return reject(
                    claimedDelivery,
                    DeliveryPreparationFailureCategory.AMBIGUOUS_SUBSCRIPTION,
                    attemptId,
                    preparedAt);
        } catch (ConstraintViolationException
                 | InvalidNotificationSubscriptionException
                 | InvalidNotificationDestinationException exception) {
            return reject(
                    claimedDelivery,
                    DeliveryPreparationFailureCategory.INVALID_DESTINATION,
                    attemptId,
                    preparedAt);
        }
    }

    private Optional<PreparedNotificationDelivery> reject(
            ClaimedNotificationDelivery claimedDelivery,
            DeliveryPreparationFailureCategory failureCategory,
            UUID attemptId,
            Instant failedAt) {
        repository.failConfigurationIfClaimIsCurrent(
                claimedDelivery,
                failureCategory,
                attemptId,
                failedAt);
        return Optional.empty();
    }
}
