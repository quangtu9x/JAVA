package com.td.application.vbdi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-220 (VBDi Core) — DoD (2): request cho {@code POST /api/v1/vbdi/TrinhKy}.
 */
@Data
public class TrinhKyVbdiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    @NotBlank(message = "nguoiKy khong duoc de trong")
    private String nguoiKy;

    private String opinion;
}
