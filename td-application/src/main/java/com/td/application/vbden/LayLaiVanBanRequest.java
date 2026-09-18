package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N18 (04/09/2026 — "Lấy lại VB", nút {@code VBDenButtonActionVT.jsx#btnLayLaiVanBan}): request cho
 * {@code POST /api/v1/vbden/LayLaiVanBan} — tương thích 1 PHẦN {@code qlvb/vbden/VanThuThuHoiVanBan}
 * ({@code VanThuThuHoiVanBan.java} thật — xem lý do CHỈ port 1 phần trong javadoc
 * {@link LayLaiVanBanUseCase}).
 */
@Data
public class LayLaiVanBanRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Lý do lấy lại — FE ({@code Modal.confirm} trong {@code VBDenButtonActionVT.jsx}) BẮT BUỘC nhập. */
    @NotBlank(message = "opinion khong duoc de trong")
    private String opinion;

    private String creatorName;

    private String position;
}
