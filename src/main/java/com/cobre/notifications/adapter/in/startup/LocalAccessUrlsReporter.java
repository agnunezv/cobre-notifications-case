package com.cobre.notifications.adapter.in.startup;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class LocalAccessUrlsReporter implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalAccessUrlsReporter.class);

    private final Environment environment;

    public LocalAccessUrlsReporter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext webApplicationContext)) {
            return;
        }

        int applicationPort = webApplicationContext.getWebServer().getPort();
        String swaggerPath = environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
        boolean swaggerEnabled = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true);

        StringBuilder message = new StringBuilder("Local access URLs:");
        if (swaggerEnabled) {
            message.append(System.lineSeparator())
                    .append("  Swagger UI: http://localhost:")
                    .append(applicationPort)
                    .append(swaggerPath);
        }
        if (observabilityEnabled()) {
            String grafanaPort = environment.getProperty("GRAFANA_PORT", "3000");
            message.append(System.lineSeparator())
                    .append("  Grafana: http://localhost:")
                    .append(grafanaPort);
        }

        LOGGER.info(message.toString());
    }

    private boolean observabilityEnabled() {
        String composeProfiles = environment.getProperty("spring.docker.compose.profiles.active", "");
        return Arrays.stream(composeProfiles.split(",")).map(String::trim).anyMatch("observability"::equalsIgnoreCase);
    }
}
