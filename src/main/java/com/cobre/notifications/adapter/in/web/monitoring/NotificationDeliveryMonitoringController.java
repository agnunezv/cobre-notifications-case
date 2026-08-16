package com.cobre.notifications.adapter.in.web.monitoring;

import com.cobre.notifications.application.model.NotificationDeliveryInvestigationQuery;
import com.cobre.notifications.application.port.inbound.InvestigateNotificationDeliveryUseCase;
import com.cobre.notifications.config.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/monitoring/notification_events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Internal Monitoring", description = "Read-only event delivery investigation for operators")
@SecurityRequirement(name = OpenApiConfiguration.MONITORING_BEARER)
public class NotificationDeliveryMonitoringController {

    private final InvestigateNotificationDeliveryUseCase investigateDelivery;

    public NotificationDeliveryMonitoringController(InvestigateNotificationDeliveryUseCase investigateDelivery) {
        this.investigateDelivery = investigateDelivery;
    }

    @GetMapping("/{notification_event_id}")
    @Operation(
            summary = "Investigate notification delivery",
            description =
                    "Returns persisted operational state and ordered attempt history without payload or destination data.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Delivery investigation returned",
                content = @Content(schema = @Schema(implementation = NotificationDeliveryInvestigationResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid client or event identifier",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid bearer token",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is not the monitoring identity"),
        @ApiResponse(
                responseCode = "404",
                description = "Event does not exist for the supplied client",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public NotificationDeliveryInvestigationResponse investigate(
            @Parameter(description = "Stable notification event identifier", example = "EVT003")
                    @PathVariable("notification_event_id")
                    String eventId,
            @Parameter(description = "Client that owns the event", example = "CLIENT002") @RequestParam("client_id")
                    String clientId) {
        return NotificationDeliveryInvestigationResponse.from(
                investigateDelivery.investigate(new NotificationDeliveryInvestigationQuery(clientId, eventId)));
    }
}
