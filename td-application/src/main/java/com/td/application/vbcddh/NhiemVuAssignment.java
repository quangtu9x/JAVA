package com.td.application.vbcddh;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * BL-250 (VBCDDH Core) — 1 đơn vị/cá nhân được phân công thực hiện 1 văn bản chỉ đạo. Domino thật
 * ({@code NhiemVuChiTietPage.jsx}): mỗi đơn vị chọn trong {@code donvithuchien_obj} sinh ra 1 bản
 * ghi {@code nhiemvu} riêng (1 văn bản → N nhiệm vụ, N = số đơn vị được chọn).
 */
@Data
public class NhiemVuAssignment {

    @NotBlank(message = "donViThucHien khong duoc de trong")
    private String donViThucHien;

    @NotBlank(message = "donViThucHienHide khong duoc de trong")
    private String donViThucHienHide;

    private String noiDungChiDao;

    private String hanXuLy;
}
