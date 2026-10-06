package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** 03/10/2026 — xem {@link XoaTepVetDuThaoUseCase}. */
@Data
public class XoaTepVetDuThaoRequest {

    @NotNull(message = "processId khong duoc de trong")
    private UUID processId;

    @NotNull(message = "fileId khong duoc de trong")
    private UUID fileId;
}
