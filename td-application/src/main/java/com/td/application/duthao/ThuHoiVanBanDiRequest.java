package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (4): request cho {@code POST /api/v1/duthao/ThuHoiVanBanDi}.
 */
@Data
public class ThuHoiVanBanDiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Ý kiến tuỳ chọn khi thu hồi (Domino: {@code opinion} trong FormData của
     * DuThaoActionModal.jsx) — ghi vào vết draft_process nếu khác rỗng, xem
     * {@link ThuHoiVanBanDiUseCase#writeProcessTrail}. */
    private String opinion;
}
