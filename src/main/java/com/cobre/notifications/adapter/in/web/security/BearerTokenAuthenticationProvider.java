package com.cobre.notifications.adapter.in.web.security;

import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class BearerTokenAuthenticationProvider implements AuthenticationProvider {

    private final BearerTokenRegistry tokenRegistry;

    public BearerTokenAuthenticationProvider(BearerTokenRegistry tokenRegistry) {
        this.tokenRegistry = tokenRegistry;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String token = authentication.getCredentials() instanceof String credentials ? credentials : null;
        BearerTokenRegistry.ResolvedPrincipal resolved =
                tokenRegistry.resolve(token).orElseThrow(() -> new BadCredentialsException("Invalid bearer token"));
        Object principal = principal(resolved);
        return BearerTokenAuthentication.authenticated(
                principal, List.of(new SimpleGrantedAuthority(resolved.authority())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthentication.class.isAssignableFrom(authentication);
    }

    private Object principal(BearerTokenRegistry.ResolvedPrincipal resolved) {
        return switch (resolved.authority()) {
            case BearerTokenRegistry.CLIENT_AUTHORITY -> new ClientPrincipal(resolved.subject());
            case BearerTokenRegistry.MONITORING_AUTHORITY -> new MonitoringPrincipal(resolved.subject());
            default -> throw new IllegalStateException("Unsupported bearer-token authority");
        };
    }
}
