package com.td.infrastructure.persistence.repository;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 01/10/2026 (rà soát "tìm trong danh sách" dự thảo/hồ sơ, sau khi sửa vbden — xem
 * {@code PostgresDocumentRepository#appendKeywordPredicate}): điều kiện LIKE trên nhiều cột cho 1 từ khoá,
 * dùng chung cho các bảng có cột tĩnh {@code title} (duthao_documents, hosocv_documents):
 * <ul>
 *   <li>Trích yếu/tiêu đề ({@code subject}/{@code title}) lưu ở CỘT TĨNH {@code title} (controller tách
 *   khỏi dynamic_fields lúc tạo) — khớp thêm {@code alias.title}, nếu không gõ trích yếu không bao giờ ra.</li>
 *   <li>Tên cột đến TỪ CLIENT ({@code request.columns}) và bị nối thẳng vào SQL — chỉ nhận định danh an
 *   toàn {@code [A-Za-z0-9_]}, bỏ qua phần còn lại (chống SQL injection).</li>
 * </ul>
 */
final class KeywordSql {

    static final Pattern SAFE_FIELD_NAME = Pattern.compile("^[A-Za-z0-9_]{1,100}$");

    private KeywordSql() {
    }

    static boolean isSafeFieldName(String name) {
        return name != null && SAFE_FIELD_NAME.matcher(name).matches();
    }

    static void append(StringBuilder sql, Map<String, Object> parameters, String keyword, List<String> keywordColumns,
                       String alias) {
        if (keyword == null || keyword.isBlank() || keywordColumns == null) {
            return;
        }
        List<String> columns = keywordColumns.stream().filter(KeywordSql::isSafeFieldName).toList();
        if (columns.isEmpty()) {
            return;
        }
        String likeValue = "%" + keyword.trim().toLowerCase() + "%";
        sql.append(" AND (");
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String column = columns.get(index);
            String parameterName = "keyword" + index;
            sql.append("LOWER(COALESCE(").append(alias).append(".dynamic_fields->>'").append(column).append("', ''))")
                .append(" LIKE :").append(parameterName);
            if ("subject".equalsIgnoreCase(column) || "title".equalsIgnoreCase(column)) {
                sql.append(" OR LOWER(COALESCE(").append(alias).append(".title, '')) LIKE :").append(parameterName);
            }
            parameters.put(parameterName, likeValue);
        }
        sql.append(")");
    }
}
