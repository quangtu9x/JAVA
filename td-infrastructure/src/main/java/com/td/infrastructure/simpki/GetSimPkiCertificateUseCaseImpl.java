package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.common.models.Result;
import com.td.application.simpki.GetSimPkiCertificateUseCase;
import com.td.application.simpki.SimPkiCertificateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class GetSimPkiCertificateUseCaseImpl implements GetSimPkiCertificateUseCase {

    private final TdSimPkiClient client;

    public GetSimPkiCertificateUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<SimPkiCertificateResult> execute(String msisdn) {
        if (msisdn == null || !msisdn.matches("\\d{8,15}")) {
            return Result.failure("So dien thoai (msisdn) khong hop le - can 8-15 chu so");
        }
        try {
            JsonNode json = client.getCertificate(msisdn);
            return Result.success(new SimPkiCertificateResult(
                json.path("msisdn").asText(null),
                json.path("certificateBase64").asText(null),
                json.path("certificateChainDerBase64").asText(null),
                json.path("serialNumber").asText(null),
                json.path("subjectDn").asText(null),
                json.path("issuerDn").asText(null),
                parseInstant(json.path("notBefore").asText(null)),
                parseInstant(json.path("notAfter").asText(null)),
                json.path("signatureAlgorithm").asText(null),
                json.path("publicKeyAlgorithm").asText(null),
                json.path("publicKeySizeBits").isMissingNode() ? null : json.path("publicKeySizeBits").asInt(),
                json.path("certificateChainLength").isMissingNode() ? null : json.path("certificateChainLength").asInt()));
        } catch (TdSimPkiException ex) {
            log.error("Tra cuu chung thu so SIM-PKI that bai", ex);
            return Result.failure(ex.getMessage());
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
