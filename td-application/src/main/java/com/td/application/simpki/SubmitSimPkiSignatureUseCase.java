package com.td.application.simpki;

import com.td.application.common.models.Result;

/**
 * Nộp 1 PDF để ký qua SIM-PKI (TDSimPki, {@code C:\code_projects\HSCV\TDSimPki}) — implementation
 * (td-infrastructure) giữ {@code X-API-Key}/{@code X-Tenant-Id} phía SERVER, KHÔNG BAO GIỜ để lộ ra
 * Frontend (khác {@code VITE_*} sẽ bị nhúng công khai vào bundle JS — cùng nguyên tắc đã áp dụng cho
 * {@code ONLYOFFICE_JWT_SECRET}). Async ở phía TDSimPki — kết quả trả về ngay là {@code SIGNING}, gọi
 * {@link GetSimPkiSignStatusUseCase} để theo dõi.
 */
public interface SubmitSimPkiSignatureUseCase {
    Result<SimPkiSignSubmissionResult> execute(SubmitSimPkiSignatureRequest request);
}
