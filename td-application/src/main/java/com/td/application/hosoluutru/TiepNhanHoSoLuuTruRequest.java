package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Request cho {@link TiepNhanHoSoLuuTruUseCase}/{@link TraLaiHoSoLuuTruUseCase}. */
@Getter
@Setter
public class TiepNhanHoSoLuuTruRequest {

    @NotNull
    private UUID hosoLuuTruId;

    /** Ý kiến (tuỳ chọn) — tương đương field {@code subject} form {@code quatrinhchuyenluutru}. */
    private String opinion;
}
