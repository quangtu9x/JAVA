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

    /**
     * Ký tự không thể thoát khỏi chuỗi SQL {@code '...'} (không có {@code '}/{@code \}), rộng hơn
     * {@link #SAFE_FIELD_NAME} để không làm hỏng tên field Domino có {@code $ . -}.
     */
    private static final Pattern SQL_LITERAL_SAFE_KEY = Pattern.compile("^[A-Za-z0-9_$.\\-]{1,100}$");

    /**
     * 28/10/2026 (rà soát bảo mật): chốt chặn cho tên field DO SERVER DỰNG (map điều kiện lọc của view) bị
     * nối thẳng vào {@code dynamic_fields->>'...'}. Ném lỗi thay vì bỏ qua: bỏ qua 1 điều kiện lọc sẽ NỚI
     * RỘNG kết quả (rò rỉ dữ liệu) — với tên cột tìm kiếm từ client thì dùng {@link #isSafeFieldName} để lọc.
     */
    static String requireSafeFieldName(String name) {
        if (name == null || !SQL_LITERAL_SAFE_KEY.matcher(name).matches()) {
            throw new IllegalArgumentException("Tên trường không hợp lệ");
        }
        return name;
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
        String likeValue = SearchTextSql.containsLike(keyword); // 20/10/2026: không phân biệt dấu
        sql.append(" AND (");
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String column = columns.get(index);
            String parameterName = "keyword" + index;
            sql.append("td_search_norm(COALESCE(").append(alias).append(".dynamic_fields->>'").append(column).append("', ''))")
                .append(" LIKE :").append(parameterName);
            if ("subject".equalsIgnoreCase(column) || "title".equalsIgnoreCase(column)) {
                sql.append(" OR td_search_norm(COALESCE(").append(alias).append(".title, '')) LIKE :").append(parameterName);
            }
            parameters.put(parameterName, likeValue);
        }
        sql.append(")");
    }
}
