package com.cobre.notifications.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplayNotificationEventCommand(
        @NotBlank(message = "An authenticated client is required") @Size(max = 64, message = "clientId must not exceed 64 characters") String clientId,

        @NotBlank(message = "notificationEventId is required") @Size(max = 64, message = "notificationEventId must not exceed 64 characters") String notificationEventId) {}
