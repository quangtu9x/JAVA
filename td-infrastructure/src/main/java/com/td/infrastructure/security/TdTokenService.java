package com.td.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verify JWT cũ ({@code Tdtoken}) ký HMAC bằng secret Domino ({@code TDKeySettings.JWT_Authen_Secret_KEY}
 * cũ, nay chuyển sang {@code app.security.tdtoken.secret}). Dùng Nimbus JOSE+JWT — thư viện đã có sẵn
 * transitive qua {@code spring-security-oauth2-jose} nên không cần thêm dependency mới.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TdTokenService {

    static final String INVALID_TOKEN_MESSAGE = "Sai token";
    // Bổ sung 20/08/2026 (bugfix, BL-118 tiếp): public để caller (QLVBCompatAuthController) ghi đè
    // đúng claim này qua "extraClaims" bằng danh sách role ĐÃ HỢP NHẤT với quyền từ Nhóm vai trò
    // (RoleGroup) — xem javadoc issue(AuthenticatedUser, Duration, Map).
    public static final String ROLES_CLAIM = "roles";
    private static final String USERNAME_CLAIM = "username";
    private static final String NAME_CLAIM = "name";
    /** Thời hạn Tdtoken sẽ phát hành tại {@code POST /qlvb/authen} — ngắn hơn session Keycloak. */
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(8);

    private final TdTokenProperties tdTokenProperties;

    /**
     * Verify chữ ký + hạn dùng của token, map claims sang {@link AuthenticatedUser}.
     *
     * @throws TdTokenAuthenticationException nếu token không hợp lệ, hết hạn, hoặc secret chưa cấu hình
     */
    public AuthenticatedUser verify(String token) {
        if (!tdTokenProperties.isEnabled()) {
            throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE);
        }

        String secret = tdTokenProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            log.error("app.security.tdtoken.secret chua duoc cau hinh - tu choi moi request Tdtoken");
            throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE);
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(secret.getBytes(StandardCharsets.UTF_8)))) {
                throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            assertNotExpired(claims);

            String username = firstNonBlank(claims.getStringClaim(USERNAME_CLAIM), claims.getSubject());
            if (username == null || username.isBlank()) {
                throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE);
            }

            return AuthenticatedUser.builder()
                .username(username)
                .name(claims.getStringClaim(NAME_CLAIM))
                .roles(parseRoles(claims))
                .claims(claims.getClaims())
                .build();
        } catch (TdTokenAuthenticationException ex) {
            throw ex;
        } catch (ParseException | JOSEException ex) {
            log.debug("Tdtoken khong parse/verify duoc: {}", ex.getMessage());
            throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE, ex);
        }
    }

    /**
     * Phát hành Tdtoken mới (thời hạn mặc định 8 giờ) cho người dùng đã xác thực — dùng cho
     * {@code POST /qlvb/authen} (tương thích FE cũ: FE gửi {@code Authorization: Bearer} Keycloak và
     * nhận lại chính token nội bộ này để gửi trong header {@code Tdtoken}).
     *
     * @param user thông tin người dùng đã map từ Keycloak
     * @return JWT HS256 ký bằng {@code app.security.tdtoken.secret}
     */
    public String issue(AuthenticatedUser user) {
        return issue(user, DEFAULT_TOKEN_TTL, Map.of());
    }

    /**
     * Phát hành Tdtoken với thời hạn tùy chỉnh.
     *
     * @throws TdTokenAuthenticationException nếu tính năng tắt hoặc secret chưa cấu hình
     */
    public String issue(AuthenticatedUser user, Duration ttl) {
        return issue(user, ttl, Map.of());
    }

    /** Phát hành Tdtoken (thời hạn mặc định) kèm claim tổ chức — xem {@link #issue(AuthenticatedUser, Duration, Map)}. */
    public String issue(AuthenticatedUser user, Map<String, Object> extraClaims) {
        return issue(user, DEFAULT_TOKEN_TTL, extraClaims);
    }

    /**
     * Phát hành Tdtoken kèm claim tổ chức (BL-118, 20/08/2026) — {@code identifier}/{@code
     * identifiers}/{@code parentids}/{@code level}/{@code unit}/{@code unit_id}/{@code department}/
     * {@code department_id}/{@code agency}/{@code agency_id}/{@code agency_level}/{@code caplanhdao}/
     * {@code position}, cùng tên field mà {@code TDTokenObject} (BE_THANHHOA Domino thật) kỳ vọng.
     *
     * <p><b>Bối cảnh</b>: trước đây Tdtoken Java phát hành chỉ có sub/username/name/roles — MỌI
     * endpoint Domino còn lại chưa migrate mà đọc field tổ chức từ token (qua {@code
     * TDDatabaseConnection}/{@code TDTokenObject}) đều âm thầm nhận giá trị {@code null} cho các
     * field này (ca đầu phát hiện: {@code qlvb/danhmuc/DanhSachNguoiDung} lọc theo {@code dbpath}).
     * Bổ sung claim tổ chức ở đây sửa tận gốc cầu nối, không cần vá từng endpoint Domino một khi
     * phát hiện thêm ca tương tự. {@code dbpath}/{@code ipserver}/{@code server_id}/{@code mailin}/
     * {@code ip_server_*} CỐ TÌNH không có — đây là định danh hạ tầng Domino (server/database
     * NSF) không có khái niệm tương đương trong hệ thống Postgres mới, gán giá trị giả sẽ rủi ro hơn
     * để trống (Domino tự xử lý null-safe cho các field này, không crash).</p>
     *
     * @param extraClaims claim bổ sung (vd. build từ {@code UserOrganizationProfileDto}) — key/value
     *                    {@code null} bị bỏ qua, không claim rỗng
     */
    public String issue(AuthenticatedUser user, Duration ttl, Map<String, Object> extraClaims) {
        if (!tdTokenProperties.isEnabled()) {
            throw new TdTokenAuthenticationException("Tdtoken khong duoc bat");
        }

        String secret = tdTokenProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            log.error("app.security.tdtoken.secret chua duoc cau hinh - khong the phat hanh Tdtoken moi");
            throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE);
        }

        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .claim(USERNAME_CLAIM, user.getUsername())
                .claim(NAME_CLAIM, user.getName())
                .claim(ROLES_CLAIM, String.join(";", user.getRoles()))
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(ttl)));

            if (extraClaims != null) {
                for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
                    if (entry.getValue() != null) {
                        builder.claim(entry.getKey(), entry.getValue());
                    }
                }
            }

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
            signedJWT.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            log.error("Khong the ky Tdtoken moi", ex);
            throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE, ex);
        }
    }

    private void assertNotExpired(JWTClaimsSet claims) {
        Date expirationTime = claims.getExpirationTime();
        long skewMillis = tdTokenProperties.getClockSkewSeconds() * 1000;
        Date threshold = new Date(System.currentTimeMillis() - skewMillis);

        // Token khong co claim "exp" duoc coi la khong hop le: chap nhan token khong bao gio het han
        // la mot rui ro bao mat, kha nang cao la token da bi sua/gia mao.
        if (expirationTime == null || expirationTime.before(threshold)) {
            throw new TdTokenAuthenticationException(INVALID_TOKEN_MESSAGE);
        }
    }

    private Set<String> parseRoles(JWTClaimsSet claims) throws ParseException {
        Set<String> roles = new LinkedHashSet<>();
        String raw = claims.getStringClaim(ROLES_CLAIM);
        if (raw == null || raw.isBlank()) {
            return roles;
        }
        for (String role : raw.split(";")) {
            if (!role.isBlank()) {
                roles.add(role.trim());
            }
        }
        return roles;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
