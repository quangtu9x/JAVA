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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
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

        if (authentication instanceof TdTokenAuthenticationToken tdTokenAuthentication) {
            return Optional.of(resolveTdTokenContext(tdTokenAuthentication));
        }

        Jwt jwt = extractJwt(authentication);
        String cacheKey = buildCacheKey(authentication, jwt);
        // Bugfix 03/09/2026 (báo lỗi người dùng, xác nhận qua token thật + log: đăng nhập snnmt
        // nhưng server resolve ra skhcn, "tự đúng lại" sau ~5-6 phút — đúng bằng TTL cache dưới đây):
        // KHÔNG dùng authentication.getName() làm requestUsername — với JwtAuthenticationToken,
        // getName() đọc claim "sub", mà access token Keycloak THẬT của hệ thống này KHÔNG có claim
        // "sub" (chỉ có "preferred_username") — getName() luôn trả null cho MỌI user, khiến
        // isSameUser(null, cached) luôn coi là "khớp" (không bao giờ phát hiện lệch), và buildCacheKey
        // (cùng lỗi, xem bên dưới) sinh ra CÙNG 1 KEY cho MỌI user. Dùng resolveJwtUsername() (ưu
        // tiên "preferred_username", đã xác nhận LUÔN có trên token thật).
        String requestUsername = resolveJwtUsername(authentication, jwt);

        CurrentUserContext local = localCache.getIfPresent(cacheKey);
        if (local != null) {
            if (isSameUser(requestUsername, local)) {
                return Optional.of(local);
            }
            evictMismatch("local", cacheKey, requestUsername, local);
        }

        org.springframework.cache.Cache redisCache = resolveRedisCache();
        if (redisCache != null) {
            try {
                CurrentUserContext cached = redisCache.get(cacheKey, CurrentUserContext.class);
                if (cached != null) {
                    if (isSameUser(requestUsername, cached)) {
                        localCache.put(cacheKey, cached);
                        return Optional.of(cached);
                    }
                    evictMismatch("redis", cacheKey, requestUsername, cached);
                    redisCache.evict(cacheKey);
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

    /**
     * Build {@link CurrentUserContext} cho request xác thực qua {@code Tdtoken} (BL-004), không
     * cần {@link Jwt} Keycloak. userUuid được suy ra tất định từ username (không đổi giữa các
     * lần gọi) để {@code AuditorAware} (createdBy/updatedBy) vẫn hoạt động cho user Tdtoken.
     */
    private CurrentUserContext resolveTdTokenContext(TdTokenAuthenticationToken authentication) {
        String requestUsername = authentication.getName();
        String cacheKey = "ctx:tdtoken:" + safe(requestUsername);

        // Bugfix 03/09/2026 (báo lỗi người dùng: đăng nhập lại 1 tenant/tài khoản — kể cả tab ẩn
        // danh, đã stop/start lại BE nhưng KHÔNG hết — vẫn thấy dữ liệu của đơn vị khác ở 1 số API,
        // 1 số khác lại đúng; tự khỏi "1 lúc sau"): restart BE không dọn được cache này vì
        // `security:user-context` sống ở REDIS (external, sống lâu hơn JVM) — khớp CHÍNH XÁC lý do
        // restart BE không giải quyết được. Đối chiếu: các API bị lỗi (GetSoVanBanDenUseCase,
        // GetCapBanHanhOptionsUseCase) ĐỀU đi qua CurrentUserProvider.currentUsername() (tức HÀM
        // NÀY) để suy ra identifier lọc dữ liệu; API không lỗi (danh sách sổ văn bản, dự thảo toàn
        // bộ) KHÔNG phụ thuộc identifier theo user hiện tại — khớp đúng "lỗi theo API". Không chỉ
        // LOG khi cache trả về username KHÁC username của request hiện tại — LOẠI BỎ NGAY entry sai
        // đó và tính lại tươi từ token, tự phục hồi ngay trong CÙNG request thay vì phải đợi TTL hết
        // hạn (đây là dữ liệu định danh/phân quyền — không chấp nhận phục vụ nhầm dù chỉ 1 request).
        CurrentUserContext local = localCache.getIfPresent(cacheKey);
        if (local != null) {
            if (isSameUser(requestUsername, local)) {
                return local;
            }
            evictMismatch("local", cacheKey, requestUsername, local);
        }

        org.springframework.cache.Cache redisCache = resolveRedisCache();
        if (redisCache != null) {
            try {
                CurrentUserContext cached = redisCache.get(cacheKey, CurrentUserContext.class);
                if (cached != null) {
                    if (isSameUser(requestUsername, cached)) {
                        localCache.put(cacheKey, cached);
                        return cached;
                    }
                    evictMismatch("redis", cacheKey, requestUsername, cached);
                    redisCache.evict(cacheKey);
                }
            } catch (Exception ex) {
                log.debug("Read tdtoken user context from Redis failed for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        CurrentUserContext resolved = buildTdTokenContext(authentication);
        localCache.put(cacheKey, resolved);

        if (redisCache != null) {
            try {
                redisCache.put(cacheKey, resolved);
            } catch (Exception ex) {
                log.debug("Write tdtoken user context to Redis failed for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        return resolved;
    }

    // Xem chu thich o resolveTdTokenContext/resolveCurrentUserContext - dung chung cho ca 2 nhanh
    // Tdtoken va Keycloak JWT.
    private boolean isSameUser(String requestUsername, CurrentUserContext cached) {
        return requestUsername == null || cached == null
            || requestUsername.equalsIgnoreCase(cached.getUsername());
    }

    // Loai bo NGAY entry cache lech user khoi L1 (Redis duoc goi evict rieng o noi goi, vi co the
    // can try/catch khac nhau giua 2 nhanh) - khong de request SAU tiep tuc doc trung entry sai nay.
    private void evictMismatch(String source, String cacheKey, String requestUsername, CurrentUserContext cached) {
        log.warn(
            "MISMATCH cache user-context ({}): request username='{}' (key={}) nhung gia tri cache "
                + "tra ve username='{}', activeOrgId='{}' - da LOAI BO entry nay va tinh lai tuoi tu "
                + "token (khong phuc vu nham du lieu nguoi dung khac).",
            source, requestUsername, cacheKey, cached.getUsername(), cached.getActiveOrgId());
        localCache.invalidate(cacheKey);
    }

    private CurrentUserContext buildTdTokenContext(TdTokenAuthenticationToken authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        Map<String, Object> claims = user.getClaims();

        String activeOrgId = firstNonBlank(
            claimAsString(claims, "unit_id"),
            claimAsString(claims, "agency_id"),
            claimAsString(claims, "identifier")
        );

        return CurrentUserContext.builder()
            .subject(user.getUsername())
            .userUuid(deterministicUuid(user.getUsername()))
            .userId(user.getUsername())
            .username(user.getUsername())
            .issuer("tdtoken")
            .activeOrgId(activeOrgId)
            .agencyId(claimAsString(claims, "agency_id"))
            .roles(user.getRoles())
            .permissions(user.getRoles())
            .profileVersion(0L)
            .resolvedAt(Instant.now())
            .build();
    }

    private UUID deterministicUuid(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return UUID.nameUUIDFromBytes(("tdtoken:" + username.trim().toLowerCase(Locale.ROOT))
            .getBytes(StandardCharsets.UTF_8));
    }

    private String claimAsString(Map<String, Object> claims, String claimName) {
        if (claims == null || claimName == null) {
            return null;
        }
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    // Bugfix 03/09/2026 (xem chú thích ở resolveCurrentUserContext): access token Keycloak THẬT của
    // hệ thống này KHÔNG có claim "sub" — chỉ dùng "sub" để định danh user (như code cũ) khiến MỌI
    // user rơi vào CÙNG 1 nhánh fallback rỗng. "preferred_username" LUÔN có (đã xác nhận qua token
    // thật) và cũng CHÍNH LÀ field buildContext() đã dùng để set CurrentUserContext.username — dùng
    // thống nhất 1 nguồn duy nhất cho cả cache key lẫn so khớp mismatch.
    private String resolveJwtUsername(Authentication authentication, Jwt jwt) {
        return firstNonBlank(
            claimAsString(jwt, "preferred_username"),
            claimAsString(jwt, "username"),
            claimAsString(jwt, "sub"),
            authentication == null ? null : authentication.getName()
        );
    }

    private String buildCacheKey(Authentication authentication, Jwt jwt) {
        String issuer = claimAsString(jwt, "iss");
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

        String principal = resolveJwtUsername(authentication, jwt);
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