package com.td.infrastructure.hosocv;

import com.td.application.common.models.Result;
import com.td.application.hosocv.LgspTokenClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G8: implementation thật của {@link LgspTokenClient} — gọi HTTP
 * outbound tới endpoint LGSP (Basic Auth), tương đương {@code DownloadFileHSCV.java#gettoken()}
 * ({@code api.monre.gov.vn/token}).
 *
 * <p>Dùng {@code java.net.http.HttpClient} chuẩn JDK (KHÔNG cần thêm dependency mới — dự án chưa
 * có {@code spring-boot-starter-web}/{@code RestTemplate} ở tầng {@code td-infrastructure}, chỉ có
 * ở {@code td-web}; theo Dependency Inversion, adapter tích hợp ngoài thuộc {@code td-infrastructure}
 * — dùng thẳng HttpClient JDK phù hợp hơn kéo thêm dependency web vào tầng infra chỉ cho 1 lời gọi
 * đơn giản).</p>
 *
 * <p><b>KHÔNG hard-code secret</b> — {@code enabled=false} theo mặc định (đúng tiền lệ
 * {@code MAIL_SENDING_ENABLED=false} BL-221): trả lỗi nghiệp vụ rõ ràng khi chưa cấu hình, thay vì
 * kết nối thất bại/NPE. Cấu hình qua env/{@code application.yml}:
 * {@code hosocv.lgsp.enabled}/{@code hosocv.lgsp.token-endpoint}/
 * {@code hosocv.lgsp.basic-auth-username}/{@code hosocv.lgsp.basic-auth-password}.</p>
 */
@Component
@Slf4j
public class HttpLgspTokenClient implements LgspTokenClient {

    @Value("${hosocv.lgsp.enabled:false}")
    private boolean enabled;

    @Value("${hosocv.lgsp.token-endpoint:}")
    private String tokenEndpoint;

    @Value("${hosocv.lgsp.basic-auth-username:}")
    private String basicAuthUsername;

    @Value("${hosocv.lgsp.basic-auth-password:}")
    private String basicAuthPassword;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public Result<String> fetchToken() {
        if (!enabled || tokenEndpoint == null || tokenEndpoint.isBlank()) {
            return Result.failure(
                "Tích hợp LGSP chưa được cấu hình (hosocv.lgsp.enabled=false hoặc thiếu hosocv.lgsp.token-endpoint)");
        }
        try {
            String credentials = (basicAuthUsername == null ? "" : basicAuthUsername) + ":"
                + (basicAuthPassword == null ? "" : basicAuthPassword);
            String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", basicAuthHeader)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Result.success(response.body());
            }
            return Result.failure("Lấy token LGSP thất bại: HTTP " + response.statusCode());
        } catch (Exception ex) {
            log.error("Lấy token LGSP thất bại", ex);
            return Result.failure("Lấy token LGSP thất bại: " + ex.getMessage());
        }
    }
}
