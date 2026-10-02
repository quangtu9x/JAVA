package com.td.application.identityfiles;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;

import java.util.UUID;

// "Identity" tương đương ListDocumentFilesUseCase - liệt kê theo identity_file_metadata (không theo
// tenant). Tái dùng NGUYÊN FileDto/PaginationResponse (generic) - xem javadoc IdentityFileMetadata.
public interface ListIdentityFilesUseCase extends UseCase<UUID, PaginationResponse<FileDto>> {
}
