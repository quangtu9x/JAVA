package com.td.infrastructure.simpki;

import com.td.application.common.models.Result;
import com.td.application.simpki.SimPkiSignSubmissionResult;
import com.td.application.simpki.SubmitSimPkiSignatureRequest;
import com.td.application.simpki.SubmitSimPkiSignatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ký = upload + sign gộp thành 1 lời gọi cho caller (host chỉ cần 1 request duy nhất) — nội bộ vẫn là
 * 2 lời gọi HTTP tuần tự tới TDSimPki (upload trước để lấy {@code fileId}, rồi sign).
 */
@Slf4j
@Service
public class SubmitSimPkiSignatureUseCaseImpl implements SubmitSimPkiSignatureUseCase {

    private final TdSimPkiClient client;

    public SubmitSimPkiSignatureUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<SimPkiSignSubmissionResult> execute(SubmitSimPkiSignatureRequest request) {
        if (request == null || request.getPdfBytes() == null || request.getPdfBytes().length == 0) {
            return Result.failure("Thieu noi dung PDF can ky");
        }
        if (request.getMsisdn() == null || !request.getMsisdn().matches("\\d{8,15}")) {
            return Result.failure("So dien thoai (msisdn) khong hop le - can 8-15 chu so");
        }
        try {
            String fileId = client.uploadPdf(request.getPdfBytes(), request.getFileName());
            String status = client.sign(fileId, request.getMsisdn(), request.getDisplayMessage(), request.getSignatures());
            return Result.success(new SimPkiSignSubmissionResult(fileId, status));
        } catch (TdSimPkiException ex) {
            log.error("Nop yeu cau ky SIM-PKI that bai", ex);
            return Result.failure(ex.getMessage());
        }
    }
}
