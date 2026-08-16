package com.cobre.notifications.adapter.in.web.security;

import com.cobre.notifications.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BearerTokenAuthenticationTest.TestController.class,
        properties = {
                "notifications.security.clients[0].client-id=CLIENT001",
                "notifications.security.clients[0].token=client-001-test-token",
                "notifications.security.monitoring.token=monitoring-test-token"
        })
@Import({SecurityConfiguration.class, BearerTokenAuthenticationTest.TestController.class})
class BearerTokenAuthenticationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void authenticatesAConfiguredClientToken() throws Exception {
        mockMvc.perform(get("/notification_events/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value("CLIENT001"));
    }

    @Test
    void authorizesOnlyTheMonitoringIdentityForInternalEndpoints() throws Exception {
        mockMvc.perform(get("/internal/monitoring/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer monitoring-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("INTERNAL_MONITORING"));

        mockMvc.perform(get("/internal/monitoring/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-001-test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotAllowTheMonitoringIdentityToImpersonateAClient() throws Exception {
        mockMvc.perform(get("/notification_events/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer monitoring-test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/notification_events/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void rejectsAnInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/notification_events/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void keepsHealthChecksPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectsOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {

        @GetMapping("/notification_events/test")
        Map<String, String> authenticatedClient(@AuthenticationPrincipal ClientPrincipal principal) {
            return Map.of("client_id", principal.clientId());
        }

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping("/internal/monitoring/test")
        Map<String, String> authenticatedMonitoringIdentity(
                @AuthenticationPrincipal MonitoringPrincipal principal) {
            return Map.of("subject", principal.subject());
        }
    }
}
