package com.td.application.duthao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDisplayInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 25/09/2026 (yêu cầu người dùng "trong dự thảo 'Nhật ký đã xem' chưa hoạt động -> tham khảo văn bản
 * đi đã làm bổ sung sang dự thảo"): bản dự thảo của
 * {@link com.td.application.vbdi.AddVbdiSeenDocumentUseCase} — gọi khi mở xem chi tiết dự thảo.
 *
 * <p>Ghi 2 field động trên {@code duthao_documents}, CÙNG khuôn vbdi để FE dùng chung
 * {@code LogSeenDocument.jsx}:
 * <ul>
 * <li>{@value #FIELD_LOG_SEEN_DOCUMENT}: chuỗi username nối {@code ";"} (có mặt hay không).</li>
 * <li>{@value #FIELD_LOG_SEEN_DOCUMENT_DETAIL}: mảng JSON {@code {username,name,unit,created,lastSeen}}
 * dedup theo username — {@code created} = lần xem đầu (giữ nguyên), {@code lastSeen}/{@code name}/
 * {@code unit} làm mới mỗi lần xem; tên/đơn vị tra server-side qua {@link UserDisplayInfoService}.</li>
 * </ul>
 * Không port các field "ẩn/hiện theo người phối hợp" của vbdi ({@code coordinator_hide_*},
 * {@code userxemdebiet}) — không có view dự thảo nào đọc các field đó.</p>
 *
 * <p>Danh tính SERVER-AUTHORITATIVE qua {@link CurrentUserProvider}. Guard: tồn tại, đúng
 * {@code documentType="duthao"}, chưa xoá mềm. Idempotent với lần mở lại.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddDuThaoSeenDocumentUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";

    static final String FIELD_LOG_SEEN_DOCUMENT = "log_seen_document";
    static final String FIELD_LOG_SEEN_DOCUMENT_DETAIL = "log_seen_document_detail";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final CurrentUserProvider currentUserProvider;
    private final UserDisplayInfoService userDisplayInfoService;

    public Result<UUID> execute(AddDuThaoSeenDocumentRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu documentId cần đánh dấu đã xem");
        }
        var document = duThaoDocumentRepository.findById(request.getDocumentId()).orElse(null);
        if (document == null || document.isDeleted()
                || !TaoDuThaoUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        addMember(fields, FIELD_LOG_SEEN_DOCUMENT, username);
        recordSeenDetail(fields, username);

        document.replaceDynamicFields(toJson(fields));
        var saved = duThaoDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        log.info("Ghi log xem du thao {} boi {}", saved.getId(), username);
        return Result.success(saved.getId());
    }

    private void recordSeenDetail(Map<String, Object> fields, String username) {
        ArrayNode detailArr = readArray(fields, FIELD_LOG_SEEN_DOCUMENT_DETAIL);
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        var displayInfo = userDisplayInfoService.resolve(username);

        ObjectNode existing = null;
        for (JsonNode item : detailArr) {
            if (item.isObject() && username.equalsIgnoreCase(textOrNull(item, "username"))) {
                existing = (ObjectNode) item;
                break;
            }
        }
        if (existing != null) {
            existing.put("name", displayInfo.fullName());
            existing.put("unit", displayInfo.unit());
            existing.put("lastSeen", now);
            if (!existing.has("created") || existing.get("created").isNull()) {
                existing.put("created", now);
            }
        } else {
            ObjectNode entry = OBJECT_MAPPER.createObjectNode();
            entry.put("username", username);
            entry.put("name", displayInfo.fullName());
            entry.put("unit", displayInfo.unit());
            entry.put("created", now);
            entry.put("lastSeen", now);
            detailArr.add(entry);
        }
        fields.put(FIELD_LOG_SEEN_DOCUMENT_DETAIL, detailArr);
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    /** Chấp nhận cả chuỗi JSON thô lẫn List/Map do {@code parseDynamicFields} tự suy kiểu. */
    private ArrayNode readArray(Map<String, Object> fields, String fieldName) {
        Object raw = fields.get(fieldName);
        if (raw == null) {
            return OBJECT_MAPPER.createArrayNode();
        }
        try {
            String text = raw instanceof String ? (String) raw : OBJECT_MAPPER.writeValueAsString(raw);
            if (text == null || text.isBlank()) {
                return OBJECT_MAPPER.createArrayNode();
            }
            JsonNode parsed = OBJECT_MAPPER.readTree(text);
            return parsed != null && parsed.isArray() ? (ArrayNode) parsed : OBJECT_MAPPER.createArrayNode();
        } catch (Exception ex) {
            return OBJECT_MAPPER.createArrayNode();
        }
    }

    private void addMember(Map<String, Object> fields, String fieldName, String username) {
        List<String> members = splitMembers(fields.get(fieldName));
        if (members.stream().noneMatch(item -> item.equalsIgnoreCase(username))) {
            members.add(username);
        }
        fields.put(fieldName, String.join(";", members));
    }

    private List<String> splitMembers(Object rawValue) {
        String text = rawValue == null ? "" : String.valueOf(rawValue);
        if (text.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split(";"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
