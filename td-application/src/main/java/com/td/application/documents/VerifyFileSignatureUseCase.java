package com.td.application.documents;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

import java.util.List;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): cổng (port) xác thực chữ ký số nhúng trong file PDF —
 * impl thật (PDFBox + BouncyCastle, đọc {@code /ByteRange}/{@code /Contents} của signature
 * dictionary) nằm ở {@code td-infrastructure} (cần truy cập {@code MinioService} lấy byte file),
 * theo đúng nguyên tắc Dependency Inversion đã dùng cho {@link UploadFileUseCase}/
 * {@link GetFileUseCase}.
 */
public interface VerifyFileSignatureUseCase extends UseCase<VerifyFileSignatureRequest, Result<List<ChuKySoDto>>> {
}
