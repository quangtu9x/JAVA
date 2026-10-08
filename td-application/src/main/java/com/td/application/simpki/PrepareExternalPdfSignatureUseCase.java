package com.td.application.simpki;

import com.td.application.common.models.Result;

import java.util.Map;

/**
 * 08/10/2026 (ký USB Token: "tem như ký SIM, đặt bao nhiêu ký bấy nhiêu lần"): chuyển tiếp sang TDSimPki
 * {@code POST /api/pdf/external-signature/prepare} — thêm 1 chữ ký PDF CÓ HÌNH tại 1 vị trí tem, trả
 * {@code preparedPdfBase64, byteRange, contentsOffset, contentsLength, hashAlgorithm, hashBase64}. Viewer gọi
 * lặp cho từng tem: prepare -> Token ký hash (TDClientAgent) -> {@link CompleteExternalPdfSignatureUseCase}.
 * API riêng, KHÔNG dùng chung luồng ký SIM.
 */
public interface PrepareExternalPdfSignatureUseCase {
    Result<Map<String, Object>> execute(Map<String, Object> request);
}
