package com.td.infrastructure.persistence.repository;

import java.util.Map;

/**
 * Nhánh SQL khớp THÀNH VIÊN của field động lưu dạng chuỗi scalar nối {@code ";"} (vd
 * {@code author = "demo3;lpha;H05.01"}, {@code user_coordinator = "demo1;demo2"}).
 *
 * <p>Bugfix 27/09/2026 (người dùng báo DT-29136 "người phối hợp không thấy dự thảo", yêu cầu sửa
 * tổng thể mọi module): cùng 1 field danh sách có thể mang 2 dạng — JSON array (use case Java ghi)
 * hoặc chuỗi {@code "a;b"} (FE ghi qua Lưu; {@code AuthorAclFieldsService} cố ý ghi {@code author}
 * như vậy). Các predicate khoá cá nhân/tổ chức cũ chỉ có containment {@code @>} (khớp array) +
 * equality toàn chuỗi (khớp scalar 1 giá trị), nên văn bản biến mất khỏi view ngay khi danh sách có
 * từ 2 phần tử. Đo DB thật tenant skhcn: {@code author} dạng chuỗi nhiều giá trị ở documents (27),
 * vbdi (4), duthao (7), hosocv (1); {@code user_coordinator} duthao; {@code userxemdebiet_send} vbdi.
 *
 * <p>Gọi NGAY SAU nhánh equality, bên trong cặp ngoặc OR của field đó — chỉ thêm 1 nhánh
 * {@code OR ...}, không đổi các nhánh cũ. So khớp case-insensitive, bỏ khoảng trắng (tương đương
 * {@code @Explode(field; ";")} + {@code sortnocase} của cột Domino). Username/mã định danh không
 * bao giờ chứa {@code ";"} nên không đổi nghĩa với field scalar 1 giá trị; ký tự {@code % _ \}
 * trong giá trị được escape (LIKE mặc định ESCAPE '\' ở PostgreSQL).
 */
final class DelimitedMemberSql {

    private DelimitedMemberSql() {
    }

    static void appendOrMember(
            StringBuilder sql, Map<String, Object> parameters, String fieldName, String value, String paramName) {
        String normalized = value == null ? "" : value.replace(" ", "").toLowerCase();
        if (normalized.isEmpty() || normalized.contains(";")) {
            return;
        }
        sql.append(" OR (';' || REPLACE(LOWER(COALESCE(d.dynamic_fields->>'").append(fieldName)
            .append("', '')), ' ', '') || ';') LIKE :").append(paramName);
        parameters.put(paramName, "%;" + escapeLike(normalized) + ";%");
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
