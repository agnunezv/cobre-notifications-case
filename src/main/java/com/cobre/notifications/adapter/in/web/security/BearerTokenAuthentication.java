package com.cobre.notifications.adapter.in.web.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public final class BearerTokenAuthentication extends AbstractAuthenticationToken {

    private final Object principal;
    private String token;

    private BearerTokenAuthentication(String token) {
        super(List.of());
        this.principal = null;
        this.token = token;
        setAuthenticated(false);
    }

    private BearerTokenAuthentication(
            Object principal,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = null;
        setAuthenticated(true);
    }

    public static BearerTokenAuthentication unauthenticated(String token) {
        return new BearerTokenAuthentication(token);
    }

    public static BearerTokenAuthentication authenticated(
            Object principal,
            Collection<? extends GrantedAuthority> authorities) {
        return new BearerTokenAuthentication(principal, authorities);
    }

    @Override
    public String getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        token = null;
    }
}
