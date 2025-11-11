package com.td.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converter to extract roles from Keycloak JWT and map them to Spring Security authorities
 */
@Slf4j
@Component
public class KeycloakJwtConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Extract realm roles
        Collection<String> realmRoles = extractRealmRoles(jwt);
        authorities.addAll(mapRolesToAuthorities(realmRoles));
        
        // Extract client roles (resource access)
        Collection<String> clientRoles = extractClientRoles(jwt);
        authorities.addAll(mapRolesToAuthorities(clientRoles));
        
        log.debug("Extracted authorities from JWT: {}", authorities);
        return authorities;
    }

    /**
     * Extract roles from realm_access claim
     */
    private Collection<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        Object roles = realmAccess.get(ROLES_CLAIM);
        if (roles instanceof Collection<?>) {
            return ((Collection<?>) roles).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }

    /**
     * Extract roles from resource_access claim for all clients
     */
    private Collection<String> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null) {
            return Collections.emptyList();
        }

        return resourceAccess.values().stream()
                .filter(Map.class::isInstance)
                .map(client -> (Map<String, Object>) client)
                .map(client -> client.get(ROLES_CLAIM))
                .filter(Collection.class::isInstance)
                .flatMap(roles -> ((Collection<?>) roles).stream())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * Map role names to Spring Security authorities with ROLE_ prefix
     */
    private Collection<GrantedAuthority> mapRolesToAuthorities(Collection<String> roles) {
        return roles.stream()
                .map(role -> {
                    // Add ROLE_ prefix if not already present
                    String authority = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role.toUpperCase();
                    return new SimpleGrantedAuthority(authority);
                })
                .collect(Collectors.toSet());
    }
}