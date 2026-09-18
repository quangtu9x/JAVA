package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N16 (03/09/2026 — bổ sung theo yêu cầu người dùng, xem {@code docs/analysis/02-checklist-port-
 * vbden-con-lai.md}): request cho {@code POST /api/v1/vbden/ChuyenVanBanPhapQuy} — tương thích 1
 * PHẦN {@code qlvb/tichhop/VBdenChuyenVanBanPhapQuy} ({@code VBdenChuyenVanBanPhapQuy.java} thật —
 * xem lý do CHỈ port 1 phần trong javadoc {@link VBdenChuyenVanBanPhapQuyUseCase}).
 *
 * <p>Giữ tên field {@code idqlvb}/{@code mailin} Y HỆT Domino gốc (khác quy ước camelCase field
 * khác của dự án) — FE {@code VBDenButtonActionVT.jsx#handleButton("chuyenvbqp")} đã gửi đúng 2 tên
 * này, không đổi để cutover chỉ cần đổi URL, không đổi payload.</p>
 */
@Data
public class VBdenChuyenVanBanPhapQuyRequest {

    @NotNull(message = "idqlvb khong duoc de trong")
    private UUID idqlvb;

    /** Domino: {@code vbpq_SendTo} — địa chỉ "mailin" kết nối hệ thống văn bản quy phạm (FE gửi từ
     * {@code systemConfig?.data?.cfg_vbpq_mailin}), dùng làm người nhận email (xem javadoc use case). */
    @NotBlank(message = "chua khai bao mailin ket noi van ban quy pham!")
    private String mailin;
}
