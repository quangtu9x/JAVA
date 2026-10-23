package com.td.infrastructure.persistence.repository;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

/**
 * 20/10/2026 (báo lỗi người dùng: ô tìm kiếm trong danh sách gõ KHÔNG DẤU không ra kết quả): so khớp từ khoá
 * KHÔNG phân biệt dấu/hoa thường — cột bọc {@code td_search_norm(...)} (hàm SQL, migration
 * {@code V1.0.75__Search_Unaccent_Normalize.sql}: unaccent + lower, đ→d) và từ khoá chuẩn hoá CÙNG quy tắc ở
 * Java ({@link #normalize}), nên "cong van", "Công Văn", "CONG VAN" đều khớp "Công văn".
 */
public final class SearchTextSql {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private SearchTextSql() {
    }

    /** Biểu thức SQL đã chuẩn hoá của {@code sqlExpression} (vd {@code COALESCE(d.title, '')}). */
    public static String norm(String sqlExpression) {
        return "td_search_norm(" + sqlExpression + ")";
    }

    /** Chữ thường + bỏ dấu (đ→d) — khớp {@code td_search_norm} phía SQL. Null → "". */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        return DIACRITICS.matcher(Normalizer.normalize(lowered, Normalizer.Form.NFD)).replaceAll("");
    }

    /** Giá trị LIKE "chứa" cho ô tìm trong danh sách: {@code %từ khoá chuẩn hoá%}. */
    public static String containsLike(String keyword) {
        return "%" + normalize(keyword) + "%";
    }

    /** JPA Criteria: {@code td_search_norm(COALESCE(expr, ''))} — dùng với {@link #containsLike}. */
    public static Expression<String> norm(CriteriaBuilder cb, Expression<String> expression) {
        return cb.function("td_search_norm", String.class, cb.coalesce(expression, ""));
    }
}
