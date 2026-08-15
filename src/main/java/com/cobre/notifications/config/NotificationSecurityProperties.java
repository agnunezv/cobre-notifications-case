package com.cobre.notifications.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "notifications.security")
public record NotificationSecurityProperties(
        @NotNull List<@NotNull @Valid ClientCredential> clients) {

    public NotificationSecurityProperties {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    public record ClientCredential(
            @Size(max = 64) String clientId,
            String token) {

        @AssertTrue(message = "clientId is required when a bearer token is configured")
        public boolean isClientIdPresentWhenTokenConfigured() {
            return token == null
                    || token.isBlank()
                    || clientId != null && !clientId.isBlank();
        }
    }
}
