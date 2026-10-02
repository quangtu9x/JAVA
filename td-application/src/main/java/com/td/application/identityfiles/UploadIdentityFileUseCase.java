package com.td.application.identityfiles;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.Result;
import com.td.application.documents.UploadFileRequest;

import java.util.UUID;

// "Identity" tương đương UploadFileUseCase (com.td.application.documents) - lưu qua
// identity_file_metadata (không theo tenant) thay vì file_metadata. Tái dùng NGUYÊN UploadFileRequest
// (generic, không có field tenant-scoped nào) - xem javadoc IdentityFileMetadata cho lý do tồn tại.
public interface UploadIdentityFileUseCase extends UseCase<UploadFileRequest, Result<UUID>> {
}
