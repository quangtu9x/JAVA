package com.td.worker.infrastructure.http;

import com.td.worker.config.WorkerProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP connection pool (Apache HttpClient 5) cho handler gọi API ngoài — đúng yêu cầu
 * "HTTP connection pool" trong docs/promt.txt. PoolingHttpClientConnectionManager giới hạn
 * tổng kết nối và kết nối/route; connection được tái dùng (keep-alive) tới hết TTL.
 */
@Slf4j
@Component
public class WorkerHttpClient {

    private final CloseableHttpClient client;

    public WorkerHttpClient(WorkerProperties props) {
        WorkerProperties.Http cfg = props.getHttp();
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(cfg.getMaxConnections())
            .setMaxConnPerRoute(cfg.getMaxConnectionsPerRoute())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(cfg.getConnectTimeout().toMillis()))
                .setSocketTimeout(Timeout.ofMilliseconds(cfg.getSocketTimeout().toMillis()))
                .setTimeToLive(Timeout.ofMilliseconds(cfg.getConnectionTtl().toMillis()))
                .build())
            .build();
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(cfg.getConnectTimeout().toMillis()))
            .build();
        this.client = HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(requestConfig)
            .evictExpiredConnections()
            .evictIdleConnections(org.apache.hc.core5.util.TimeValue.ofMilliseconds(
                cfg.getConnectionTtl().toMillis()))
            .build();
        log.info("Khởi tạo HTTP client pool: maxConnections={}, maxPerRoute={}",
            cfg.getMaxConnections(), cfg.getMaxConnectionsPerRoute());
    }

    /** GET trả body text (nếu 2xx). */
    public Optional<String> get(String url) {
        return execute(new HttpGet(url));
    }

    /** POST JSON trả body text (nếu 2xx). */
    public Optional<String> postJson(String url, String jsonBody) {
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(jsonBody == null ? "{}" : jsonBody,
            ContentType.APPLICATION_JSON));
        return execute(post);
    }

    private Optional<String> execute(org.apache.hc.client5.http.classic.methods.HttpUriRequestBase request) {
        try (var response = client.execute(request)) {
            int code = response.getCode();
            String body = response.getEntity() == null
                ? "" : EntityUtils.toString(response.getEntity());
            if (code >= 200 && code < 300) {
                return Optional.of(body);
            }
            log.warn("HTTP {} -> {} (status {})", request.getMethod(), request.getRequestUri(), code);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("HTTP call thất bại {} {}: {}", request.getMethod(), request.getRequestUri(), e.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    public void close() {
        try {
            client.close();
            log.info("Đã đóng HTTP client pool");
        } catch (Exception e) {
            log.warn("Lỗi đóng HTTP client pool: {}", e.getMessage());
        }
    }
}