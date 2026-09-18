package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import com.td.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileUseCaseImpl implements UploadFileUseCase {

    private final MinioService minioService;
    private final FileMetadataJpaRepository fileMetadataRepo;
    private final DocumentCacheService documentCacheService;
    // Yeu cau nguoi dung 28/08/2026 ("luu ca files vao JSONB de tim kiem khong phai goi nguoc vao
    // database") - xem javadoc VbdiFilesJsonbSync.
    private final VbdiFilesJsonbSync vbdiFilesJsonbSync;
    // Yeu cau nguoi dung 04/09/2026 (cung ly do, ap dung them cho vbden) - xem javadoc VbdenFilesJsonbSync.
    private final VbdenFilesJsonbSync vbdenFilesJsonbSync;
    // Yeu cau nguoi dung 28/08/2026 ("ghi log chinh sua... luu ca nhat ky them, xoa dinh kem") -
    // xem javadoc VbdiEditHistorySync.
    private final VbdiEditHistorySync vbdiEditHistorySync;

    private static final String DEFAULT_MODULE = "documents";

    @Override
    public Result<UUID> execute(UploadFileRequest request) {
        UUID fileId = UUID.randomUUID();
        String extension = extractExtension(request.getFileName());
        String storedName = fileId + (extension.isEmpty() ? "" : "." + extension);
        // Bugfix 27/08/2026 (phan hoi nguoi dung: nhieu module dung CHUNG 1 bucket MinIO - tach
        // duong dan theo tenant/module de tranh lan/nham lan giua cac module khi doc/xoa truc tiep
        // qua storage, dong thoi de trace duoc tep thuoc tenant/module nao tu path). tenantId lay
        // tu TenantContext (BL-303, da set san boi TenantFilter cho request nay) - null/rong (vd
        // luong khong qua HTTP filter, hiem gap) fallback "default" de KHONG lam vo upload. module
        // tuy chon o UploadFileRequest, fallback "documents" (khop duong dan cu CHUA co segment nay)
        // de KHONG doi hanh vi cua cac call site chua cap nhat.
        String tenantSegment = safeSegment(TenantContext.getCurrentTenant(), "default");
        String moduleSegment = safeSegment(request.getModule(), DEFAULT_MODULE);
        String objectPath = tenantSegment + "/" + moduleSegment + "/" + request.getDocumentId() + "/" + storedName;

        minioService.uploadObject(objectPath, request.getFileContent(),
                request.getFileSize() != null ? request.getFileSize() : -1,
                request.getMimeType());

        // Yeu cau nguoi dung 15/09/2026 (port ky so vbdi/vbden - "giu file goc, tep ky luu moi, thay
        // the tep chinh thuc"): request.isPrimary() da co san tu lau (UploadFileRequest) nhung CHUA
        // BAO GIO duoc doc o day - KySoVbdiUseCase da goi .isPrimary(true) tu truoc ma khong co hieu
        // luc gi (khong co cot DB, khong demote gi ca). Demote tep primary CU (neu co) TRUOC khi
        // insert tep MOI - dam bao tai 1 thoi diem chi 1 tep is_primary=true cho cung documentId,
        // tep cu KHONG bi xoa (van con nguyen trong danh sach tep, chi khong con la "tep chinh thuc").
        boolean makePrimary = Boolean.TRUE.equals(request.getIsPrimary());
        if (makePrimary) {
            demoteExistingPrimary(request.getDocumentId());
        }

        FileMetadataEntity entity = FileMetadataEntity.builder()
                .id(fileId)
                .documentId(request.getDocumentId())
                .processId(request.getProcessId())
                .originalFilename(request.getFileName())
                .storedFilename(storedName)
                .filePath(objectPath)
                .fileSize(request.getFileSize() != null ? request.getFileSize() : 0L)
                .contentType(request.getMimeType())
                .fileExtension(extension.isEmpty() ? null : extension)
                .fileCategory("DOCUMENT")
                .bucketName(minioService.getBucketName())
                .uploadedAt(LocalDateTime.now())
                .isPublic(false)
                // Bugfix 09/09/2026 (bao loi nguoi dung: upload dinh kem 500 "is_shared" NOT NULL -
                // xem javadoc FileMetadataEntity.isShared): tep upload moi LUON tu so huu object vua
                // tao, khong phai ban sao "doc chung" nao ca.
                .isShared(Boolean.FALSE)
                .isPrimary(makePrimary)
                .description(request.getDescription())
                .build();

        fileMetadataRepo.save(entity);
        documentCacheService.evictAllListCaches();
        vbdiFilesJsonbSync.syncIfVbdiDocument(request.getDocumentId());
        vbdenFilesJsonbSync.syncIfVbdenDocument(request.getDocumentId());
        vbdiEditHistorySync.logFileAdded(request.getDocumentId(), request.getFileName());
        log.info("Uploaded file {} for document {}", fileId, request.getDocumentId());
        return Result.success(fileId);
    }

    // Yeu cau nguoi dung 15/09/2026 - xem javadoc o execute(). demote (KHONG xoa) moi tep dang
    // is_primary=true hien co cho document nay truoc khi insert tep primary MOI.
    private void demoteExistingPrimary(UUID documentId) {
        List<FileMetadataEntity> currentPrimaries = fileMetadataRepo.findAllByDocumentIdAndIsPrimaryTrue(documentId);
        currentPrimaries.forEach(entity -> entity.setIsPrimary(Boolean.FALSE));
        fileMetadataRepo.saveAll(currentPrimaries);
    }

    // Chuan hoa segment duong dan MinIO: rong/null -> fallback; giu nguyen chu (khong lowercase -
    // tenantId/module dang la ma dinh danh ro rang, vd "H05"/"duthao", khong can chuan hoa them).
    private String safeSegment(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
