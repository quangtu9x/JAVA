package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 18/09/2026 (cutover {@code ModalThuHoiVanBan.jsx} — màn chi tiết văn bản đến, xem javadoc
 * {@link GetNguoiThuHoiUseCase}): request cho {@code POST /api/v1/vbden/GetNguoiThuHoi} — tương
 * thích API legacy {@code qlvb/vbden/GetNguoiThuHoi} ({@code GetNguoiThuHoi.java} thật).
 */
@Data
public class GetNguoiThuHoiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;
}
