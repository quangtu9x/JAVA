package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.DeleteFileUseCase;
import com.td.application.documents.DeleteFileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteFileUseCaseImpl implements DeleteFileUseCase {

    @Override
    public Result<UUID> execute(DeleteFileRequest request) {
        // TODO: Implement delete file logic
        // 1. Validate document and file exist
        // 2. Check permissions
        // 3. Delete file from storage
        // 4. Soft delete file record from database
        // 5. Record audit event
        return Result.success(request.getFileId());
    }
}
