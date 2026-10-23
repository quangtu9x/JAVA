package com.td.infrastructure.persistence.repository;

import java.util.List;
import java.util.Map;

/**
 * 19/10/2026 (bộ lọc nâng cao — ô nhập tự do khớp "chứa chuỗi"): điều kiện {@code ILIKE '%giá trị%'} trên
 * field động, dùng chung cho mọi bảng văn bản (documents, vbdi_documents, hosocv_documents,
 * vbden_xinykien_documents). Trước đây các ô như "Cơ quan ban hành" (vbden), "Số, ký hiệu" (vbdi), "Ký
 * hiệu/Tiêu đề hồ sơ" khớp BẰNG/containment nên phải gõ đúng nguyên giá trị.
 *
 * <ul>
 *   <li>Field là mảng JSON vẫn khớp (so trên text của {@code ->>}).</li>
 *   <li>{@code title}/{@code subject} khớp thêm cột tĩnh {@code title} khi bảng có cột đó (cùng lý do
 *   {@link KeywordSql}).</li>
 *   <li>Tên field chỉ nhận định danh an toàn ({@link KeywordSql#isSafeFieldName}); ký tự {@code % _ \}
 *   trong giá trị người dùng được escape (gõ "%" không thành "khớp mọi thứ").</li>
 * </ul>
 */
final class TextContainsSql {

    private TextContainsSql() {
    }

    static void append(StringBuilder sql, Map<String, Object> parameters, Map<String, String> textContains,
                       boolean hasTitleColumn) {
        if (textContains == null || textContains.isEmpty()) {
            return;
        }
        int index = 0;
        for (Map.Entry<String, String> entry : textContains.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            if (!KeywordSql.isSafeFieldName(field) || value == null || value.isBlank()) {
                continue;
            }
            String param = "txtContains" + index++;
            parameters.put(param, "%" + escapeLike(SearchTextSql.normalize(value)) + "%"); // 20/10/2026: không phân biệt dấu
            sql.append(" AND (td_search_norm(COALESCE(d.dynamic_fields->>'").append(field).append("', '')) LIKE :")
                .append(param).append(" ESCAPE '\\'");
            if (hasTitleColumn && ("title".equals(field) || "subject".equals(field))) {
                sql.append(" OR td_search_norm(COALESCE(d.title, '')) LIKE :").append(param).append(" ESCAPE '\\'");
            }
            sql.append(")");
        }
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * field → danh sách giá trị, khớp ÍT NHẤT 1 (OR): mảng JSON chứa phần tử HOẶC scalar bằng — cùng
     * ngữ nghĩa {@code appendContainsAnyFieldValuePredicates} của các repository khác, cho bảng chưa có
     * (vd vbden_xinykien_documents).
     */
    static void appendContainsAny(StringBuilder sql, Map<String, Object> parameters,
                                  Map<String, List<String>> containsAnyFieldValues, String paramPrefix) {
        if (containsAnyFieldValues == null || containsAnyFieldValues.isEmpty()) {
            return;
        }
        int fieldIndex = 0;
        for (Map.Entry<String, List<String>> entry : containsAnyFieldValues.entrySet()) {
            String field = entry.getKey();
            List<String> values = entry.getValue();
            fieldIndex++;
            if (!KeywordSql.isSafeFieldName(field) || values == null || values.isEmpty()) {
                continue;
            }
            sql.append(" AND (");
            boolean first = true;
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String value = values.get(valueIndex);
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (!first) {
                    sql.append(" OR ");
                }
                first = false;
                String containsParam = paramPrefix + fieldIndex + "_" + valueIndex;
                String scalarParam = paramPrefix + "S" + fieldIndex + "_" + valueIndex;
                sql.append("(d.dynamic_fields->'").append(field).append("' @> jsonb_build_array(CAST(:")
                    .append(containsParam).append(" AS text))")
                    .append(" OR COALESCE(d.dynamic_fields->>'").append(field).append("', '') = :")
                    .append(scalarParam).append(")");
                parameters.put(containsParam, value);
                parameters.put(scalarParam, value);
            }
            if (first) {
                sql.append("1 = 0");
            }
            sql.append(")");
        }
    }
}
