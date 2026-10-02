package com.td.infrastructure.onlyoffice;

import com.td.application.common.models.Result;
import com.td.application.onlyoffice.SaveOnlyOfficeCallbackRequest;
import com.td.application.onlyoffice.SaveOnlyOfficeCallbackUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Đổi hành vi 25/09/2026 (xem javadoc {@link SaveOnlyOfficeCallbackRequest}): lưu bản sửa thành TỆP MỚI,
 * KHÔNG ghi đè tệp gốc.
 * <ul>
 *   <li>Lần lưu ĐẦU của 1 phiên soạn thảo ({@code editSessionKey} chưa có tệp nào): tạo dòng
 *   {@code file_metadata} mới — cùng {@code document_id}, cùng thư mục MinIO với tệp gốc, tên hiển thị
 *   đặt giống luồng "Sửa tệp" BTE ({@code tdeditorJavaFile.js#buildEditedFileName}):
 *   {@code <tên tệp gốc ban đầu, đầy đủ>_<username>_<ddMMyyyyHHmmss>.<đuôi>} (xem buildEditedFileName);
 *   {@code source_file_id} = tệp gốc.</li>
 *   <li>Các lần lưu SAU trong cùng phiên (Document Server force-save status 6 rồi status 2 khi đóng):
 *   ghi đè đúng tệp MỚI của phiên đó (không tạo thêm tệp trùng lặp).</li>
 * </ul>
 * Tệp gốc không bị đụng tới — vẫn tải/xem được, lịch sử và chữ ký (nếu có) trên tệp gốc giữ nguyên.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaveOnlyOfficeCallbackUseCaseImpl implements SaveOnlyOfficeCallbackUseCase {

    private static final DateTimeFormatter EDITED_NAME_TIME = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");

    private final FileMetadataJpaRepository fileMetadataRepository;
    private final MinioService minioService;

    @Override
    public Result<Void> execute(SaveOnlyOfficeCallbackRequest request) {
        Optional<FileMetadataEntity> sourceOpt = fileMetadataRepository
                .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (sourceOpt.isEmpty()) {
            return Result.failure("File không tồn tại (documentId=" + request.getDocumentId()
                    + ", fileId=" + request.getFileId() + ")");
        }
        FileMetadataEntity source = sourceOpt.get();
        String sessionKey = request.getEditSessionKey() == null || request.getEditSessionKey().isBlank()
                ? null : request.getEditSessionKey().trim();

        Optional<FileMetadataEntity> sessionFile = sessionKey == null ? Optional.empty()
                : fileMetadataRepository.findFirstByDocumentIdAndSourceFileIdAndEditSessionKey(
                        source.getDocumentId(), source.getId(), sessionKey);

        FileMetadataEntity target = sessionFile.orElseGet(() -> newEditedFile(source, sessionKey, request.getUsername()));
        try {
            minioService.uploadObject(target.getFilePath(), request.getContent(), request.getContentLength(),
                    target.getContentType());
        } catch (Exception ex) {
            log.error("Luu tep OnlyOffice callback vao MinIO that bai (sourceFileId={}, targetFileId={})",
                    source.getId(), target.getId(), ex);
            return Result.failure("Không thể lưu tệp vào MinIO: " + ex.getMessage());
        }

        target.setFileSize(request.getContentLength());
        target.setUploadedAt(LocalDateTime.now());
        fileMetadataRepository.save(target);
        log.info("OnlyOffice: da luu ban sua cua tep {} thanh tep {} ({}, phien {})",
                source.getId(), target.getId(), sessionFile.isPresent() ? "ghi tiep" : "tep moi", sessionKey);
        return Result.success(null);
    }

    private FileMetadataEntity newEditedFile(FileMetadataEntity source, String sessionKey, String username) {
        UUID newId = UUID.randomUUID();
        String extension = source.getFileExtension();
        String storedName = newId + (extension == null || extension.isBlank() ? "" : "." + extension);
        // Cùng thư mục MinIO với tệp gốc ({tenant}/{module}/{documentId}/) — chỉ đổi tên object.
        String sourcePath = source.getFilePath() == null ? "" : source.getFilePath();
        int slash = sourcePath.lastIndexOf('/');
        String objectPath = (slash >= 0 ? sourcePath.substring(0, slash + 1) : "") + storedName;

        return FileMetadataEntity.builder()
                .id(newId)
                .documentId(source.getDocumentId())
                .processId(null)
                .originalFilename(buildEditedFileName(resolveRootOriginalName(source), username))
                .storedFilename(storedName)
                .filePath(objectPath)
                .fileSize(0L)
                .contentType(source.getContentType())
                .fileExtension(extension)
                .fileCategory(source.getFileCategory())
                .bucketName(minioService.getBucketName())
                .uploadedAt(LocalDateTime.now())
                .isPublic(source.getIsPublic())
                .isShared(Boolean.FALSE)
                .isPrimary(Boolean.FALSE)
                .description("Sửa trực tuyến (OnlyOffice) từ tệp \"" + source.getOriginalFilename() + "\"")
                .sourceFileId(source.getId())
                .editSessionKey(sessionKey)
                .build();
    }

    /**
     * Tên tệp GỐC ban đầu: nếu tệp đang sửa bản thân đã là 1 bản sửa (có {@code source_file_id}), lần
     * ngược về tệp gốc đầu tiên — để tên tệp mới không bị chồng nhiều hậu tố "_người_ngàygiờ" qua các lần
     * sửa liên tiếp. Chặn vòng lặp/độ sâu đề phòng dữ liệu lỗi; không tìm được thì dùng tên tệp đang sửa.
     */
    private String resolveRootOriginalName(FileMetadataEntity source) {
        FileMetadataEntity current = source;
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        while (current.getSourceFileId() != null && visited.add(current.getId()) && visited.size() <= 50) {
            Optional<FileMetadataEntity> parent = fileMetadataRepository.findById(current.getSourceFileId());
            if (parent.isEmpty()) {
                break;
            }
            current = parent.get();
        }
        return current.getOriginalFilename() != null ? current.getOriginalFilename() : source.getOriginalFilename();
    }

    /**
     * {@code <tên gốc ĐẦY ĐỦ>_<username>_<ddMMyyyyHHmmss>.<đuôi>}.
     *
     * <p>Bugfix 25/09/2026 (báo lỗi người dùng "tên tệp mới bị cắt mất, không giống tên gốc"): bản đầu làm
     * y hệt BTE ({@code tdeditorJavaFile.js#buildEditedFileName}) — CẮT tên tại dấu "_" ĐẦU TIÊN (ý đồ: bỏ
     * hậu tố của lần sửa trước), khiến tên gốc có "_" như "phan_cong_ct_..._final.docx" chỉ còn "phan".
     * Nay GIỮ NGUYÊN tên gốc; việc tránh chồng hậu tố do {@link #resolveRootOriginalName} lo (lần ngược về
     * tệp gốc qua source_file_id — không đoán hậu tố bằng regex vì username cũng có thể chứa "_").</p>
     */
    static String buildEditedFileName(String originalName, String username) {
        String name = originalName == null || originalName.isBlank() ? "tep" : originalName;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String user = username == null || username.isBlank() ? "user" : username;
        return base + "_" + user + "_" + LocalDateTime.now().format(EDITED_NAME_TIME) + ext;
    }
}
