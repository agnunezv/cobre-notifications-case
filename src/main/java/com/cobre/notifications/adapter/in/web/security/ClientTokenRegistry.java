package com.cobre.notifications.adapter.in.web.security;

import com.cobre.notifications.config.NotificationSecurityProperties;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Validated
public class ClientTokenRegistry {

    private final List<ConfiguredToken> configuredTokens;

    public ClientTokenRegistry(NotificationSecurityProperties properties) {
        Set<String> uniqueTokens = new HashSet<>();
        this.configuredTokens = properties.clients().stream()
                .filter(ClientTokenRegistry::isComplete)
                .map(credential -> {
                    if (!uniqueTokens.add(credential.token())) {
                        throw new IllegalStateException("Each client must have a unique bearer token");
                    }
                    return new ConfiguredToken(
                            credential.clientId(),
                            credential.token().getBytes(StandardCharsets.UTF_8));
                })
                .toList();
    }

    public Optional<@Valid ClientPrincipal> resolve(String candidateToken) {
        if (candidateToken == null || candidateToken.isBlank()) {
            return Optional.empty();
        }

        byte[] candidate = candidateToken.getBytes(StandardCharsets.UTF_8);
        return configuredTokens.stream()
                .filter(configured -> MessageDigest.isEqual(configured.token(), candidate))
                .map(configured -> new ClientPrincipal(configured.clientId()))
                .findFirst();
    }

    private static boolean isComplete(NotificationSecurityProperties.ClientCredential credential) {
        return credential.clientId() != null
                && !credential.clientId().isBlank()
                && credential.token() != null
                && !credential.token().isBlank();
    }

    private record ConfiguredToken(String clientId, byte[] token) {
    }
}
