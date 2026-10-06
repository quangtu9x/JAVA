package com.td.application.documents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.sharedcore.UserDisplayInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 05/10/2026 (yêu cầu người dùng: "Nhật ký đã xem" đồng nhất ở văn bản đến / văn bản đi / dự thảo, có
 * ngày giờ xem LẦN ĐẦU và LẦN CUỐI): ghi field {@value #FIELD} = mảng JSON
 * {@code {username, name, unit, created, lastSeen}} — CÙNG định dạng
 * {@code AddDuThaoSeenDocumentUseCase}/{@code AddVbdiSeenDocumentUseCase} đã dùng, để FE
 * ({@code LogReadCollapse.jsx}) đọc chung 1 cách. {@code created} chỉ ghi lần đầu, {@code lastSeen}
 * cập nhật mỗi lần xem; tên/đơn vị làm mới theo hồ sơ hiện tại.
 */
@Component
@RequiredArgsConstructor
public class SeenDocumentDetailRecorder {

    public static final String FIELD = "log_seen_document_detail";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserDisplayInfoService userDisplayInfoService;

    /** Ghi/cập nhật dòng của {@code username} vào {@code fields}; luôn trả true (lastSeen luôn đổi). */
    public boolean record(Map<String, Object> fields, String username) {
        if (fields == null || username == null || username.isBlank()) {
            return false;
        }
        ArrayNode detail = readArray(fields.get(FIELD));
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        var displayInfo = userDisplayInfoService.resolve(username);

        ObjectNode existing = null;
        for (JsonNode item : detail) {
            JsonNode itemUsername = item.get("username");
            if (item.isObject() && itemUsername != null && username.equalsIgnoreCase(itemUsername.asText(""))) {
                existing = (ObjectNode) item;
                break;
            }
        }
        if (existing == null) {
            existing = OBJECT_MAPPER.createObjectNode();
            existing.put("username", username);
            existing.put("created", now);
            detail.add(existing);
        } else if (!existing.hasNonNull("created")) {
            existing.put("created", now);
        }
        existing.put("name", displayInfo.fullName());
        existing.put("unit", displayInfo.unit());
        existing.put("lastSeen", now);
        fields.put(FIELD, OBJECT_MAPPER.convertValue(detail, Object.class));
        return true;
    }

    private ArrayNode readArray(Object raw) {
        if (raw == null) {
            return OBJECT_MAPPER.createArrayNode();
        }
        try {
            JsonNode node = raw instanceof String text ? OBJECT_MAPPER.readTree(text) : OBJECT_MAPPER.valueToTree(raw);
            return node != null && node.isArray() ? (ArrayNode) node : OBJECT_MAPPER.createArrayNode();
        } catch (Exception ex) {
            return OBJECT_MAPPER.createArrayNode();
        }
    }
}
