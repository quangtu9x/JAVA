package com.td.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotificationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Đẩy thông báo tới server socket.io — port {@code TDInit.sendSocketNotSave}. Channel {@code coquan_<mã>} cho mã
 * định danh đơn vị, {@code user_<username>} cho người dùng; payload phẳng (server phát nguyên cho FE qua
 * {@code user_notify}/{@code groups_notify}).
 *
 * <p>Hiệu năng: 1 thông báo (dù hàng trăm người nhận) = 1 request {@code POST {baseUrl}/socket-batch}. Server cũ
 * chưa có endpoint đó (404) -> nhớ 10 phút và quay về 1 request/người nhận ({@code /socket/{channel}}) như
 * Domino. Gửi bất đồng bộ ({@code sendAsync}), timeout ngắn, lỗi chỉ log.</p>
 */
@Component
@Slf4j
public class SocketIoRealtimeNotificationGateway implements RealtimeNotificationGateway {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    private static final long BATCH_RETRY_AFTER_MILLIS = 10 * 60 * 1000L;

    private final SocketIoProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    /** Thời điểm được thử lại endpoint batch (server chưa redeploy trả 404). */
    private final AtomicLong batchUnsupportedUntil = new AtomicLong(0);

    public SocketIoRealtimeNotificationGateway(SocketIoProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(200, properties.getConnectTimeoutMillis())))
            .build();
    }

    @Override
    public void publish(RealtimeNotification notification) {
        String baseUrl = baseUrl();
        if (!properties.isEnabled() || baseUrl.isEmpty() || notification == null
                || notification.getRecipients().isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() < batchUnsupportedUntil.get()) {
            publishOneByOne(baseUrl, notification);
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode items = body.putArray("items");
            String time = LocalDateTime.now().format(TIME_FORMAT);
            for (String recipient : notification.getRecipients()) {
                ObjectNode item = items.addObject();
                item.put("channel", channel(recipient));
                item.set("data", payload(notification, recipient, time));
            }
            HttpRequest request = post(baseUrl + "/socket-batch", objectMapper.writeValueAsString(body));
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.warn("Gửi socket ({}/{}, {} người nhận) lỗi: {}", notification.getDatabaseType(),
                            notification.getAction(), notification.getRecipients().size(), error.getMessage());
                    } else if (response.statusCode() == 404) {
                        log.info("Server socket chưa có /socket-batch - gửi từng người nhận trong 10 phút tới");
                        batchUnsupportedUntil.set(System.currentTimeMillis() + BATCH_RETRY_AFTER_MILLIS);
                        publishOneByOne(baseUrl, notification);
                    } else if (response.statusCode() >= 300) {
                        log.warn("Gửi socket ({}/{}) HTTP {}", notification.getDatabaseType(), notification.getAction(),
                            response.statusCode());
                    }
                });
        } catch (Exception ex) {
            log.warn("Không tạo được yêu cầu socket {}/{}: {}", notification.getDatabaseType(),
                notification.getAction(), ex.getMessage());
        }
    }

    private void publishOneByOne(String baseUrl, RealtimeNotification notification) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        for (String recipient : notification.getRecipients()) {
            try {
                String channel = channel(recipient);
                HttpRequest request = post(baseUrl + "/socket/" + URLEncoder.encode(channel, StandardCharsets.UTF_8),
                    objectMapper.writeValueAsString(payload(notification, recipient, time)));
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.warn("Gửi socket {} ({}/{}) lỗi: {}", channel, notification.getDatabaseType(),
                                notification.getAction(), error.getMessage());
                        } else if (response.statusCode() >= 300) {
                            log.warn("Gửi socket {} ({}/{}) HTTP {}", channel, notification.getDatabaseType(),
                                notification.getAction(), response.statusCode());
                        }
                    });
            } catch (Exception ex) {
                log.warn("Không tạo được yêu cầu socket cho {}: {}", recipient, ex.getMessage());
            }
        }
    }

    private HttpRequest post(String url, String json) {
        URI uri = URI.create(url + "?api_key=" + URLEncoder.encode(nullToEmpty(properties.getApiKey()), StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(Math.max(500, properties.getRequestTimeoutMillis())))
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
    }

    private ObjectNode payload(RealtimeNotification n, String recipient, String time) {
        ObjectNode body = objectMapper.createObjectNode();
        putIfPresent(body, "databasetype", n.getDatabaseType());
        putIfPresent(body, "action", n.getAction());
        putIfPresent(body, "docid", n.getDocId());
        putIfPresent(body, "code", n.getCode());
        putIfPresent(body, "creator", n.getCreator());
        putIfPresent(body, "position", n.getPosition());
        putIfPresent(body, "opinion", n.getOpinion());
        putIfPresent(body, "deadline", n.getDeadline());
        putIfPresent(body, "message", n.getMessage());
        body.put("user", recipient);
        body.put("time", time);
        UUID notificationId = n.getNotificationId(recipient);
        if (notificationId != null) {
            body.put("notification_id", notificationId.toString());
        }
        putIfPresent(body, "created_on", n.getCreatedOn());
        return body;
    }

    private static String channel(String recipient) {
        return (RealtimeNotification.isUnitIdentifier(recipient) ? "coquan_" : "user_") + recipient;
    }

    private String baseUrl() {
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static void putIfPresent(ObjectNode body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
