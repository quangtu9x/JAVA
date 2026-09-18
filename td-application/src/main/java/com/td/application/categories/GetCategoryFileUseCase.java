package com.td.application.categories;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

public interface GetCategoryFileUseCase extends UseCase<DownloadCategoryFileRequest, Result<CategoryFileDto>> {
}
