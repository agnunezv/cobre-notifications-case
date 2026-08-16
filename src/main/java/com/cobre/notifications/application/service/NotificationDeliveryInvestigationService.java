package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.NotificationDeliveryInvestigation;
import com.cobre.notifications.application.model.NotificationDeliveryInvestigationQuery;
import com.cobre.notifications.application.model.NotificationEventNotFoundException;
import com.cobre.notifications.application.port.inbound.InvestigateNotificationDeliveryUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryInvestigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationDeliveryInvestigationService
        implements InvestigateNotificationDeliveryUseCase {

    private final NotificationDeliveryInvestigationRepository repository;

    public NotificationDeliveryInvestigationService(
            NotificationDeliveryInvestigationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDeliveryInvestigation investigate(
            NotificationDeliveryInvestigationQuery query) {
        return repository.find(query)
                .orElseThrow(NotificationEventNotFoundException::new);
    }
}
