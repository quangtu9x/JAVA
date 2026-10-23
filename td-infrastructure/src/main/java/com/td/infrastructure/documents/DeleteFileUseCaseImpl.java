package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DeleteFileUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.fileregistry.SharedFileRegistryClient;
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
    private final SharedFileRegistryClient sharedFileRegistry;

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
        // Yeu cau nguoi dung 22/10/2026: ap dung CUNG quy tac cho van ban den noi tinh / truc quoc gia
        // (td-woker VbdenIncomingDocumentWriter nay cung ghi is_shared=TRUE) - object chi bi xoa boi job
        // don dep td-woker (SharedFileGcJob) khi khong con don vi nao tham chieu.
        if (Boolean.TRUE.equals(entity.getIsShared())) {
            return Result.failure(
                "Không thể xóa: tệp này được nhận từ đơn vị khác (văn bản đến hoặc \"Chuyển xử lý\"), "
                    + "dùng chung với các đơn vị nhận khác nên không thể xóa tại đây.");
        }
        if (stillReferencedByOtherUnits(entity)) {
            log.info("Giu object {} khi xoa file {}: con don vi khac dung chung (so dang ky tep dung chung)",
                entity.getFilePath(), request.getFileId());
        } else {
            minioService.removeObject(entity.getFilePath());
        }
        fileMetadataRepo.delete(entity);
        documentCacheService.evictAllListCaches();
        vbdiFilesJsonbSync.syncIfVbdiDocument(request.getDocumentId());
        vbdenFilesJsonbSync.syncIfVbdenDocument(request.getDocumentId());
        vbdiEditHistorySync.logFileRemoved(request.getDocumentId(), entity.getOriginalFilename());
        log.info("Deleted file {} from document {}", request.getFileId(), request.getDocumentId());
        return Result.success(request.getFileId());
    }

    /**
     * Dong chu so huu (is_shared=FALSE) nhung object da duoc "Chuyen xu ly" sang don vi khac (doc chung):
     * chi xoa dong cua minh, giu object cho don vi kia. So dang ky loi -> GIU object (thieu don dep con
     * hon xoa nham tep don vi khac dang dung).
     */
    private boolean stillReferencedByOtherUnits(FileMetadataEntity entity) {
        if (!sharedFileRegistry.isEnabled()) {
            return false;
        }
        String bucket = entity.getBucketName() == null || entity.getBucketName().isBlank()
            ? minioService.getBucketName() : entity.getBucketName();
        try {
            return sharedFileRegistry.hasActiveRefs(bucket, entity.getFilePath());
        } catch (RuntimeException ex) {
            log.error("Khong tra duoc so dang ky tep dung chung cho object {} - giu object, chi xoa dong file_metadata",
                entity.getFilePath(), ex);
            return true;
        }
    }
}
