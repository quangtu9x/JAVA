package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadFileUseCaseImpl implements UploadFileUseCase {

    @Override
    public Result<UUID> execute(UploadFileRequest request) {
        // TODO: Implement file upload logic
        // 1. Validate file size and type
        // 2. Store file in MinIO/S3/local storage
        // 3. Calculate checksum
        // 4. Save file metadata to database
        // 5. Return file ID
        return Result.success(UUID.randomUUID());
    }
}
