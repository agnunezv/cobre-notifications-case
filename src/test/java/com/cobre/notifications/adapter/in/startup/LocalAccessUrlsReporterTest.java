package com.cobre.notifications.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class LocalAccessUrlsReporterTest {

    @Test
    void reportsSwaggerAndGrafanaForTheObservabilityRun(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("springdoc.swagger-ui.path", "/swagger-ui.html")
                .withProperty("spring.docker.compose.profiles.active", "observability")
                .withProperty("GRAFANA_PORT", "3100");

        new LocalAccessUrlsReporter(environment).onApplicationEvent(readyEvent(8181));

        assertThat(output)
                .contains("Swagger UI: http://localhost:8181/swagger-ui.html")
                .contains("Grafana: http://localhost:3100");
    }

    @Test
    void omitsGrafanaForTheStandardRun(CapturedOutput output) {
        new LocalAccessUrlsReporter(new MockEnvironment()).onApplicationEvent(readyEvent(8080));

        assertThat(output)
                .contains("Swagger UI: http://localhost:8080/swagger-ui.html")
                .doesNotContain("Grafana:");
    }

    private static ApplicationReadyEvent readyEvent(int port) {
        ServletWebServerApplicationContext webApplicationContext = mock(ServletWebServerApplicationContext.class);
        WebServer webServer = mock(WebServer.class);
        when(webApplicationContext.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(port);

        return new ApplicationReadyEvent(
                new SpringApplication(LocalAccessUrlsReporter.class),
                new String[0],
                webApplicationContext,
                Duration.ZERO);
    }
}
