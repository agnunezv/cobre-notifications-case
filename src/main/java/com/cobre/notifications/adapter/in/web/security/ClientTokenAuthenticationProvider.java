package com.cobre.notifications.adapter.in.web.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ClientTokenAuthenticationProvider implements AuthenticationProvider {

    private static final List<SimpleGrantedAuthority> CLIENT_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));

    private final ClientTokenRegistry tokenRegistry;

    public ClientTokenAuthenticationProvider(ClientTokenRegistry tokenRegistry) {
        this.tokenRegistry = tokenRegistry;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String token = authentication.getCredentials() instanceof String credentials ? credentials : null;
        ClientPrincipal principal = tokenRegistry.resolve(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid bearer token"));
        return ClientTokenAuthentication.authenticated(principal, CLIENT_AUTHORITIES);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ClientTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
