package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.AmbiguousNotificationSubscriptionException;
import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.application.port.inbound.ResolveNotificationSubscriptionUseCase;
import com.cobre.notifications.application.port.outbound.NotificationSubscriptionRepository;
import com.cobre.notifications.domain.model.InvalidNotificationSubscriptionException;
import com.cobre.notifications.domain.model.NotificationSubscription;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationSubscriptionResolutionService implements ResolveNotificationSubscriptionUseCase {

    private final NotificationSubscriptionRepository repository;

    public NotificationSubscriptionResolutionService(NotificationSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(
            readOnly = true,
            noRollbackFor = {
                AmbiguousNotificationSubscriptionException.class,
                ConstraintViolationException.class,
                InvalidNotificationSubscriptionException.class
            })
    public Optional<NotificationSubscription> resolve(NotificationSubscriptionQuery query) {
        List<NotificationSubscription> matches = repository.findActiveMatches(query);

        if (matches.size() > 1) {
            throw new AmbiguousNotificationSubscriptionException(query);
        }

        return matches.stream().findFirst();
    }
}
