package com.cobre.notifications.adapter.in.web.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public final class ClientTokenAuthentication extends AbstractAuthenticationToken {

    private final ClientPrincipal principal;
    private String token;

    private ClientTokenAuthentication(String token) {
        super(List.of());
        this.principal = null;
        this.token = token;
        setAuthenticated(false);
    }

    private ClientTokenAuthentication(
            ClientPrincipal principal,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = null;
        setAuthenticated(true);
    }

    public static ClientTokenAuthentication unauthenticated(String token) {
        return new ClientTokenAuthentication(token);
    }

    public static ClientTokenAuthentication authenticated(
            ClientPrincipal principal,
            Collection<? extends GrantedAuthority> authorities) {
        return new ClientTokenAuthentication(principal, authorities);
    }

    @Override
    public String getCredentials() {
        return token;
    }

    @Override
    public ClientPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        token = null;
    }
}
