package com.cobre.notifications.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationSubscriptionQuery(
        @NotBlank @Size(max = 64) String clientId,
        @NotBlank @Size(max = 128) String eventType) {}
