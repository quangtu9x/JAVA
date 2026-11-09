package com.td.worker.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 30/10/2026 - thông báo "Văn bản đến mới" cho ĐƠN VỊ NHẬN khi worker tạo văn bản đến (vbdi.delivery tới
 * internal_unit/contact_unit). Trước đây td-web báo ngay lúc phát hành với docid = VĂN BẢN ĐI của đơn vị gửi -
 * phía nhận thấy "Văn bản đi", bấm vào lỗi (id không tồn tại ở DB đơn vị nhận).
 *
 * <p>Giống {@code RealtimeNotifier} (td-web) nhưng worker không phụ thuộc td-application nên tự làm bằng JDBC:
 * (1) ghi 1 dòng {@code user_notifications} vào DB đơn vị nhận (transaction RIÊNG, sau khi văn bản đến đã
 * commit - bảng chưa có/lỗi chỉ log, KHÔNG làm hỏng việc nhận văn bản), (2) đẩy socket.io qua
 * {@code /socket-batch} - BẮT BUỘC HTTP/1.1 (Upgrade h2c bị engine.io đóng kết nối). Chỉ gọi ở lần nhận ĐẦU
 * (dedup) nên chạy lại task không sinh thông báo trùng.</p>
 */
@Component
@Slf4j
public class IncomingDocumentNotifier {

    /** Khớp {@code RealtimeNotification.isUnitIdentifier} (td-application). */
    private static final Pattern UNIT_IDENTIFIER = Pattern.compile("[A-Z][0-9]{2}(\\.[0-9A-Za-z]+)*");
    static final String ACTION = "vanbanden";

    private final WorkerProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IncomingDocumentNotifier(WorkerProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    }

    /**
     * @param recipient   mã định danh đơn vị nhận (hộp thư chung của văn thư) hoặc username
     * @param documentId  văn bản đến VỪA TẠO ở DB đơn vị nhận
     * @param code        số/ký hiệu
     * @param subject     trích yếu
     * @param senderName  tên đơn vị gửi
     */
    public void notifyIncomingDocument(JdbcTemplate jdbc, TransactionTemplate tx, String recipient, UUID documentId,
                                       String code, String subject, String senderName, String opinion) {
        try {
            if (!props.getRealtime().isEnabled() || recipient == null || recipient.isBlank() || documentId == null) {
                return;
            }
            String to = recipient.trim();
            boolean unit = UNIT_IDENTIFIER.matcher(to).matches();
            Map<String, String> fields = new LinkedHashMap<>();
            putIfPresent(fields, "code", code);
            putIfPresent(fields, "creator", senderName);
            putIfPresent(fields, "message", subject);
            putIfPresent(fields, "opinion", opinion);
            LocalDateTime now = LocalDateTime.now();
            UUID notificationId = persist(jdbc, tx, to, unit, documentId, fields, now);
            push(to, unit, documentId, fields, notificationId, now);
        } catch (RuntimeException ex) {
            log.warn("Không gửi được thông báo văn bản đến {} cho {}: {}", documentId, recipient, ex.getMessage());
        }
    }

    private UUID persist(JdbcTemplate jdbc, TransactionTemplate tx, String recipient, boolean unit, UUID documentId,
                         Map<String, String> fields, LocalDateTime now) {
        try {
            Boolean hasTable = jdbc.queryForObject("SELECT to_regclass('user_notifications') IS NOT NULL", Boolean.class);
            if (!Boolean.TRUE.equals(hasTable)) {
                log.debug("DB đơn vị nhận chưa có user_notifications (td-web chưa migrate V1.0.78) - chỉ đẩy socket");
                return null;
            }
            UUID id = UUID.randomUUID();
            String json = objectMapper.writeValueAsString(fields);
            tx.executeWithoutResult(status -> jdbc.update(
                "INSERT INTO user_notifications (id, recipient, recipient_type, database_type, action, document_id,"
                    + " dynamic_fields, created_on, last_modified_on) VALUES (?, ?, ?, 'vbden', ?, ?, ?::jsonb, ?, ?)",
                id, recipient, unit ? "unit" : "user", ACTION, documentId.toString(), json,
                Timestamp.valueOf(now), Timestamp.valueOf(now)));
            return id;
        } catch (Exception ex) {
            log.warn("Không lưu được hộp thư thông báo văn bản đến {}: {}", documentId, ex.getMessage());
            return null;
        }
    }

    private void push(String recipient, boolean unit, UUID documentId, Map<String, String> fields, UUID notificationId,
                      LocalDateTime now) throws RuntimeException {
        String baseUrl = props.getRealtime().getBaseUrl() == null ? "" : props.getRealtime().getBaseUrl().trim();
        if (baseUrl.isEmpty()) {
            return;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        try {
            ObjectNode data = objectMapper.createObjectNode();
            fields.forEach(data::put);
            data.put("databasetype", "vbden");
            data.put("action", ACTION);
            data.put("docid", documentId.toString());
            data.put("user", recipient);
            data.put("created_on", now.toString());
            if (notificationId != null) {
                data.put("notification_id", notificationId.toString());
            }
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode item = body.putArray("items").addObject();
            item.put("channel", (unit ? "coquan_" : "user_") + recipient);
            item.set("data", data);
            String apiKey = props.getRealtime().getApiKey() == null ? "" : props.getRealtime().getApiKey();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/socket-batch?api_key="
                    + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                .timeout(props.getRealtime().getRequestTimeout())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.warn("Đẩy socket văn bản đến {} lỗi: {}", documentId, error.getMessage());
                    } else if (response.statusCode() >= 300) {
                        log.warn("Đẩy socket văn bản đến {} HTTP {}", documentId, response.statusCode());
                    }
                });
        } catch (Exception ex) {
            log.warn("Không tạo được yêu cầu socket văn bản đến {}: {}", documentId, ex.getMessage());
        }
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value.trim());
        }
    }
}
