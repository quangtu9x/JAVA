package com.td.infrastructure.onlyoffice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Ký/verify JWT HS256 dùng CHUNG secret với chính OnlyOffice Document Server
 * ({@code app.onlyoffice.jwt-secret} = {@code ONLYOFFICE_JWT_SECRET}, PHẢI khớp cấu hình
 * {@code JWT_SECRET} thật trên Document Server) — khác hẳn {@link OnlyOfficeAccessTokenService}
 * (secret riêng của Backend, Document Server không hề biết).
 *
 * <p><b>Tự dựng JWT bằng {@code javax.crypto.Mac} thay vì Nimbus (bugfix 15/09/2026, báo lỗi thật
 * khi test)</b>: Nimbus's {@code MACSigner}/{@code MACVerifier} BẮT BUỘC secret HS256 ≥ 256 bit (32
 * byte) theo đúng khuyến nghị RFC 7518 — ném {@code KeyLengthException} ngay lập tức nếu ngắn hơn.
 * Secret CẤU HÌNH THẬT trên Document Server dev (`ONLYOFFICE_JWT_SECRET`) NGẮN HƠN 32 byte, và bản
 * thân Document Server (thư viện `jsonwebtoken` phía Node.js) KHÔNG hề áp đặt giới hạn này — HMAC-
 * SHA256 tự nó chấp nhận key ngắn hơn block size (tự động pad 0 bên trong), Nimbus chỉ đang chọn
 * NGHIÊM NGẶT hơn tiêu chuẩn bảo mật khuyến nghị chứ không phải giới hạn thật của thuật toán. Để
 * tương thích với secret thật (không đổi được vì phải khớp Document Server), tự build/verify JWT
 * bằng {@code javax.crypto.Mac("HmacSHA256")} (JCE chuẩn, không giới hạn độ dài khoá) thay vì Nimbus
 * — vẫn ĐÚNG chuẩn JWT compact serialization ({@code base64url(header).base64url(payload).base64url(hmac)}),
 * chỉ khác thư viện ký, tương thích 100% với mọi implementation JWT khác kể cả Document Server.</p>
 *
 * <p>Có 2 chiều dùng secret này:</p>
 * <ol>
 *   <li><b>Ký</b> {@code config.token} khi build cấu hình trả cho FE — Document Server tự verify
 *       token này bằng chính secret nó đang cấu hình trước khi cho phép mở tài liệu.</li>
 *   <li><b>Verify</b> JWT Document Server đính kèm khi gọi ngược {@code callbackUrl} (theo
 *       {@code app.onlyoffice.jwt-header}/{@code jwt-in-body}) — chứng minh request callback thực
 *       sự đến từ Document Server đã cấu hình đúng secret, không phải request giả mạo đoán được URL
 *       (URL callback dùng token riêng ở {@link OnlyOfficeAccessTokenService} nhưng lộ URL không có
 *       nghĩa là giả mạo được luôn JWT ký bằng secret này).</li>
 * </ol>
 *
 * <p>Theo tài liệu OnlyOffice: khi JWT gửi qua HEADER, payload giải mã bọc trong 1 field
 * {@code "payload"}; khi gửi qua BODY ({@code jwt-in-body=true}, field {@code token} của chính body
 * callback), payload giải mã CHÍNH LÀ nội dung body gốc (không bọc {@code "payload"}). Xử lý cả 2
 * dạng cho chắc — một số bản Document Server không hoàn toàn nhất quán giữa 2 cấu hình.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OnlyOfficeDocumentJwtService {

    private static final String PAYLOAD_WRAPPER_CLAIM = "payload";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String JWT_HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final OnlyOfficeProperties onlyOfficeProperties;
    private final ObjectMapper objectMapper;

    /**
     * Ký {@code configWithoutToken} (map {@code document}/{@code documentType}/{@code editorConfig})
     * bằng {@code ONLYOFFICE_JWT_SECRET}. Trả về {@code null} nếu JWT đang tắt trên Document Server
     * (config trả về không có field {@code token} — FE component tự báo lỗi thiếu cấu hình nếu
     * component gọi cho server có JWT bật, đúng hành vi mong muốn) hoặc secret chưa cấu hình.
     */
    public String signConfig(Map<String, Object> configWithoutToken) {
        if (!onlyOfficeProperties.isJwtEnabled()) {
            return null;
        }
        String secret = onlyOfficeProperties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            log.error("app.onlyoffice.jwt-secret chua duoc cau hinh nhung jwt-enabled=true - khong the ky config OnlyOffice");
            throw new OnlyOfficeDocumentJwtException("Chưa cấu hình ONLYOFFICE_JWT_SECRET");
        }
        try {
            String payloadJson = objectMapper.writeValueAsString(configWithoutToken);
            String signingInput = base64UrlEncode(JWT_HEADER_JSON) + "." + base64UrlEncode(payloadJson);
            String signature = URL_ENCODER.encodeToString(hmacSha256(signingInput, secret));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            log.error("Khong the ky config OnlyOffice", ex);
            throw new OnlyOfficeDocumentJwtException("Không thể ký cấu hình OnlyOffice", ex);
        }
    }

    /**
     * Xác thực + trích payload thật của request callback — xem javadoc lớp cho 2 dạng bọc token.
     * Nếu JWT đang tắt trên Document Server (môi trường test/dev chưa bật), trả nguyên
     * {@code rawBody} không verify — CHỈ chấp nhận khi {@code jwt-enabled=false} tường minh.
     *
     * @param headerValue giá trị header {@code app.onlyoffice.jwt-header} (thường {@code Authorization: Bearer <jwt>}), có thể null
     * @param rawBody     body JSON gốc của request callback
     * @throws OnlyOfficeDocumentJwtException nếu JWT bật nhưng không tìm được/không verify được token hợp lệ
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveCallbackPayload(String headerValue, Map<String, Object> rawBody) {
        if (!onlyOfficeProperties.isJwtEnabled()) {
            return rawBody;
        }
        String secret = onlyOfficeProperties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new OnlyOfficeDocumentJwtException("Chưa cấu hình ONLYOFFICE_JWT_SECRET");
        }

        String token = null;
        boolean fromBody = false;
        if (rawBody != null && rawBody.get("token") instanceof String bodyToken && !bodyToken.isBlank()) {
            token = bodyToken;
            fromBody = true;
        } else if (headerValue != null && !headerValue.isBlank()) {
            token = headerValue.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? headerValue.substring(7).trim()
                    : headerValue.trim();
        }
        if (token == null || token.isBlank()) {
            throw new OnlyOfficeDocumentJwtException("Thiếu JWT xác thực callback từ OnlyOffice Document Server");
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new OnlyOfficeDocumentJwtException("JWT callback OnlyOffice không đúng định dạng");
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(signingInput, secret);
            byte[] actualSignature = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new OnlyOfficeDocumentJwtException("Chữ ký JWT callback OnlyOffice không hợp lệ");
            }
            String payloadJson = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {
            });
            if (!fromBody && claims.get(PAYLOAD_WRAPPER_CLAIM) instanceof Map<?, ?> wrapped) {
                return (Map<String, Object>) wrapped;
            }
            return claims;
        } catch (OnlyOfficeDocumentJwtException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Khong verify duoc JWT callback OnlyOffice: {}", ex.getMessage());
            throw new OnlyOfficeDocumentJwtException("Không xác thực được JWT callback OnlyOffice", ex);
        }
    }

    private byte[] hmacSha256(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
