package com.td.application.categories;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.PaginationResponse;

import java.util.UUID;

public interface ListCategoryFilesUseCase extends UseCase<UUID, PaginationResponse<CategoryFileDto>> {
}
