package com.td.application.documentlinks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.domain.documentlinks.DocumentLink;
import com.td.domain.documentlinks.DocumentLinkType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 06/10/2026 - giai đoạn 2 bảng {@code document_links} (docs/architecture/document-links.md): 2 field
 * "bản chụp" của văn bản đi / dự thảo nay lưu ở bảng liên kết (cột {@code link_data}), KHÔNG lưu trong
 * {@code dynamic_fields} nữa:
 * <ul>
 *   <li>{@value #DOCUMENTIN_OBJ} - "Trả lời cho văn bản đến" ({@code *_traloi_vbden});</li>
 *   <li>{@value #VANBAN_LIENQUAN_OBJ} - "Văn bản kèm theo" ({@code *_kemtheo_vbden|vbdi}).</li>
 * </ul>
 * Lớp này là điểm DUY NHẤT biết khuôn của 2 field: dựng lại field cho response API (FE đọc như cũ),
 * gỡ field khỏi payload lưu chung, và chuyển phần tử payload thành liên kết.
 */
@Component
@RequiredArgsConstructor
public class RelatedDocumentSnapshots {

    public static final String DOCUMENTIN_OBJ = "documentin_obj";
    public static final String VANBAN_LIENQUAN_OBJ = "vanban_lienquan_obj";
    public static final String SOURCE_VBDI = "vbdi";
    public static final String SOURCE_DUTHAO = "duthao";
    private static final String DATABASE_TYPE_KEY = "database_type";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentLinkService documentLinkService;

    // ---------- loại liên kết ----------

    public static DocumentLinkType replyType(String sourceType) {
        return SOURCE_DUTHAO.equalsIgnoreCase(sourceType)
            ? DocumentLinkType.DUTHAO_TRALOI_VBDEN : DocumentLinkType.VBDI_TRALOI_VBDEN;
    }

    /** "Văn bản kèm theo": {@code database_type} của phần tử = "vbden" -> đích văn bản đến, còn lại văn bản đi. */
    public static DocumentLinkType relatedType(String sourceType, String databaseType) {
        boolean vbden = "vbden".equalsIgnoreCase(databaseType == null ? "" : databaseType.trim());
        if (SOURCE_DUTHAO.equalsIgnoreCase(sourceType)) {
            return vbden ? DocumentLinkType.DUTHAO_KEMTHEO_VBDEN : DocumentLinkType.DUTHAO_KEMTHEO_VBDI;
        }
        return vbden ? DocumentLinkType.VBDI_KEMTHEO_VBDEN : DocumentLinkType.VBDI_KEMTHEO_VBDI;
    }

    public static List<DocumentLinkType> relatedTypes(String sourceType) {
        return List.of(relatedType(sourceType, "vbden"), relatedType(sourceType, "vbdi"));
    }

    // ---------- đọc ----------

    /** Danh sách phần tử "Trả lời cho văn bản đến" theo thứ tự gắn. */
    public List<Map<String, Object>> replyItems(UUID sourceId, String sourceType) {
        return items(documentLinkService.findLinks(sourceId, List.of(replyType(sourceType))));
    }

    /** Danh sách phần tử "Văn bản kèm theo" theo thứ tự gắn. */
    public List<Map<String, Object>> relatedItems(UUID sourceId, String sourceType) {
        return items(documentLinkService.findLinks(sourceId, relatedTypes(sourceType)));
    }

    /** Id văn bản đến mà văn bản đi / dự thảo trả lời, theo thứ tự gắn (như mảng documentin_obj cũ). */
    public List<UUID> replyTargetIds(UUID sourceId, String sourceType) {
        return documentLinkService.findLinks(sourceId, List.of(replyType(sourceType))).stream()
            .map(DocumentLink::getTargetId)
            .toList();
    }

    /**
     * Dựng lại 2 field cho response (chi tiết / dòng danh sách) - ghi đè giá trị cũ còn sót trong
     * dynamic_fields. Không có liên kết thì BỎ key (không trả mảng rỗng) để các điều kiện hiện panel cũ
     * của FE ("có field và khác '[]'") giữ nguyên hành vi.
     */
    public void decorate(Map<String, Object> fields, UUID sourceId, String sourceType) {
        if (fields == null || sourceId == null) {
            return;
        }
        List<DocumentLinkType> types = new ArrayList<>(relatedTypes(sourceType));
        types.add(replyType(sourceType));
        List<Map<String, Object>> replies = new ArrayList<>();
        List<Map<String, Object>> related = new ArrayList<>();
        for (DocumentLink link : documentLinkService.findLinks(sourceId, types)) {
            (replyType(sourceType).code().equals(link.getLinkType()) ? replies : related).add(item(link));
        }
        putOrRemove(fields, DOCUMENTIN_OBJ, replies);
        putOrRemove(fields, VANBAN_LIENQUAN_OBJ, related);
    }

    // ---------- ghi ----------

    /**
     * Gỡ 2 field khỏi payload lưu chung (LuuDuThao/TaoDuThao/HieuChinh/SoanThao) - không còn lưu trong
     * dynamic_fields. @return giá trị đã gỡ theo tên field (để lúc TẠO MỚI chuyển thành liên kết).
     */
    public Map<String, Object> strip(Map<String, ?> payload) {
        Map<String, Object> removed = new LinkedHashMap<>();
        if (payload == null) {
            return removed;
        }
        for (String field : List.of(DOCUMENTIN_OBJ, VANBAN_LIENQUAN_OBJ)) {
            if (payload.containsKey(field)) {
                removed.put(field, payload.remove(field));
            }
        }
        return removed;
    }

    /**
     * Chuyển phần tử của 2 field (giá trị đã {@link #strip} khỏi payload tạo mới) thành liên kết. Chỉ thêm,
     * không gỡ liên kết đang có. Phần tử không có id UUID (Domino cũ) bị bỏ qua.
     */
    public void importFromPayload(Map<String, Object> removed, UUID sourceId, String sourceType) {
        if (removed == null || sourceId == null) {
            return;
        }
        for (Map<String, Object> item : toItems(removed.get(DOCUMENTIN_OBJ))) {
            linkItem(replyType(sourceType), sourceId, item);
        }
        for (Map<String, Object> item : toItems(removed.get(VANBAN_LIENQUAN_OBJ))) {
            linkItem(relatedType(sourceType, asString(item.get(DATABASE_TYPE_KEY))), sourceId, item);
        }
    }

    /** Gắn 1 phần tử (khuôn documentin_obj/vanban_lienquan_obj). @return true nếu tạo mới. */
    public boolean linkItem(DocumentLinkType type, UUID sourceId, Map<String, Object> item) {
        Optional<UUID> targetId = parseUuid(item == null ? null : item.get("id"));
        if (targetId.isEmpty()) {
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>(item);
        data.put("id", targetId.get().toString());
        return documentLinkService.link(type, sourceId, targetId.get(), toJson(data));
    }

    /** Chuyển liên kết + bản chụp từ dự thảo sang văn bản đi được phát hành (ChuyenThanhVanBanDi). */
    public void copyFromDraftToVbdi(UUID draftId, UUID vbdiId) {
        for (Map<String, Object> item : replyItems(draftId, SOURCE_DUTHAO)) {
            linkItem(replyType(SOURCE_VBDI), vbdiId, item);
        }
        for (DocumentLink link : documentLinkService.findLinks(draftId, relatedTypes(SOURCE_DUTHAO))) {
            String databaseType = DocumentLinkType.DUTHAO_KEMTHEO_VBDEN.code().equals(link.getLinkType()) ? "vbden" : "vbdi";
            linkItem(relatedType(SOURCE_VBDI, databaseType), vbdiId, item(link));
        }
    }

    // ---------- tiện ích ----------

    /** Phần tử từ List / Map / chuỗi JSON (dữ liệu field _obj qua nhiều đời API) - lỗi parse = rỗng. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> toItems(Object value) {
        Object parsed = value;
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return List.of();
            }
            try {
                parsed = OBJECT_MAPPER.readValue(trimmed, Object.class);
            } catch (Exception ex) {
                return List.of();
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
        } else if (parsed instanceof Map<?, ?> map) {
            result.add((Map<String, Object>) map);
        }
        return result;
    }

    private List<Map<String, Object>> items(List<DocumentLink> links) {
        return links.stream().map(this::item).toList();
    }

    private Map<String, Object> item(DocumentLink link) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.putAll(OBJECT_MAPPER.readValue(
                link.getLinkDataJson() == null ? "{}" : link.getLinkDataJson(), new TypeReference<Map<String, Object>>() { }));
        } catch (Exception ignored) {
            // bản chụp hỏng -> vẫn trả id để FE mở được văn bản
        }
        data.put("id", link.getTargetId().toString());
        return data;
    }

    private static void putOrRemove(Map<String, Object> fields, String key, List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            fields.remove(key);
        } else {
            fields.put(key, items);
        }
    }

    private static Optional<UUID> parseUuid(Object value) {
        if (value == null || value.toString().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.toString().trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
