package com.td.application.categories;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

import java.util.UUID;

public interface DeleteCategoryFileUseCase extends UseCase<DeleteCategoryFileRequest, Result<UUID>> {
}
