package com.cobre.notifications.adapter.in.web.monitoring;

import com.cobre.notifications.application.model.NotificationDeliveryInvestigationQuery;
import com.cobre.notifications.application.port.inbound.InvestigateNotificationDeliveryUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/monitoring/notification_events", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationDeliveryMonitoringController {

    private final InvestigateNotificationDeliveryUseCase investigateDelivery;

    public NotificationDeliveryMonitoringController(InvestigateNotificationDeliveryUseCase investigateDelivery) {
        this.investigateDelivery = investigateDelivery;
    }

    @GetMapping("/{notification_event_id}")
    public NotificationDeliveryInvestigationResponse investigate(
            @PathVariable("notification_event_id") String eventId, @RequestParam("client_id") String clientId) {
        return NotificationDeliveryInvestigationResponse.from(
                investigateDelivery.investigate(new NotificationDeliveryInvestigationQuery(clientId, eventId)));
    }
}
