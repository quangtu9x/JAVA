package com.td.infrastructure.simpki;

import com.td.application.common.models.Result;
import com.td.application.simpki.ConvertDocumentToPdfUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConvertDocumentToPdfUseCaseImpl implements ConvertDocumentToPdfUseCase {

    private final TdSimPkiClient client;

    public ConvertDocumentToPdfUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<byte[]> execute(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            return Result.failure("Thieu noi dung tep can chuyen doi");
        }
        try {
            return Result.success(client.convertToPdf(fileBytes, fileName));
        } catch (TdSimPkiException ex) {
            log.error("Chuyen doi tep sang PDF that bai: fileName={}", fileName, ex);
            return Result.failure(ex.getMessage());
        }
    }
}
