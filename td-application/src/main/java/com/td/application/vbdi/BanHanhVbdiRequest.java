package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-220 (VBDi Core) — DoD (3): request cho {@code POST /api/v1/vbdi/BanHanh}.
 */
@Data
public class BanHanhVbdiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Định dạng ISO {@code yyyy-MM-dd}; mặc định hôm nay nếu để trống. */
    private String promulgationDate;
}
