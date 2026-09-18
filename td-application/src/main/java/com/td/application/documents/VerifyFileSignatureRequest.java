package com.td.application.documents;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): request xác thực chữ ký số nhúng trong 1 file PDF đã
 * upload. Tổng quát theo {@code documentId}/{@code fileId} (không riêng {@code documentType}) —
 * đúng thiết kế thật ({@code TDAttachment.VerifySignatureAttachment()} nhận {@code databasetype}
 * tuỳ ý: {@code vbden}, {@code duthao}, {@code vbdi}, {@code phieutrinh}, {@code hosocv},
 * {@code vbcddh}...).
 */
@Data
public class VerifyFileSignatureRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    @NotNull(message = "fileId khong duoc de trong")
    private UUID fileId;
}
