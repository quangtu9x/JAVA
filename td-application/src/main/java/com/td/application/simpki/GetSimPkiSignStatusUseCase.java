package com.td.application.simpki;

import com.td.application.common.models.Result;

/** Tra trạng thái 1 lượt ký SIM-PKI theo {@code simPkiFileId} (trả về từ {@link SubmitSimPkiSignatureUseCase}). */
public interface GetSimPkiSignStatusUseCase {
    Result<SimPkiSignStatusResult> execute(String simPkiFileId);
}
