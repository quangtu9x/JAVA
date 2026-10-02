package com.td.infrastructure.simpki;

import com.td.application.common.models.Result;
import com.td.application.simpki.DownloadSignedPdfUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DownloadSignedPdfUseCaseImpl implements DownloadSignedPdfUseCase {

    private final TdSimPkiClient client;

    public DownloadSignedPdfUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<byte[]> execute(String simPkiFileId) {
        if (simPkiFileId == null || simPkiFileId.isBlank()) {
            return Result.failure("Thieu simPkiFileId");
        }
        try {
            return Result.success(client.download(simPkiFileId));
        } catch (TdSimPkiException ex) {
            log.error("Tai PDF da ky that bai: simPkiFileId={}", simPkiFileId, ex);
            return Result.failure(ex.getMessage());
        }
    }
}
