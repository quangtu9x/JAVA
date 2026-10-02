package com.td.application.identityfiles;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;
import com.td.application.documents.DeleteFileRequest;

import java.util.UUID;

// "Identity" tương đương DeleteFileUseCase - xoá 1 tệp khỏi identity_file_metadata (không theo
// tenant). Tái dùng NGUYÊN DeleteFileRequest (generic) - xem javadoc IdentityFileMetadata.
public interface DeleteIdentityFileUseCase extends UseCase<DeleteFileRequest, Result<UUID>> {
}
