package com.cobre.notifications.adapter.in.web.notification;

import com.cobre.notifications.adapter.in.web.security.ClientPrincipal;
import com.cobre.notifications.application.model.NotificationEventDetailsQuery;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.port.inbound.GetNotificationEventDetailsUseCase;
import com.cobre.notifications.application.port.inbound.ListNotificationEventsUseCase;
import com.cobre.notifications.application.port.inbound.ReplayNotificationEventUseCase;
import com.cobre.notifications.domain.model.DeliveryStatus;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/notification_events", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationEventController {

    private final ListNotificationEventsUseCase listNotificationEvents;
    private final GetNotificationEventDetailsUseCase getNotificationEventDetails;
    private final ReplayNotificationEventUseCase replayNotificationEvent;

    public NotificationEventController(
            ListNotificationEventsUseCase listNotificationEvents,
            GetNotificationEventDetailsUseCase getNotificationEventDetails,
            ReplayNotificationEventUseCase replayNotificationEvent) {
        this.listNotificationEvents = listNotificationEvents;
        this.getNotificationEventDetails = getNotificationEventDetails;
        this.replayNotificationEvent = replayNotificationEvent;
    }

    @GetMapping
    public NotificationEventListResponse list(
            @AuthenticationPrincipal ClientPrincipal client,
            @RequestParam(name = "created_from", required = false) Instant createdFrom,
            @RequestParam(name = "created_to", required = false) Instant createdTo,
            @RequestParam(name = "delivery_status", required = false) DeliveryStatus deliveryStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        NotificationEventQuery query =
                new NotificationEventQuery(client.clientId(), createdFrom, createdTo, deliveryStatus, page, size);
        return NotificationEventListResponse.from(listNotificationEvents.list(query));
    }

    @GetMapping("/{notification_event_id}")
    public NotificationEventDetailsResponse get(
            @AuthenticationPrincipal ClientPrincipal client,
            @PathVariable("notification_event_id") String notificationEventId) {
        NotificationEventDetailsQuery query = new NotificationEventDetailsQuery(client.clientId(), notificationEventId);
        return NotificationEventDetailsResponse.from(getNotificationEventDetails.get(query));
    }

    @PostMapping("/{notification_event_id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void replay(
            @AuthenticationPrincipal ClientPrincipal client,
            @PathVariable("notification_event_id") String notificationEventId) {
        replayNotificationEvent.replay(new ReplayNotificationEventCommand(client.clientId(), notificationEventId));
    }
}
