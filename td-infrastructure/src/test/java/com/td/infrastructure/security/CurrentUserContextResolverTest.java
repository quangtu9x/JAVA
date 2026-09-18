package com.td.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bugfix 03/09/2026 (báo lỗi người dùng: đăng nhập lại tenant/tài khoản — kể cả tab ẩn danh, đã
 * stop/start lại BE nhưng KHÔNG hết — vẫn thấy dữ liệu của đơn vị khác ở 1 số API, 1 số khác lại
 * đúng; tự khỏi "1 lúc sau"). Root cause: {@link CurrentUserContextResolver} cache (L1 Caffeine
 * trong JVM + L2 Redis {@code security:user-context}, sống lâu hơn 1 lần restart BE) — nếu giá trị
 * đọc từ cache thuộc về username KHÁC username của request hiện tại (dữ liệu hỏng/lệch, bất kể lý
 * do gốc), phải LOẠI BỎ entry đó và tính lại tươi từ token ngay trong request đó — KHÔNG được phục
 * vụ nhầm dù chỉ 1 lần. Fix áp dụng CHUNG cho cả 2 nhánh xác thực (Tdtoken lẫn Keycloak JWT) tại
 * đúng 1 chỗ (cổng vào duy nhất mọi use case dùng để lấy username hiện tại), không phải vá riêng
 * từng use case gọi tới.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserContextResolverTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private org.springframework.cache.Cache redisCache;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CurrentUserContextResolver newResolver() {
        return new CurrentUserContextResolver(cacheManager);
    }

    private void authenticateAsTdToken(String username) {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username(username)
            .roles(Set.of("USER"))
            .claims(Map.of("unit_id", "unit-" + username))
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateAsJwt(String username) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", username)
            .claim("preferred_username", username)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication =
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // Bugfix 03/09/2026: access token Keycloak THẬT của hệ thống này KHÔNG có claim "sub" — chỉ có
    // "preferred_username" (đã xác nhận qua token thật người dùng gửi, decode được: exp/iat/jti/iss/
    // typ/azp/sid/realm_access/scope/preferred_username/email — không có "sub"). Đây CHÍNH XÁC là
    // token mà getName()/JwtAuthenticationToken trả null cho mọi user, gây bug key/mismatch-check
    // dùng chung 1 entry cho MỌI người.
    private JwtAuthenticationToken authenticateAsJwtWithoutSubClaim(String username) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("iss", "https://app.tandan.com.vn/keycloak/realms/td-webapi-realm")
            .claim("preferred_username", username)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication =
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test
    void resolveCurrentUserContext_whenNotAuthenticated_shouldReturnEmpty() {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("x", "y") {
                @Override
                public boolean isAuthenticated() {
                    return false;
                }
            });

        assertThat(newResolver().resolveCurrentUserContext()).isEmpty();
    }

    @Test
    void resolveCurrentUserContext_tdToken_freshBuild_shouldHaveCorrectUsername() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(null);
        authenticateAsTdToken("skhcn");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("skhcn");
    }

    // Mo phong dung trieu chung bao loi: entry Redis (song lau hon 1 lan restart BE) dang giu du
    // lieu cua 1 username KHAC voi request hien tai (du hong du lieu goc la gi) - phai bi loai bo va
    // tinh lai dung, KHONG duoc tra ve nguyen si.
    @Test
    void resolveCurrentUserContext_tdToken_whenRedisHasMismatchedUsername_shouldEvictAndRebuildFresh() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        CurrentUserContext staleWrongUser = CurrentUserContext.builder()
            .username("snnmt")
            .activeOrgId("unit-snnmt")
            .build();
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(staleWrongUser);
        authenticateAsTdToken("skhcn");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("skhcn");
        verify(redisCache).evict(any());
    }

    @Test
    void resolveCurrentUserContext_tdToken_whenRedisHasMatchingUsername_shouldReturnCachedAsIs() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        CurrentUserContext cached = CurrentUserContext.builder()
            .username("skhcn")
            .activeOrgId("unit-skhcn")
            .build();
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(cached);
        authenticateAsTdToken("skhcn");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).contains(cached);
        verify(redisCache, never()).evict(any());
    }

    @Test
    void resolveCurrentUserContext_jwt_freshBuild_shouldHaveCorrectUsername() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(null);
        authenticateAsJwt("skhcn");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("skhcn");
    }

    // Cung 1 co che nhung o nhanh Keycloak JWT (cache key khac Tdtoken - xay tu subject/issuer/
    // activeOrgId/profileVersion, khong phai username truc tiep - nen mismatch cang de xay ra hon).
    @Test
    void resolveCurrentUserContext_jwt_whenRedisHasMismatchedUsername_shouldEvictAndRebuildFresh() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        CurrentUserContext staleWrongUser = CurrentUserContext.builder()
            .username("snnmt")
            .activeOrgId("unit-snnmt")
            .build();
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(staleWrongUser);
        authenticateAsJwt("skhcn");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("skhcn");
        verify(redisCache).evict(any());
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng, xác nhận qua token thật do người dùng gửi: đăng nhập
    // snnmt nhưng server resolve ra skhcn — token KHÔNG có claim "sub", chỉ "preferred_username").
    // Đây là test tái hiện CHÍNH XÁC bug thật, không phải kịch bản giả định.
    @Test
    void resolveCurrentUserContext_jwtWithoutSubClaim_freshBuild_shouldStillHaveCorrectUsername() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(null);
        authenticateAsJwtWithoutSubClaim("snnmt");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("snnmt");
    }

    @Test
    void resolveCurrentUserContext_jwtWithoutSubClaim_whenRedisHasMismatchedUsername_shouldEvictAndRebuildFresh() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        CurrentUserContext staleWrongUser = CurrentUserContext.builder()
            .username("skhcn")
            .activeOrgId("af14bab4-db9b-4d2c-9bf2-f8b62d272f24")
            .build();
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(staleWrongUser);
        authenticateAsJwtWithoutSubClaim("snnmt");

        var result = newResolver().resolveCurrentUserContext();

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("snnmt");
        verify(redisCache).evict(any());
    }

    // Xac nhan goc re bug that su da duoc chan: 2 user KHAC nhau, CUNG khong co claim "sub" (dung
    // token that), phai tra ve 2 cache key KHAC nhau - truoc fix ca 2 deu ra CUNG 1 key "ctx:<iss>:_:_:0".
    @Test
    void buildCacheKey_shouldDifferPerUser_evenWithoutSubClaim() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(null);
        var keyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);

        authenticateAsJwtWithoutSubClaim("snnmt");
        newResolver().resolveCurrentUserContext();

        SecurityContextHolder.clearContext();
        authenticateAsJwtWithoutSubClaim("skhcn");
        newResolver().resolveCurrentUserContext();

        verify(redisCache, org.mockito.Mockito.times(2)).get(keyCaptor.capture(), eq(CurrentUserContext.class));
        var capturedKeys = keyCaptor.getAllValues();
        assertThat(capturedKeys.get(0)).isNotEqualTo(capturedKeys.get(1));
    }

    @Test
    void resolveCurrentUserUuid_tdToken_shouldBeDeterministicAndStable() {
        when(cacheManager.getCache(CurrentUserContextResolver.USER_CONTEXT_CACHE)).thenReturn(redisCache);
        when(redisCache.get(any(), eq(CurrentUserContext.class))).thenReturn(null);
        authenticateAsTdToken("skhcn");
        CurrentUserContextResolver resolver = newResolver();

        var first = resolver.resolveCurrentUserUuid();
        var second = resolver.resolveCurrentUserUuid();

        assertThat(first).isPresent().isEqualTo(second);
    }
}
