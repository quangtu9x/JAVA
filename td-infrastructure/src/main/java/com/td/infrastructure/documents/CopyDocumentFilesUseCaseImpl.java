package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import com.td.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 27/08/2026 (port "Chuyển văn thư" — {@code DraftToDocumentOut.CopyDraftToDocumentOut()} copy
 * tệp {@code duthao_file} -> {@code vbdi_file} thật): copy tệp của {@code sourceDocumentId} sang
 * {@code targetDocumentId} — tài liệu ĐÍCH nhận bản sao MỚI (id/stored_filename mới — cột
 * {@code stored_filename} có ràng buộc UNIQUE, không thể tái dùng), tài liệu NGUỒN giữ nguyên tệp
 * của nó (không di chuyển/xoá — khớp nguyên tắc "không phá huỷ dữ liệu nguồn" đã áp dụng xuyên suốt
 * cụm file đính kèm dự thảo, xem UploadFileUseCaseImpl javadoc).
 *
 * <p>Copy object MinIO ở TẦNG SERVER (qua {@link MinioService#copyObject}, không tải byte về rồi
 * tải lên lại) — cùng scheme đường dẫn {@code {tenant}/{module}/{documentId}/{storedName}} đã dùng
 * ở {@code UploadFileUseCaseImpl} (module tuỳ chọn, fallback "documents").</p>
 *
 * <p>Lọc theo tên tệp (xem javadoc {@link CopyDocumentFilesRequest#getFilenames()}):
 * {@code request.getFilenames()} rỗng/null = copy TOÀN BỘ tệp của tài liệu nguồn; khác rỗng = CHỈ
 * copy tệp có {@code originalFilename} nằm trong tập đó — đúng cách {@code ChuyenThanhVanBanDiUseCase}
 * dùng để chỉ mang bộ hồ sơ chính thức ({@code tepbanhanh}/{@code tepkemtheo}) sang văn bản đi.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopyDocumentFilesUseCaseImpl implements CopyDocumentFilesUseCase {

    private static final String DEFAULT_MODULE = "documents";

    private final MinioService minioService;
    private final FileMetadataJpaRepository fileMetadataRepo;
    // Bugfix 28/09/2026 (bao loi nguoi dung "vbdi/xuly/chocapkyso chua hien thi tep dinh kem o ngoai
    // danh sach"): danh sach vbdi/vbden doc "files" tu dynamic_fields (xem javadoc VbdiFilesJsonbSync)
    // - truoc day chi Upload/Delete/Rename dong bo, luong copy nay (Chuyen van thu du thao -> vbdi,
    // Cap nhat VB thay the vbden) bo sot nen van ban dich co tep that nhung danh sach trong.
    private final VbdiFilesJsonbSync vbdiFilesJsonbSync;
    private final VbdenFilesJsonbSync vbdenFilesJsonbSync;

    @Override
    public Result<Integer> execute(CopyDocumentFilesRequest request) {
        List<FileMetadataEntity> sourceFiles = fileMetadataRepo.findAllByDocumentId(request.getSourceDocumentId());
        Set<String> filenameFilter = request.getFilenames();
        boolean hasFilter = filenameFilter != null && !filenameFilter.isEmpty();

        int copied = 0;
        for (FileMetadataEntity source : sourceFiles) {
            if (hasFilter && !filenameFilter.contains(source.getOriginalFilename())) {
                continue;
            }
            copyOne(source, request);
            copied++;
        }
        if (copied > 0) {
            vbdiFilesJsonbSync.syncIfVbdiDocument(request.getTargetDocumentId());
            vbdenFilesJsonbSync.syncIfVbdenDocument(request.getTargetDocumentId());
        }
        log.info("Copied {} file(s) from document {} to {}",
                copied, request.getSourceDocumentId(), request.getTargetDocumentId());
        return Result.success(copied);
    }

    private void copyOne(FileMetadataEntity source, CopyDocumentFilesRequest request) {
        UUID newFileId = UUID.randomUUID();
        String extension = source.getFileExtension();
        String storedName = newFileId + (extension == null || extension.isEmpty() ? "" : "." + extension);
        String tenantSegment = safeSegment(TenantContext.getCurrentTenant(), "default");
        String moduleSegment = safeSegment(request.getModule(), DEFAULT_MODULE);
        String objectPath = tenantSegment + "/" + moduleSegment + "/" + request.getTargetDocumentId() + "/" + storedName;

        minioService.copyObject(source.getFilePath(), objectPath);

        FileMetadataEntity copy = FileMetadataEntity.builder()
                .id(newFileId)
                .documentId(request.getTargetDocumentId())
                .processId(null)
                .originalFilename(source.getOriginalFilename())
                .storedFilename(storedName)
                .filePath(objectPath)
                .fileSize(source.getFileSize())
                .contentType(source.getContentType())
                .fileExtension(source.getFileExtension())
                .fileCategory(source.getFileCategory())
                .bucketName(minioService.getBucketName())
                .uploadedBy(source.getUploadedBy())
                .uploadedAt(LocalDateTime.now())
                .isPublic(source.getIsPublic())
                // Bugfix 09/09/2026 (xem javadoc FileMetadataEntity.isShared): day la COPY VAT LY
                // that (minioService.copyObject o tren) - dong moi LUON tu so huu object rieng cua
                // no, KHONG ke thua co "doc chung" cua nguon (du nguon co isShared=true).
                .isShared(Boolean.FALSE)
                .description(source.getDescription())
                .build();

        fileMetadataRepo.save(copy);
    }

    // Cung cach chuan hoa segment nhu UploadFileUseCaseImpl#safeSegment (co chu, khong lowercase).
    private String safeSegment(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
