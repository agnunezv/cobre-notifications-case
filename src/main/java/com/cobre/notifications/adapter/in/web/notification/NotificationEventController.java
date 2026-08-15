package com.cobre.notifications.adapter.in.web.notification;

import com.cobre.notifications.adapter.in.web.security.ClientPrincipal;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.port.inbound.ListNotificationEventsUseCase;
import com.cobre.notifications.domain.model.DeliveryStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping(path = "/notification_events", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationEventController {

    private final ListNotificationEventsUseCase listNotificationEvents;

    public NotificationEventController(ListNotificationEventsUseCase listNotificationEvents) {
        this.listNotificationEvents = listNotificationEvents;
    }

    @GetMapping
    public NotificationEventListResponse list(
            @AuthenticationPrincipal ClientPrincipal client,
            @RequestParam(name = "created_from", required = false) Instant createdFrom,
            @RequestParam(name = "created_to", required = false) Instant createdTo,
            @RequestParam(name = "delivery_status", required = false) DeliveryStatus deliveryStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        NotificationEventQuery query = new NotificationEventQuery(
                client.clientId(),
                createdFrom,
                createdTo,
                deliveryStatus,
                page,
                size);
        return NotificationEventListResponse.from(listNotificationEvents.list(query));
    }
}
