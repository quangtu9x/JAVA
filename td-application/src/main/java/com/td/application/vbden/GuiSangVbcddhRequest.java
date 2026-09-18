package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-211 (VBDen — Xử lý & Chuyển tiếp) — DoD (4): request cho
 * {@code POST /api/v1/vbden/GuiSangVBCDDH} — tương thích API legacy
 * {@code qlvb/vbden/GuiSangVBCDDH} ({@code {listchutri, listphoihop, thoihan, ykien}}, 2 field đầu
 * Domino gửi dạng JSON-string-hoá 1 mảng object — ở đây Java nhận thẳng mảng object đã parse sẵn,
 * xem {@link NhiemVuCdDhAssigneeDto}).
 *
 * <p><b>Sửa 17/09/2026</b> (đóng gap "GuiSangVbcddhUseCase chưa replicate TaoNhiemVu()"):
 * {@code listChuTri}/{@code listPhoiHop} đổi từ {@code List<String>} (chỉ chứa username thô, KHÔNG đủ
 * thông tin để tạo nhiệm vụ) sang {@code List<NhiemVuCdDhAssigneeDto>} — khớp đúng cấu trúc Domino
 * thật ({@code TaoNhiemVu()} đọc {@code name}/{@code identifier}/{@code username}/{@code receiver}/
 * {@code user_receiver} từ mỗi phần tử).</p>
 */
@Data
public class GuiSangVbcddhRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private List<NhiemVuCdDhAssigneeDto> listChuTri = new ArrayList<>();

    private List<NhiemVuCdDhAssigneeDto> listPhoiHop = new ArrayList<>();

    private String thoiHan;

    private String yKien;
}
