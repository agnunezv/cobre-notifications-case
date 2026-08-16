package com.cobre.notifications.adapter.in.web.security;

import com.cobre.notifications.config.NotificationSecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.validation.annotation.Validated;

@Validated
public class BearerTokenRegistry {

    static final String CLIENT_AUTHORITY = "ROLE_CLIENT";
    static final String MONITORING_AUTHORITY = "ROLE_MONITORING";
    private static final String MONITORING_SUBJECT = "INTERNAL_MONITORING";

    private final List<ConfiguredToken> configuredTokens;

    public BearerTokenRegistry(NotificationSecurityProperties properties) {
        Set<String> uniqueTokens = new HashSet<>();
        List<ConfiguredToken> clientTokens = properties.clients().stream()
                .filter(BearerTokenRegistry::isComplete)
                .map(credential -> {
                    if (!uniqueTokens.add(credential.token())) {
                        throw new IllegalStateException("Each client must have a unique bearer token");
                    }
                    return new ConfiguredToken(
                            new ResolvedPrincipal(credential.clientId(), CLIENT_AUTHORITY),
                            credential.token().getBytes(StandardCharsets.UTF_8));
                })
                .toList();
        ConfiguredToken monitoringToken = monitoringToken(properties, uniqueTokens);
        this.configuredTokens = monitoringToken == null
                ? clientTokens
                : Stream.concat(clientTokens.stream(), Stream.of(monitoringToken))
                        .toList();
    }

    public Optional<@Valid ResolvedPrincipal> resolve(String candidateToken) {
        if (candidateToken == null || candidateToken.isBlank()) {
            return Optional.empty();
        }

        byte[] candidate = candidateToken.getBytes(StandardCharsets.UTF_8);
        return configuredTokens.stream()
                .filter(configured -> MessageDigest.isEqual(configured.token(), candidate))
                .map(ConfiguredToken::principal)
                .findFirst();
    }

    private ConfiguredToken monitoringToken(NotificationSecurityProperties properties, Set<String> uniqueTokens) {
        String token = properties.monitoring().token();
        if (token == null || token.isBlank()) {
            return null;
        }
        if (!uniqueTokens.add(token)) {
            throw new IllegalStateException("Each principal must have a unique bearer token");
        }
        return new ConfiguredToken(
                new ResolvedPrincipal(MONITORING_SUBJECT, MONITORING_AUTHORITY),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isComplete(NotificationSecurityProperties.ClientCredential credential) {
        return credential.clientId() != null
                && !credential.clientId().isBlank()
                && credential.token() != null
                && !credential.token().isBlank();
    }

    public record ResolvedPrincipal(
            @NotBlank String subject, @NotBlank String authority) {}

    private record ConfiguredToken(ResolvedPrincipal principal, byte[] token) {}
}
