package com.cobre.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "notifications.security.clients[0].client-id=CLIENT001",
            "notifications.security.clients[0].token=client-001-integration-token",
            "notifications.security.monitoring.token=monitoring-integration-token"
        })
@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTest extends PostgresqlIntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void exposesSwaggerUiWithoutAuthenticationForTheLocalDemo() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        String swaggerUi = mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(swaggerUi).contains("<title>Swagger UI</title>");
    }

    @Test
    void publishesTheImplementedApiWithRoleSpecificOpaqueBearerSchemes() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode openApi = objectMapper.readTree(document);
        JsonNode paths = openApi.path("paths");
        Set<String> documentedPaths = new HashSet<>();
        paths.fieldNames().forEachRemaining(documentedPaths::add);
        assertThat(documentedPaths)
                .isEqualTo(Set.of(
                        "/notification_events",
                        "/notification_events/{notification_event_id}",
                        "/notification_events/{notification_event_id}/replay",
                        "/internal/monitoring/notification_events/{notification_event_id}"));

        assertBearerScheme(openApi, "clientBearer");
        assertBearerScheme(openApi, "monitoringBearer");
        assertThat(paths.path("/notification_events")
                        .path("get")
                        .path("security")
                        .path(0)
                        .path("clientBearer")
                        .isArray())
                .isTrue();
        assertThat(paths.path("/internal/monitoring/notification_events/{notification_event_id}")
                        .path("get")
                        .path("security")
                        .path(0)
                        .path("monitoringBearer")
                        .isArray())
                .isTrue();

        JsonNode listParameters = paths.path("/notification_events").path("get").path("parameters");
        Set<String> listParameterNames = new HashSet<>();
        listParameters.forEach(
                parameter -> listParameterNames.add(parameter.path("name").asText()));
        assertThat(listParameterNames)
                .isEqualTo(Set.of("created_from", "created_to", "delivery_status", "page", "size"));
        assertThat(paths.path("/notification_events/{notification_event_id}/replay")
                        .path("post")
                        .path("responses")
                        .has("202"))
                .isTrue();
        assertThat(paths.path("/notification_events/{notification_event_id}")
                        .path("get")
                        .path("responses")
                        .has("404"))
                .isTrue();

        JsonNode monitoringProperties =
                openApi.at("/components/schemas/NotificationDeliveryInvestigationResponse/properties");
        assertThat(monitoringProperties.has("content")).isFalse();
        assertThat(monitoringProperties.has("destination_url")).isFalse();
    }

    private void assertBearerScheme(JsonNode openApi, String name) {
        JsonNode scheme = openApi.at("/components/securitySchemes/" + name);
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("opaque");
    }
}
