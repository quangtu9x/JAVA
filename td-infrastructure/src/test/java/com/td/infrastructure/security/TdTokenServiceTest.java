package com.td.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test verify JWT cũ (BL-004). Token test được tự ký trong test bằng cùng cơ chế HMAC mà
 * Domino/TDAuthentication dùng (auth0 java-jwt HMAC256) — ở đây dùng Nimbus MACSigner cho tương đương.
 */
class TdTokenServiceTest {

    private static final String SECRET = generateHmacSecret();

    private TdTokenService tdTokenService;
    private TdTokenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TdTokenProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        properties.setClockSkewSeconds(60);
        tdTokenService = new TdTokenService(properties);
    }

    @Test
    void verify_shouldReturnAuthenticatedUser_whenTokenValid() {
        String token = buildToken(SECRET, "nguyenvana", "USER;ADMIN", Instant.now().plusSeconds(3600));

        AuthenticatedUser user = tdTokenService.verify(token);

        assertThat(user.getUsername()).isEqualTo("nguyenvana");
        assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(user.getClaims()).containsEntry("username", "nguyenvana");
    }

    @Test
    void verify_shouldThrow_whenTokenExpired() {
        String token = buildToken(SECRET, "nguyenvana", "USER", Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> tdTokenService.verify(token))
            .isInstanceOf(TdTokenAuthenticationException.class)
            .hasMessage("Sai token");
    }

    @Test
    void verify_shouldThrow_whenSignatureWrong() {
        String tamperedSecret = generateHmacSecret();
        String token = buildToken(tamperedSecret, "nguyenvana", "USER", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> tdTokenService.verify(token))
            .isInstanceOf(TdTokenAuthenticationException.class)
            .hasMessage("Sai token");
    }

    @Test
    void verify_shouldThrow_whenTokenMalformed() {
        assertThatThrownBy(() -> tdTokenService.verify("not-a-jwt"))
            .isInstanceOf(TdTokenAuthenticationException.class)
            .hasMessage("Sai token");
    }

    @Test
    void verify_shouldThrow_whenNoExpirationClaim() {
        String token = buildToken(SECRET, "nguyenvana", "USER", null);

        assertThatThrownBy(() -> tdTokenService.verify(token))
            .isInstanceOf(TdTokenAuthenticationException.class)
            .hasMessage("Sai token");
    }

    @Test
    void verify_shouldThrow_whenSecretNotConfigured() {
        properties.setSecret("");

        String token = buildToken(SECRET, "nguyenvana", "USER", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> tdTokenService.verify(token))
            .isInstanceOf(TdTokenAuthenticationException.class)
            .hasMessage("Sai token");
    }

    @Test
    void verify_shouldThrow_whenDisabled() {
        properties.setEnabled(false);

        String token = buildToken(SECRET, "nguyenvana", "USER", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> tdTokenService.verify(token))
            .isInstanceOf(TdTokenAuthenticationException.class)
            .hasMessage("Sai token");
    }

    private static String buildToken(String secret, String username, String roles, Instant expiresAt) {
        try {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(username)
                .claim("username", username)
                .claim("name", "Nguyen Van A")
                .claim("roles", roles);
            if (expiresAt != null) {
                claimsBuilder.expirationTime(Date.from(expiresAt));
            }

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsBuilder.build());
            signedJWT.sign(new MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Khong the tao token test", ex);
        }
    }

    // ---- Bo sung: phat hanh Tdtoken (POST /qlvb/authen) ----

    @Test
    void issue_thenVerify_shouldRoundTrip() {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("admin")
            .name("Quan tri he thong")
            .roles(Set.of("CVQuanTri", "ADMIN"))
            .build();

        String token = tdTokenService.issue(user);

        assertThat(token.split("\\.")).hasSize(3);
        AuthenticatedUser verified = tdTokenService.verify(token);
        assertThat(verified.getUsername()).isEqualTo("admin");
        assertThat(verified.getRoles()).containsExactlyInAnyOrder("CVQuanTri", "ADMIN");
    }

    @Test
    void issue_shouldThrow_whenDisabled() {
        properties.setEnabled(false);
        AuthenticatedUser user = AuthenticatedUser.builder().username("admin").name("Administrator").build();

        assertThatThrownBy(() -> tdTokenService.issue(user))
            .isInstanceOf(TdTokenAuthenticationException.class);
    }

    // BL-118 (tiếp, 20/08/2026): claim tổ chức (identifier/level/unit/department/agency/...) —
    // trước đây Tdtoken chỉ có sub/username/name/roles, khiến endpoint Domino còn lại chưa migrate
    // (đọc field tổ chức qua TDTokenObject) luôn nhận null. Test khoá lại: claim bổ sung phải có mặt
    // trong token đã ký, đọc lại được nguyên vẹn qua verify() (AuthenticatedUser.claims giữ mọi claim
    // gốc, không chỉ 4 field đã biết).
    @Test
    void issue_withExtraClaims_shouldEmbedThemInTokenAndBeReadableAfterVerify() {
        AuthenticatedUser user = AuthenticatedUser.builder().username("nhanvien01").name("Nguyen Van A").build();
        Map<String, Object> extraClaims = new java.util.LinkedHashMap<>();
        extraClaims.put("identifier", "G10");
        extraClaims.put("level", "3");
        extraClaims.put("agency", "So Noi Vu");
        extraClaims.put("dbpath_should_not_exist", null); // gia lap claim null - phai bi bo qua

        String token = tdTokenService.issue(user, extraClaims);

        AuthenticatedUser verified = tdTokenService.verify(token);
        assertThat(verified.getClaims()).containsEntry("identifier", "G10");
        assertThat(verified.getClaims()).containsEntry("level", "3");
        assertThat(verified.getClaims()).containsEntry("agency", "So Noi Vu");
        assertThat(verified.getClaims()).doesNotContainKey("dbpath_should_not_exist");
    }

    @Test
    void issue_withoutExtraClaims_shouldNotThrow_backwardCompatible() {
        AuthenticatedUser user = AuthenticatedUser.builder().username("admin").name("Administrator").build();

        String token = tdTokenService.issue(user);

        assertThat(token.split("\\.")).hasSize(3);
    }

    private static String generateHmacSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
