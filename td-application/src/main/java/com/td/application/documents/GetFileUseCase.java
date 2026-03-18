package com.td.application.documents;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

public interface GetFileUseCase extends UseCase<DownloadFileRequest, Result<FileDto>> {
}
