package com.td.application.phieutrinhtd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** 06/10/2026 - request {@code POST /api/v1/phieutrinhtd/ChoYKien}, xem {@link ChoYKienPhieuTrinhTdUseCase}. */
@Data
public class ChoYKienPhieuTrinhTdRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Nội dung ý kiến góp ý. */
    private String subject;
}
