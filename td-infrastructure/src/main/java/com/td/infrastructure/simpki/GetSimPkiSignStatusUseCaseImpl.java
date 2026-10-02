package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.common.models.Result;
import com.td.application.simpki.GetSimPkiSignStatusUseCase;
import com.td.application.simpki.SimPkiSignStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GetSimPkiSignStatusUseCaseImpl implements GetSimPkiSignStatusUseCase {

    private final TdSimPkiClient client;

    public GetSimPkiSignStatusUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<SimPkiSignStatusResult> execute(String simPkiFileId) {
        if (simPkiFileId == null || simPkiFileId.isBlank()) {
            return Result.failure("Thieu simPkiFileId");
        }
        try {
            JsonNode json = client.status(simPkiFileId);
            String status = json.path("status").asText(null);
            String errorDetail = json.path("errorDetail").isNull() ? null : json.path("errorDetail").asText(null);
            boolean signed = json.path("signed").asBoolean(false);
            return Result.success(new SimPkiSignStatusResult(simPkiFileId, status, errorDetail, signed));
        } catch (TdSimPkiException ex) {
            log.error("Tra trang thai ky SIM-PKI that bai: simPkiFileId={}", simPkiFileId, ex);
            return Result.failure(ex.getMessage());
        }
    }
}
