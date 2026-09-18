package com.td.infrastructure.onlyoffice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ký/verify token HS256 phạm vi hẹp (KHÔNG phải token đăng nhập người dùng) nhúng trong
 * {@code document.url}/{@code editorConfig.callbackUrl} của config OnlyOffice — 2 endpoint này do
 * chính Document Server (dịch vụ ngoài, xem {@code OnlyOfficeProperties#docserverUrl}) gọi thẳng,
 * KHÔNG gửi kèm {@code Authorization}/{@code Tdtoken} của người dùng thật nên không đi qua được
 * {@code SecurityConfig} thông thường (2 route này {@code permitAll}) — token ở đây là lớp xác thực
 * DUY NHẤT bảo vệ chúng, secret RIÊNG ({@code app.onlyoffice.internal-token-secret}, xem javadoc
 * {@link OnlyOfficeProperties}), Document Server hoàn toàn không biết secret này.
 *
 * <p><b>Tự dựng JWT bằng {@code javax.crypto.Mac} thay vì Nimbus (bugfix 15/09/2026, đồng bộ với
 * {@link OnlyOfficeDocumentJwtService})</b>: bản đầu dùng Nimbus {@code MACSigner}/{@code MACVerifier}
 * giống {@code TdTokenService} — NHƯNG Nimbus bắt buộc secret ≥ 256 bit, ném
 * {@code KeyLengthException} ngay nếu người vận hành cấu hình secret ngắn hơn (placeholder ở
 * {@code JAVA/.env.example} ban đầu cũng chỉ ~20 ký tự — CHÍNH secret này, không phải secret của
 * Document Server, mới thực sự do người vận hành backend tự chọn nên rủi ro cấu hình ngắn cao hơn).
 * Đổi sang tự ký/verify bằng {@code javax.crypto.Mac("HmacSHA256")} (JCE chuẩn, KHÔNG giới hạn độ
 * dài khoá) để loại bỏ HẲN lớp lỗi này cho CẢ 2 secret liên quan OnlyOffice trong hệ thống, không chỉ
 * secret của Document Server — secret ngắn vẫn hoạt động đúng (dù nên đặt dài cho an toàn), không
 * bao giờ crash vì độ dài khoá.</p>
 *
 * <p>Claim {@code purpose} ("content" hoặc "callback") ngăn 1 token cấp cho mục đích này bị dùng lại
 * cho endpoint kia (vd lộ token tải nội dung ra ngoài không tự nhiên đổi thành quyền ghi đè tệp).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OnlyOfficeAccessTokenService {

    public static final String PURPOSE_CONTENT = "content";
    public static final String PURPOSE_CALLBACK = "callback";

    private static final String INVALID_MESSAGE = "Token OnlyOffice không hợp lệ hoặc đã hết hạn";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_TENANT = "tenant_id";
    private static final String CLAIM_DOCUMENT_ID = "document_id";
    private static final String CLAIM_FILE_ID = "file_id";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EXPIRATION = "exp";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String JWT_HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final OnlyOfficeProperties onlyOfficeProperties;
    private final ObjectMapper objectMapper;

    public String issue(String purpose, String tenantId, UUID documentId, UUID fileId, String username, Duration ttl) {
        String secret = onlyOfficeProperties.getInternalTokenSecret();
        if (secret == null || secret.isBlank()) {
            log.error("app.onlyoffice.internal-token-secret chua duoc cau hinh - tu choi phat hanh token OnlyOffice");
            throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE);
        }
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", username);
            claims.put(CLAIM_PURPOSE, purpose);
            claims.put(CLAIM_TENANT, tenantId);
            claims.put(CLAIM_DOCUMENT_ID, documentId != null ? documentId.toString() : null);
            claims.put(CLAIM_FILE_ID, fileId != null ? fileId.toString() : null);
            claims.put(CLAIM_USERNAME, username);
            claims.put(CLAIM_EXPIRATION, Instant.now().plus(ttl).getEpochSecond());

            String payloadJson = objectMapper.writeValueAsString(claims);
            String signingInput = base64UrlEncode(JWT_HEADER_JSON) + "." + base64UrlEncode(payloadJson);
            String signature = URL_ENCODER.encodeToString(hmacSha256(signingInput, secret));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            log.error("Khong the ky token OnlyOffice noi bo", ex);
            throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE, ex);
        }
    }

    public OnlyOfficeAccessClaims verify(String token, String expectedPurpose) {
        String secret = onlyOfficeProperties.getInternalTokenSecret();
        if (secret == null || secret.isBlank() || token == null || token.isBlank()) {
            throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE);
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE);
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(signingInput, secret);
            byte[] actualSignature = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE);
            }

            String payloadJson = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {
            });

            Object expRaw = claims.get(CLAIM_EXPIRATION);
            long expirationEpochSeconds = expRaw instanceof Number number ? number.longValue() : -1L;
            if (expirationEpochSeconds < 0 || Instant.now().getEpochSecond() >= expirationEpochSeconds) {
                throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE);
            }

            String purpose = (String) claims.get(CLAIM_PURPOSE);
            if (purpose == null || !purpose.equals(expectedPurpose)) {
                throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE);
            }

            String documentIdRaw = (String) claims.get(CLAIM_DOCUMENT_ID);
            String fileIdRaw = (String) claims.get(CLAIM_FILE_ID);
            return new OnlyOfficeAccessClaims(
                    purpose,
                    (String) claims.get(CLAIM_TENANT),
                    documentIdRaw != null ? UUID.fromString(documentIdRaw) : null,
                    fileIdRaw != null ? UUID.fromString(fileIdRaw) : null,
                    (String) claims.get(CLAIM_USERNAME));
        } catch (OnlyOfficeAccessTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Token OnlyOffice khong parse/verify duoc: {}", ex.getMessage());
            throw new OnlyOfficeAccessTokenException(INVALID_MESSAGE, ex);
        }
    }

    private byte[] hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
