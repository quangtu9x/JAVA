package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): request cho {@code POST /api/v1/duthao/VanThuKhongBanHanh} — đối chiếu
 * trực tiếp {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#ChuyenTrangThaiDuThaoVanThuXoa()} —
 * văn thư từ chối, KHÔNG ban hành văn bản đi sinh ra từ dự thảo này.
 */
@Data
public class DuThaoVanThuKhongBanHanhRequest {

    /** Id dự thảo cha cần cập nhật — bắt buộc. */
    @NotNull(message = "id không được để trống")
    private UUID id;
}
