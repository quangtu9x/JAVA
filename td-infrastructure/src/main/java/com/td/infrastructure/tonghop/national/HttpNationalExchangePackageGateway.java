package com.td.infrastructure.tonghop.national;

import com.td.application.tonghop.national.NationalExchangePackageGateway;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Gọi endpoint nội bộ của TD Gateway {@code GET /internal/national-exchange/package} (bí mật chia sẻ
 * {@code X-Gateway-Admin-Token}; tên người tải URL-encode vì header HTTP chỉ an toàn với ASCII). Trả luồng dữ liệu để
 * controller stream thẳng về trình duyệt — không nạp cả gói (tới 100MB) vào bộ nhớ.
 */
public class HttpNationalExchangePackageGateway implements NationalExchangePackageGateway {

    private final HttpClient client;
    private final String baseUrl;
    private final String token;

    public HttpNationalExchangePackageGateway(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token == null ? "" : token;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public Optional<PackageStream> download(String exchangeId, String actorId, String actorName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/national-exchange/package?exchangeId="
                + URLEncoder.encode(exchangeId, StandardCharsets.UTF_8)))
            .timeout(Duration.ofMinutes(2))
            .header("X-Gateway-Admin-Token", token)
            .header("X-Actor-Id", encode(actorId))
            .header("X-Actor-Name", encode(actorName))
            .GET()
            .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status == 404) {
            response.body().close();
            return Optional.empty();
        }
        if (status != 200) {
            response.body().close();
            throw new IllegalStateException("TD Gateway trả HTTP " + status
                + (status == 401 || status == 503 ? " (kiểm tra NATIONAL_EXCHANGE_GATEWAY_TOKEN/GATEWAY_ADMIN_HTTP_TOKEN)" : ""));
        }
        Long length = response.headers().firstValueAsLong("Content-Length").stream().boxed().findFirst().orElse(null);
        String sha = response.headers().firstValue("X-Content-Sha256").orElse(null);
        String fileName = exchangeId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".edxml";
        return Optional.of(new PackageStream(fileName, sha, length, response.body()));
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
