package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (3): request cho {@code POST /api/v1/duthao/ChuyenThanhVanBanDi}.
 */
@Data
public class ChuyenThanhVanBanDiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Ý kiến tuỳ chọn khi chuyển văn thư (Domino: {@code opinion} trong FormData của
     * DuThaoActionModal.jsx) — ghi vào vết draft_process nếu khác rỗng, xem
     * {@link ChuyenThanhVanBanDiUseCase#writeProcessTrail}. */
    private String opinion;
}
