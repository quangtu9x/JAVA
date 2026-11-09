package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 30/10/2026: request cho {@code POST /api/v1/duthao/LienKetVanBanDiXuLy} — xem javadoc
 * {@link LienKetVanBanDiXuLyDuThaoUseCase}.
 */
@Data
public class LienKetVanBanDiXuLyDuThaoRequest {

    @NotNull(message = "draftId khong duoc de trong")
    private UUID draftId;

    @NotNull(message = "vbdiId khong duoc de trong")
    private UUID vbdiId;
}
