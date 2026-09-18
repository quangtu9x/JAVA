package com.td.application.common.security;

import java.util.Optional;
import java.util.Set;

/**
 * Cổng (port) để lấy username của người dùng hiện tại, dùng cho các nhu cầu ghi nhận tác giả thao
 * tác (vd. {@code document_logs.created_by} — BL-103) mà không để {@code td-application} phụ
 * thuộc ngược vào {@code td-infrastructure} (nơi thực sự đọc {@code SecurityContextHolder}).
 */
public interface CurrentUserProvider {

    Optional<String> currentUsername();

    /**
     * Bổ sung 09/09/2026 (port số liệu CẤP CƠ QUAN của {@code SoLuongCanXuLy} — xem javadoc
     * {@code GetSoLuongCanXuLyUseCase}): tên quyền QLVB thật (SEC-01, xem {@code QlvbRoles} ở
     * {@code td-infrastructure} — KHÔNG dùng lại được ở đây vì {@code td-application} không được
     * phụ thuộc ngược vào {@code td-infrastructure}, các use case cần role phải tự khai lại đúng
     * chuỗi Domino gốc) của người dùng đang đăng nhập, KHÔNG có tiền tố {@code "ROLE_"}. Rỗng nếu
     * chưa đăng nhập/không đọc được.
     *
     * <p><b>Hợp đồng (bugfix 09/09/2026, xem javadoc {@code SecurityContextCurrentUserProvider})</b>:
     * LUÔN viết HOA (implementation tự chuẩn hoá) — 2 luồng xác thực (JWT/Keycloak vs Tdtoken) lưu
     * case KHÁC NHAU ở tầng dưới, so khớp phải luôn viết HOA cả 2 phía để không lệch.</p>
     */
    Set<String> currentRoles();
}
