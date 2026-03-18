package com.td.application.documents;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

import java.util.UUID;

public interface UpdateDocumentWithFileUseCase extends UseCase<UpdateDocumentWithFileRequest, Result<UUID>> {
}
