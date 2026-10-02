package com.td.application.identityfiles;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;
import com.td.application.documents.DownloadFileRequest;
import com.td.application.documents.FileDto;

// "Identity" tương đương GetFileUseCase - tra 1 tệp theo identity_file_metadata (không theo tenant).
// Tái dùng NGUYÊN DownloadFileRequest/FileDto (generic) - xem javadoc IdentityFileMetadata.
public interface GetIdentityFileUseCase extends UseCase<DownloadFileRequest, Result<FileDto>> {
}
