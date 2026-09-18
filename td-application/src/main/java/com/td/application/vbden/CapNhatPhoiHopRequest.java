package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N20 (07/09/2026 — "Cập nhật kết quả phối hợp xử lý", nút phối hợp trong {@code ChoYKienModal.jsx}
 * với {@code action="capnhatphoihop"} — xem javadoc {@link CapNhatPhoiHopUseCase}): request cho
 * {@code POST /api/v1/vbden/CapNhatPhoiHop} — tương thích 1 PHẦN {@code qlvb/vbden/
 * CapNhatVanBanDen?action=capnhatphoihop} ({@code CapNhatVanBanDen.java} thật, hàm
 * {@code capnhatphoihop()}, dòng 425-451).
 */
@Data
public class CapNhatPhoiHopRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Ý kiến phối hợp — FE ({@code ChoYKienModal.jsx}) gửi field {@code opinion}, không bắt buộc. */
    private String opinion;

    /** Tên hiển thị người thực hiện — chỉ để hiển thị (giống {@link KetThucXuLyRequest#getCreatorName()}). */
    private String creatorName;

    /** Chức vụ hiển thị — chỉ để hiển thị. */
    private String position;
}
