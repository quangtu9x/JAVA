package com.td.application.sharedcore;

import lombok.Data;

import java.util.List;
import java.util.Map;

// Bổ sung 19/08/2026: kết quả tra cứu "cấu hình hiệu lực" mà 1 user có được THÔNG QUA "Nhóm vai trò"
// (RoleGroup) đang gán — xem GetUserRoleGroupConfigUseCase. Dùng bởi QLVBCompatAuthController
// (GET /qlvb/profile) để bổ sung roles/menu_* còn thiếu cho user KHÔNG được gán quyền/vai trò trực
// tiếp mà chỉ qua "Nhóm vai trò". Rỗng (list/map rỗng) nếu user không có role group nào.
@Data
public class UserRoleGroupConfigDto {
    // Mã "quyền" (AppPermission.code, vd "QCapNhatVBDen") gán TRỰC TIẾP cho role group — khớp đúng
    // định dạng chuỗi mà FE CheckRole()/currentUser.roles đang dùng.
    private List<String> permissionCodes = List.of();

    // Key: tên field menu (vd "menu_top", "menu_qlvb", "menu_vbden"...), Value: danh sách mục menu đã
    // MERGE (union, khử trùng, giữ thứ tự) từ TẤT CẢ "Vai trò" (AppRole) con của role group — mỗi
    // AppRole lưu 1 chuỗi nối ";" cho từng field này (dynamic_fields JSONB, xem AppRole.java). Việc
    // chuyển sang đúng định dạng "dây" (wire format) mà FE mong đợi (chuỗi nối ";" cho menu_top,
    // chuỗi JSON-encode cho các menu_* còn lại — xem legacyAdminProfile.json) do
    // QLVBCompatAuthController tự làm, DTO này chỉ giữ dữ liệu ngữ nghĩa sạch (List<String>).
    private Map<String, List<String>> menuFields = Map.of();
}
