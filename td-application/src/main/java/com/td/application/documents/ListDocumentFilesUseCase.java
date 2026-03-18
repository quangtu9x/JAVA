package com.td.application.documents;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.PaginationResponse;

import java.util.UUID;

public interface ListDocumentFilesUseCase extends UseCase<UUID, PaginationResponse<FileDto>> {
}
