package com.td.application.common.dynamicfields;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lọc field theo tham số {@code items} ({@code "getall"} hoặc {@code "field1;field2"}), tương
 * đương {@code ShowItems()} phía Domino. Dùng chung cho Dynamic Data Engine (BL-101, field động
 * của document) và View Query Engine (BL-102, field của user/document trả về từ view).
 *
 * <p>Bugfix 24/08/2026 (báo lỗi người dùng: click "xem chi tiết" từ danh sách duthao/xuly/toanbo
 * — flash 1 cái rồi vẫn ở danh sách, KHÔNG mở được chi tiết): FE gọi GetView với {@code items}
 * KHÔNG liệt kê "id" (vd. {@code "name;creator;subject;signer;status;deadline_date"} —
 * ItemsList.jsx#toanbo) vì phía Domino, UNID của 1 dòng view là thứ INTRINSIC (luôn có sẵn trên
 * mọi view entry), hoàn toàn tách biệt khỏi {@code ShowItems()} (chỉ chọn CỘT GIÁ TRỊ hiển thị) —
 * Domino KHÔNG BAO GIỜ cần liệt kê "id" trong đó để có nó. {@code filter()} bản cũ coi "id" như 1
 * field bình thường, bị lọc mất nếu {@code items} không nhắc tên nó -> FE nhận dòng KHÔNG có "id"
 * -> {@code handleButton("chi-tiet", record)} điều hướng với {@code currentdocid=undefined} -> ở
 * lại danh sách. Luôn giữ lại "id" (nếu {@code allFields} có) bất kể {@code items} yêu cầu gì, để
 * đúng ngữ nghĩa Domino.</p>
 */
public final class ItemsFilter {

    private static final String ID_FIELD = "id";

    private ItemsFilter() {
    }

    public static Map<String, Object> filter(Map<String, Object> allFields, String items) {
        if (allFields == null) {
            return Collections.emptyMap();
        }
        if (items == null || items.isBlank() || "getall".equalsIgnoreCase(items.trim())) {
            return allFields;
        }

        Set<String> requestedFields = splitSemicolonList(items);
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (allFields.containsKey(ID_FIELD)) {
            filtered.put(ID_FIELD, allFields.get(ID_FIELD));
        }
        for (String field : requestedFields) {
            if (allFields.containsKey(field)) {
                filtered.put(field, allFields.get(field));
            }
        }
        return filtered;
    }

    public static Set<String> splitSemicolonList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(";"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }
}
