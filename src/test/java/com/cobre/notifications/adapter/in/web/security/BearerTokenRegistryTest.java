package com.cobre.notifications.adapter.in.web.security;

import com.cobre.notifications.config.NotificationSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BearerTokenRegistryTest {

    @Test
    void resolvesClientAndMonitoringTokensToDifferentAuthorities() {
        BearerTokenRegistry registry = new BearerTokenRegistry(properties(
                "client-token",
                "monitoring-token"));

        assertThat(registry.resolve("client-token"))
                .contains(new BearerTokenRegistry.ResolvedPrincipal(
                        "CLIENT001",
                        BearerTokenRegistry.CLIENT_AUTHORITY));
        assertThat(registry.resolve("monitoring-token"))
                .contains(new BearerTokenRegistry.ResolvedPrincipal(
                        "INTERNAL_MONITORING",
                        BearerTokenRegistry.MONITORING_AUTHORITY));
    }

    @Test
    void rejectsASecretSharedByClientAndMonitoringIdentities() {
        assertThatThrownBy(() -> new BearerTokenRegistry(properties(
                "shared-token",
                "shared-token")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Each principal must have a unique bearer token");
    }

    private NotificationSecurityProperties properties(
            String clientToken,
            String monitoringToken) {
        return new NotificationSecurityProperties(
                List.of(new NotificationSecurityProperties.ClientCredential(
                        "CLIENT001",
                        clientToken)),
                new NotificationSecurityProperties.MonitoringCredential(monitoringToken));
    }
}
