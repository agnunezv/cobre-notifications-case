package com.cobre.notifications.adapter.in.web.notification;

import com.cobre.notifications.adapter.in.web.security.ClientPrincipal;
import com.cobre.notifications.application.model.NotificationEventDetailsQuery;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.port.inbound.GetNotificationEventDetailsUseCase;
import com.cobre.notifications.application.port.inbound.ListNotificationEventsUseCase;
import com.cobre.notifications.application.port.inbound.ReplayNotificationEventUseCase;
import com.cobre.notifications.config.OpenApiConfiguration;
import com.cobre.notifications.domain.model.DeliveryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Notification Events", description = "Tenant-scoped notification self-service operations")
@SecurityRequirement(name = OpenApiConfiguration.CLIENT_BEARER)
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
    @Operation(
            summary = "List notification events",
            description = "Returns only events owned by the client represented by the bearer token.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tenant-scoped page returned",
                content = @Content(schema = @Schema(implementation = NotificationEventListResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid filters or pagination",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid bearer token",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is not a client identity")
    })
    public NotificationEventListResponse list(
            @Parameter(hidden = true) @AuthenticationPrincipal ClientPrincipal client,
            @Parameter(description = "Inclusive event creation lower bound", example = "2026-08-15T00:00:00Z")
                    @RequestParam(name = "created_from", required = false)
                    Instant createdFrom,
            @Parameter(description = "Exclusive event creation upper bound", example = "2026-08-16T00:00:00Z")
                    @RequestParam(name = "created_to", required = false)
                    Instant createdTo,
            @Parameter(description = "Optional delivery-state filter")
                    @RequestParam(name = "delivery_status", required = false)
                    DeliveryStatus deliveryStatus,
            @Parameter(description = "Zero-based page number", example = "0") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Page size between 1 and 100", example = "20") @RequestParam(defaultValue = "20")
                    int size) {
        NotificationEventQuery query =
                new NotificationEventQuery(client.clientId(), createdFrom, createdTo, deliveryStatus, page, size);
        return NotificationEventListResponse.from(listNotificationEvents.list(query));
    }

    @GetMapping("/{notification_event_id}")
    @Operation(
            summary = "Get one notification event",
            description = "Returns one event only when it belongs to the authenticated client.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tenant-scoped event returned",
                content = @Content(schema = @Schema(implementation = NotificationEventDetailsResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid bearer token",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is not a client identity"),
        @ApiResponse(
                responseCode = "404",
                description = "Event does not exist or belongs to another client",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public NotificationEventDetailsResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal ClientPrincipal client,
            @Parameter(description = "Stable notification event identifier", example = "EVT003")
                    @PathVariable("notification_event_id")
                    String notificationEventId) {
        NotificationEventDetailsQuery query = new NotificationEventDetailsQuery(client.clientId(), notificationEventId);
        return NotificationEventDetailsResponse.from(getNotificationEventDetails.get(query));
    }

    @PostMapping("/{notification_event_id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Replay a failed notification event",
            description = "Creates a new asynchronous delivery cycle only when the tenant-scoped event is FAILED.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Replay accepted"),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid bearer token",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is not a client identity"),
        @ApiResponse(
                responseCode = "404",
                description = "Event does not exist or belongs to another client",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Event is not in FAILED state",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void replay(
            @Parameter(hidden = true) @AuthenticationPrincipal ClientPrincipal client,
            @Parameter(description = "Stable notification event identifier", example = "EVT003")
                    @PathVariable("notification_event_id")
                    String notificationEventId) {
        replayNotificationEvent.replay(new ReplayNotificationEventCommand(client.clientId(), notificationEventId));
    }
}
