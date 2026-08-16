package com.cobre.notifications.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Cobre Notification Platform API",
                        version = "v1",
                        description =
                                "Tenant-scoped notification self-service and read-only operational investigation endpoints."))
@SecurityScheme(
        name = OpenApiConfiguration.CLIENT_BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "opaque",
        description = "Opaque token mapped by configuration to one client identity.")
@SecurityScheme(
        name = OpenApiConfiguration.MONITORING_BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "opaque",
        description = "Opaque read-only token for internal monitoring endpoints.")
public class OpenApiConfiguration {

    public static final String CLIENT_BEARER = "clientBearer";
    public static final String MONITORING_BEARER = "monitoringBearer";
}
