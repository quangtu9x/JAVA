package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.common.models.Result;
import com.td.application.simpki.CompleteExternalPdfSignatureUseCase;
import com.td.application.simpki.ExternalPdfSignatureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Xem javadoc {@link CompleteExternalPdfSignatureUseCase}. */
@Slf4j
@Service
public class CompleteExternalPdfSignatureUseCaseImpl implements CompleteExternalPdfSignatureUseCase {

    // Chỉ chuyển tiếp đúng các field TDSimPki nhận (ExternalPdfSignatureRequest) — bỏ mọi field lạ.
    private static final List<String> ALLOWED_FIELDS = List.of(
        "requestId", "preparedPdfBase64", "byteRange", "contentsOffset", "contentsLength",
        "hashAlgorithm", "signatureAlgorithm", "encryptionStandard",
        "signatureValueBase64", "certBase64", "fileName",
        // 07/10/2026: signed attributes CMS do TDClientAgent ky (signatureFormat=cms-signed-attributes).
        "signedAttributesBase64");

    private final TdSimPkiClient client;

    public CompleteExternalPdfSignatureUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<ExternalPdfSignatureResult> execute(Map<String, Object> request) {
        if (request == null || request.get("preparedPdfBase64") == null) {
            return Result.failure("Thieu PDF da chuan bi de hoan tat chu ky");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String field : ALLOWED_FIELDS) {
            if (request.containsKey(field)) {
                payload.put(field, request.get(field));
            }
        }
        try {
            JsonNode json = client.completeExternalSignature(payload);
            String fileBase64 = json.path("fileBase64").asText(null);
            if (fileBase64 == null || fileBase64.isBlank()) {
                return Result.failure("TDSimPki khong tra PDF da ky");
            }
            return Result.success(new ExternalPdfSignatureResult(fileBase64, json.path("fileName").asText(null)));
        } catch (TdSimPkiException ex) {
            log.error("Hoan tat chu ky USB Token qua TDSimPki that bai (requestId={}): {}",
                request.get("requestId"), ex.getMessage());
            return Result.failure(ex.getMessage());
        }
    }
}
