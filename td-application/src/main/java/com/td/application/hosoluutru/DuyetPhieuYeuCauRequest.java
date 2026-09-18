package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Request cho {@link DuyetPhieuYeuCauUseCase}. */
@Getter
@Setter
public class DuyetPhieuYeuCauRequest {

    @NotNull
    private UUID phieuId;

    /** {@code true} = chấp nhận (status→"1"), {@code false} = không chấp nhận (status→"3"). */
    private boolean duyet;
}
