package com.td.application.onlyoffice;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;

/** Lưu lại tệp đã chỉnh sửa trực tuyến khi Document Server OnlyOffice gọi callback (status 2/6). */
public interface SaveOnlyOfficeCallbackUseCase extends UseCase<SaveOnlyOfficeCallbackRequest, Result<Void>> {
}
