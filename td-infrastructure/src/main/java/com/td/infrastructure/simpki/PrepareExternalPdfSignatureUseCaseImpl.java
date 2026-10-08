package com.td.infrastructure.simpki;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.simpki.PrepareExternalPdfSignatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Xem javadoc {@link PrepareExternalPdfSignatureUseCase}. */
@Slf4j
@Service
public class PrepareExternalPdfSignatureUseCaseImpl implements PrepareExternalPdfSignatureUseCase {

    // Chỉ chuyển tiếp đúng field TDSimPki nhận (ExternalPdfSignaturePrepareRequest).
    private static final List<String> ALLOWED_FIELDS = List.of("requestId", "pdfBase64", "placement", "certBase64", "fileName");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TdSimPkiClient client;

    public PrepareExternalPdfSignatureUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<Map<String, Object>> execute(Map<String, Object> request) {
        if (request == null || request.get("pdfBase64") == null || request.get("placement") == null) {
            return Result.failure("Thieu PDF hoac vi tri chu ky");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String field : ALLOWED_FIELDS) {
            if (request.containsKey(field)) {
                payload.put(field, request.get(field));
            }
        }
        try {
            JsonNode json = client.prepareExternalSignature(payload);
            if (!json.hasNonNull("preparedPdfBase64") || !json.hasNonNull("hashBase64")) {
                return Result.failure("TDSimPki khong tra PDF da chuan bi");
            }
            return Result.success(OBJECT_MAPPER.convertValue(json, new TypeReference<Map<String, Object>>() { }));
        } catch (TdSimPkiException ex) {
            log.error("Chuan bi chu ky USB Token qua TDSimPki that bai (requestId={}): {}", request.get("requestId"), ex.getMessage());
            return Result.failure(ex.getMessage());
        }
    }
}
