package com.cobre.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "notifications.security")
public record NotificationSecurityProperties(List<ClientCredential> clients) {

    public NotificationSecurityProperties {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    public record ClientCredential(String clientId, String token) {
    }
}
