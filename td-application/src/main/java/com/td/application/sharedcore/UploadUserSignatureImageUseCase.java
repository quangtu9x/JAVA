package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.documents.UploadFileRequest;
import com.td.application.identityfiles.UploadIdentityFileUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Hoàn thiện "gắn hình ảnh chữ ký" trong hồ sơ người dùng (2 loại — xem javadoc
// UserSignatureImageType) — trước đây bị ẩn có chủ đích ở ChiTietModalNguoiDung.jsx (FE) vì
// "chưa có cột nào trên AppUser" (BL-108), với 1 documentId "ảo" tất định theo (userId, type) — xem
// UserSignatureImageDocumentIdResolver. isPrimary luôn true: mỗi loại chỉ giữ ĐÚNG 1 ảnh hiện hành
// tại 1 thời điểm (ảnh cũ bị demote, không xoá — cùng hành vi ký số vbdi/vbden đã có).
//
// BUG THẬT đã gặp + sửa (22/09/2026): ban đầu tái dùng NGUYÊN hạ tầng file_metadata/MinIO chung
// (UploadFileUseCase, tenant-scoped) — admin cấu hình ảnh cho 1 người dùng lúc tenant CỦA ADMIN
// resolve khác tenant của chính người dùng đó (AppUser vốn KHÔNG nhân bản theo tenant — xem
// ResolveUserTenantUseCase) khiến ảnh "biến mất" khi chính người dùng tự đọc lại (đọc đúng UUID
// nhưng khác tenant DB). Đã chuyển sang UploadIdentityFileUseCase (identity_file_metadata, không
// theo tenant — xem javadoc IdentityFileMetadata) — sửa tận gốc, không phải vá triệu chứng.
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadUserSignatureImageUseCase {

    // Yêu cầu người dùng (26/09/2026: nâng từ 100KB lên 1MB, đồng bộ với
    // UserSignatureImages.jsx#SIGNATURE_IMAGE_MAX_BYTES): validate lại ở BE vì giới hạn FE có thể bị bỏ qua
    // (gọi thẳng API).
    private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

    private final AppUserRepository appUserRepository;
    private final UploadIdentityFileUseCase uploadIdentityFileUseCase;

    public Result<UUID> execute(UploadUserSignatureImageRequest request) {
        if (request.getUserId() == null) {
            return Result.failure("Thiếu ID người dùng");
        }
        if (request.getType() == null) {
            return Result.failure("Thiếu loại ảnh chữ ký (OFFICIAL/DRAFT)");
        }
        if (appUserRepository.findByIdAndDeletedOnIsNull(request.getUserId()).isEmpty()) {
            return Result.failure("Không tìm thấy người dùng với ID: " + request.getUserId());
        }
        if (request.getFileContent() == null || request.getFileSize() == null || request.getFileSize() <= 0) {
            return Result.failure("Tệp ảnh chữ ký không được để trống");
        }
        if (request.getFileSize() > MAX_FILE_SIZE_BYTES) {
            return Result.failure("Ảnh chữ ký chỉ cho phép tối đa 1MB");
        }
        if (request.getMimeType() == null || !request.getMimeType().toLowerCase().startsWith("image/")) {
            return Result.failure("Ảnh chữ ký phải là tệp hình ảnh (jpg, png, ...)");
        }

        UUID documentId = UserSignatureImageDocumentIdResolver.resolve(request.getUserId(), request.getType());
        var uploadRequest = UploadFileRequest.builder()
            .documentId(documentId)
            .fileContent(request.getFileContent())
            .fileName(request.getFileName())
            .mimeType(request.getMimeType())
            .fileSize(request.getFileSize())
            .isPrimary(true)
            .module("user-signature")
            .description(request.getType().name())
            .build();

        var result = uploadIdentityFileUseCase.execute(uploadRequest);
        if (result.isSuccess()) {
            log.info("Uploaded {} signature image for user {}", request.getType(), request.getUserId());
        }
        return result;
    }
}
