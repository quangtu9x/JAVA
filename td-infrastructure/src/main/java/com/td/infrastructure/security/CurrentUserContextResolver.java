package com.td.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrentUserContextResolver {

    public static final String USER_CONTEXT_CACHE = "security:user-context";
    private static final String ROLE_PREFIX = "ROLE_";

    private final CacheManager cacheManager;

    // L1 near-cache on each app node to reduce Redis roundtrips for hot users.
    private final Cache<String, CurrentUserContext> localCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterWrite(Duration.ofSeconds(300)) // slightly longer than Redis TTL to avoid cache stampede
        .build();

    public Optional<CurrentUserContext> resolveCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Jwt jwt = extractJwt(authentication);
        String cacheKey = buildCacheKey(authentication, jwt);

        CurrentUserContext local = localCache.getIfPresent(cacheKey);
        if (local != null) {
            return Optional.of(local);
        }

        org.springframework.cache.Cache redisCache = resolveRedisCache();
        if (redisCache != null) {
            try {
                CurrentUserContext cached = redisCache.get(cacheKey, CurrentUserContext.class);
                if (cached != null) {
                    localCache.put(cacheKey, cached);
                    return Optional.of(cached);
                }
            } catch (Exception ex) {
                log.debug("Read user context from Redis failed for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        CurrentUserContext resolved = buildContext(authentication, jwt);
        localCache.put(cacheKey, resolved);

        if (redisCache != null) {
            try {
                redisCache.put(cacheKey, resolved);
            } catch (Exception ex) {
                log.debug("Write user context to Redis failed for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        return Optional.of(resolved);
    }

    public Optional<UUID> resolveCurrentUserUuid() {
        return resolveCurrentUserContext()
            .map(CurrentUserContext::getUserUuid);
    }

    private org.springframework.cache.Cache resolveRedisCache() {
        return cacheManager.getCache(USER_CONTEXT_CACHE);
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        return null;
    }

    private String buildCacheKey(Authentication authentication, Jwt jwt) {
        String issuer = claimAsString(jwt, "iss");
        String subject = claimAsString(jwt, "sub");
        String activeOrgId = firstNonBlank(
            claimAsString(jwt, "active_org_id"),
            claimAsString(jwt, "unit_id"),
            claimAsString(jwt, "agency_id")
        );
        long profileVersion = parseLong(
            firstNonBlank(
                claimAsString(jwt, "profile_version"),
                claimAsString(jwt, "ctx_version"),
                claimAsString(jwt, "version")
            ),
            0L
        );

        String principal = firstNonBlank(subject, authentication.getName());
        return "ctx:"
            + safe(issuer) + ':'
            + safe(principal) + ':'
            + safe(activeOrgId) + ':'
            + profileVersion;
    }

    private CurrentUserContext buildContext(Authentication authentication, Jwt jwt) {
        String subject = firstNonBlank(claimAsString(jwt, "sub"), authentication.getName());
        String userId = firstNonBlank(
            claimAsString(jwt, "id"),
            claimAsString(jwt, "user_id"),
            subject
        );
        UUID userUuid = firstParsableUuid(
            claimAsString(jwt, "user_uuid"),
            claimAsString(jwt, "id"),
            claimAsString(jwt, "sub")
        );

        String username = firstNonBlank(
            claimAsString(jwt, "preferred_username"),
            claimAsString(jwt, "username"),
            authentication.getName()
        );

        Set<String> roles = extractRoles(authentication);
        Set<String> permissions = extractPermissions(jwt);

        return CurrentUserContext.builder()
            .subject(subject)
            .userUuid(userUuid)
            .userId(userId)
            .username(username)
            .issuer(claimAsString(jwt, "iss"))
            .activeOrgId(firstNonBlank(
                claimAsString(jwt, "active_org_id"),
                claimAsString(jwt, "unit_id"),
                claimAsString(jwt, "agency_id")
            ))
            .agencyId(claimAsString(jwt, "agency_id"))
            .roles(roles)
            .permissions(permissions)
            .profileVersion(parseLong(firstNonBlank(
                claimAsString(jwt, "profile_version"),
                claimAsString(jwt, "ctx_version"),
                claimAsString(jwt, "version")
            ), 0L))
            .resolvedAt(Instant.now())
            .build();
    }

    private Set<String> extractRoles(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return roles;
        }

        for (GrantedAuthority authority : authorities) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String value = authority.getAuthority().trim();
            if (value.startsWith(ROLE_PREFIX) && value.length() > ROLE_PREFIX.length()) {
                roles.add(value.substring(ROLE_PREFIX.length()));
            } else if (!value.isEmpty()) {
                roles.add(value);
            }
        }

        return roles;
    }

    private Set<String> extractPermissions(Jwt jwt) {
        Set<String> permissions = new LinkedHashSet<>();
        if (jwt == null) {
            return permissions;
        }

        Object rawPermissions = jwt.getClaims().get("permissions");
        permissions.addAll(toStringSet(rawPermissions));

        // Legacy token compatibility: old systems put semicolon-separated permissions in claim "roles".
        String legacyRoles = claimAsString(jwt, "roles");
        if (legacyRoles != null && !legacyRoles.isBlank()) {
            String[] split = legacyRoles.split(";");
            for (String item : split) {
                if (item != null && !item.isBlank()) {
                    permissions.add(item.trim());
                }
            }
        }

        return permissions;
    }

    private Set<String> toStringSet(Object raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null) {
            return values;
        }

        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    String value = String.valueOf(item).trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
            return values;
        }

        String asText = String.valueOf(raw);
        if (asText.contains(";")) {
            String[] split = asText.split(";");
            for (String item : split) {
                if (item != null && !item.isBlank()) {
                    values.add(item.trim());
                }
            }
            return values;
        }

        if (!asText.isBlank()) {
            values.add(asText.trim());
        }
        return values;
    }

    private String claimAsString(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null || claimName.isBlank()) {
            return null;
        }

        Object value = jwt.getClaims().get(claimName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private long parseLong(String value, long fallback) {
        try {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private UUID firstParsableUuid(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            UUID parsed = parseUuid(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private UUID parseUuid(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}