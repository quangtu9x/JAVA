package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 — tương thích {@code qlvb/vbden/PhucHoiVanBan} (Domino thật) — xem
 * javadoc {@link PhucHoiVanBanDenUseCase}. Cùng cặp tham số với {@link XoaVanBanDenRequest}
 * ({@code idvbden}/{@code idso}), FE {@code VBDenButtonAction.jsx#handleRestore} vẫn gọi ĐÚNG tên
 * endpoint {@code PhucHoiVanBan} này — chỉ cần cutover backend, không đổi tên route.
 */
@Data
public class PhucHoiVanBanDenRequest {

    @NotNull
    private UUID idvbden;

    private UUID idso;
}
