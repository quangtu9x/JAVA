package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.RenameFileRequest;
import com.td.application.documents.RenameFileUseCase;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Gap "đính kèm" 28/08/2026 ("port luôn gap còn lại đi" — sau khi phát hiện tệp tải lên qua Java/
 * MinIO không đổi tên được vì FE chỉ gọi Domino {@code qlvb/renamefile}, không biết gì về
 * {@code file_metadata}/MinIO).
 *
 * Đổi tên KHÔNG di chuyển object trong MinIO — {@code stored_filename} (khoá vật lý trên MinIO,
 * gắn theo {@code fileId}) giữ nguyên, chỉ cập nhật {@code original_filename} (tên hiển thị cho
 * người dùng) + suy lại {@code file_extension} theo tên mới (đề phòng người dùng gõ nhầm đuôi khác
 * — dù FE hiện tại luôn giữ nguyên đuôi cũ).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenameFileUseCaseImpl implements RenameFileUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;
    private final DocumentCacheService documentCacheService;
    // Yeu cau nguoi dung 28/08/2026 ("luu ca files vao JSONB de tim kiem khong phai goi nguoc vao
    // database") - doi ten cung phai dong bo lai dynamic_fields.files, khong chi upload/xoa.
    private final VbdiFilesJsonbSync vbdiFilesJsonbSync;
    // Yeu cau nguoi dung 04/09/2026 (cung ly do, ap dung them cho vbden) - xem javadoc VbdenFilesJsonbSync.
    private final VbdenFilesJsonbSync vbdenFilesJsonbSync;

    @Override
    public Result<UUID> execute(RenameFileRequest request) {
        Optional<FileMetadataEntity> opt = fileMetadataRepo
                .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }

        String newName = request.getNewFileName() == null ? "" : request.getNewFileName().trim();
        if (newName.isEmpty()) {
            return Result.failure("Tên tệp mới không được để trống");
        }

        FileMetadataEntity entity = opt.get();
        entity.setOriginalFilename(newName);
        entity.setFileExtension(extractExtension(newName));
        fileMetadataRepo.save(entity);
        documentCacheService.evictAllListCaches();
        vbdiFilesJsonbSync.syncIfVbdiDocument(request.getDocumentId());
        vbdenFilesJsonbSync.syncIfVbdenDocument(request.getDocumentId());
        log.info("Renamed file {} of document {} to {}", request.getFileId(), request.getDocumentId(), newName);
        return Result.success(request.getFileId());
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : null;
    }
}
