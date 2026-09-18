package com.td.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;

/**
 * {@link org.springframework.security.core.Authentication} cho request xác thực qua header
 * {@code Tdtoken}, song song với {@code JwtAuthenticationToken} (Keycloak) đã có sẵn.
 */
public class TdTokenAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser principal;
    private final String token;

    public TdTokenAuthenticationToken(AuthenticatedUser principal, String token,
                                       Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.getUsername();
    }
}
