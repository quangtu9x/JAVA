package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DeleteFileUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteFileUseCaseImpl implements DeleteFileUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;
    private final MinioService minioService;
    private final DocumentCacheService documentCacheService;
    // Yeu cau nguoi dung 28/08/2026 ("luu ca files vao JSONB de tim kiem khong phai goi nguoc vao
    // database") - xem javadoc VbdiFilesJsonbSync.
    private final VbdiFilesJsonbSync vbdiFilesJsonbSync;
    // Yeu cau nguoi dung 04/09/2026 (cung ly do, ap dung them cho vbden) - xem javadoc VbdenFilesJsonbSync.
    private final VbdenFilesJsonbSync vbdenFilesJsonbSync;
    // Yeu cau nguoi dung 28/08/2026 ("ghi log chinh sua... luu ca nhat ky them, xoa dinh kem") -
    // xem javadoc VbdiEditHistorySync.
    private final VbdiEditHistorySync vbdiEditHistorySync;

    @Override
    public Result<UUID> execute(DeleteFileRequest request) {
        Optional<FileMetadataEntity> opt = fileMetadataRepo
                .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        FileMetadataEntity entity = opt.get();
        // Yeu cau nguoi dung 08/09/2026 ("van ban nhan duoc bang chuc nang 'chuyen xu ly' thi khong
        // cho xoa dinh kem goc"): dong file_metadata co is_shared=TRUE (vd ban sao
        // vbden.forward_unit, xem VbdenForwardUnitTaskHandler#insertFileMetadataRefs phia td-woker)
        // CHI tham chieu object MinIO cua van ban goc o tenant KHAC - xoa o day se xoa that object
        // vat ly (minioService.removeObject ben duoi khong dieu kien), lam mat tep cua van ban goc.
        // Chan HAN TOAN (khong chi bo qua buoc xoa vat ly) - dung yeu cau "khong cho xoa".
        if (Boolean.TRUE.equals(entity.getIsShared())) {
            return Result.failure(
                "Không thể xóa: tệp này được nhận qua chức năng \"Chuyển xử lý\" từ đơn vị khác, "
                    + "dùng chung với văn bản gốc nên không thể xóa tại đây.");
        }
        minioService.removeObject(entity.getFilePath());
        fileMetadataRepo.delete(entity);
        documentCacheService.evictAllListCaches();
        vbdiFilesJsonbSync.syncIfVbdiDocument(request.getDocumentId());
        vbdenFilesJsonbSync.syncIfVbdenDocument(request.getDocumentId());
        vbdiEditHistorySync.logFileRemoved(request.getDocumentId(), entity.getOriginalFilename());
        log.info("Deleted file {} from document {}", request.getFileId(), request.getDocumentId());
        return Result.success(request.getFileId());
    }
}
