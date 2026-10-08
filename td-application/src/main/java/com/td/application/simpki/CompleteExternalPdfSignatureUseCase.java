package com.td.application.simpki;

import com.td.application.common.models.Result;

import java.util.Map;

/**
 * 07/10/2026 (báo lỗi "ký USB Token -> /api/pdf/external-signature/complete trả HTTP 500"): TDPdfViewer
 * gọi THẲNG {@code /api/pdf/external-signature/complete} theo origin của viewer, nhưng máy chủ triển khai
 * (qlvbv6) không định tuyến {@code /api/pdf/*} tới TDSimPki (mọi /api/* ngoài /api/v1 trả 500 rỗng từ
 * openresty) và viewer cũng không có API key. Đi đúng đường luồng ký SIM: viewer -> host ThanhHoaDEV
 * (postMessage) -> td-web (endpoint này, giữ X-API-Key/X-Tenant-Id phía server) -> TDSimPki.
 *
 * <p>Chuyển tiếp nguyên văn body JSON của viewer ({@code preparedPdfBase64, byteRange, contentsOffset,
 * contentsLength, hashAlgorithm, signatureAlgorithm, encryptionStandard, signatureValueBase64,
 * certBase64, fileName, requestId}) — TDSimPki tự xác minh ByteRange/RSA (ExternalPdfSignatureService),
 * td-web không xử lý mật mã. Không nhận/không chuyển PIN.</p>
 */
public interface CompleteExternalPdfSignatureUseCase {
    Result<ExternalPdfSignatureResult> execute(Map<String, Object> request);
}
