package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.documents.FileDto;
import com.td.application.identityfiles.ListIdentityFilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Đọc lại 2 ảnh chữ ký hiện hành (nếu có) của 1 người dùng — xem javadoc UploadUserSignatureImageUseCase
// cho cách tính documentId "ảo". Trả kèm documentId để FE gọi endpoint download/xem/xoá tệp identity
// (api/v1/identity-documents/{documentId}/files/{fileId}/... — xem IdentityDocumentsController,
// KHÁC endpoint /api/v1/documents/... tenant-scoped cũ, xem javadoc IdentityFileMetadata).
@Service
@RequiredArgsConstructor
public class GetUserSignatureImagesUseCase {

    private final AppUserRepository appUserRepository;
    private final ListIdentityFilesUseCase listIdentityFilesUseCase;

    public Result<UserSignatureImagesDto> execute(UUID userId) {
        if (userId == null) {
            return Result.failure("Thiếu ID người dùng");
        }
        if (appUserRepository.findByIdAndDeletedOnIsNull(userId).isEmpty()) {
            return Result.failure("Không tìm thấy người dùng với ID: " + userId);
        }

        var dto = UserSignatureImagesDto.builder()
            .official(findCurrent(userId, UserSignatureImageType.OFFICIAL))
            .draft(findCurrent(userId, UserSignatureImageType.DRAFT))
            .build();
        return Result.success(dto);
    }

    private UserSignatureImageDto findCurrent(UUID userId, UserSignatureImageType type) {
        UUID documentId = UserSignatureImageDocumentIdResolver.resolve(userId, type);
        List<FileDto> files = listIdentityFilesUseCase.execute(documentId).getItems();
        if (files == null || files.isEmpty()) {
            return null;
        }
        // Nhiều nhất 1 file is_primary=true tại 1 thời điểm cho 1 documentId (bất biến của
        // UploadFileUseCaseImpl#demoteExistingPrimary) — phòng hờ dữ liệu cũ/lỗi thì lấy tệp upload
        // gần nhất trong số các tệp is_primary=true.
        return files.stream()
            .filter(f -> Boolean.TRUE.equals(f.getIsPrimary()))
            .max(Comparator.comparing(FileDto::getUploadDate))
            .map(f -> UserSignatureImageDto.builder()
                .documentId(documentId)
                .fileId(f.getFileId())
                .fileName(f.getOriginalFileName() != null ? f.getOriginalFileName() : f.getFileName())
                .fileSize(f.getFileSize())
                .uploadedAt(f.getUploadDate())
                .build())
            .orElse(null);
    }
}
