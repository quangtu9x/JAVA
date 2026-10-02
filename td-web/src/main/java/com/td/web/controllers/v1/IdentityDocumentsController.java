package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DownloadFileRequest;
import com.td.application.documents.FileDto;
import com.td.application.identityfiles.DeleteIdentityFileUseCase;
import com.td.application.identityfiles.GetIdentityFileUseCase;
import com.td.application.identityfiles.ListIdentityFilesUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

// Đọc/xoá tệp lưu qua identity_file_metadata (không theo tenant) - xem javadoc IdentityFileMetadata
// (td-domain, com.td.domain.sharedcore) cho bối cảnh đầy đủ. Mirror ĐÚNG shape 3 endpoint tương ứng
// của DocumentsController (list/download/delete) - CỐ Ý không có endpoint upload chung ở đây: mỗi
// tính năng dùng bảng này (vd UserSignatureImagesController) tự có endpoint upload RIÊNG với validate
// nghiệp vụ của chính nó (mime/size...), chỉ dùng chung UploadIdentityFileUseCase ở tầng use case -
// tránh mở 1 cửa "tải bất kỳ nội dung nào lên bất kỳ documentId nào" không qua validate gì.
//
// Route RIÊNG (/api/v1/identity-documents, KHÁC /api/v1/documents) để tính mở rộng: BẤT KỲ tính năng
// tương lai nào cần tệp đính kèm gắn theo danh tính (không phải theo tenant nghiệp vụ) chỉ cần gọi
// UploadIdentityFileUseCase để ghi, rồi dùng NGUYÊN 3 endpoint đọc/xoá chung này - không cần viết lại.
@RestController
@RequestMapping("/api/v1/identity-documents")
@RequiredArgsConstructor
@Tag(name = "Identity Documents", description = "Tệp đính kèm gắn theo danh tính (không theo tenant) - vd ảnh chữ ký người dùng")
public class IdentityDocumentsController extends BaseController {

    private final ListIdentityFilesUseCase listIdentityFilesUseCase;
    private final GetIdentityFileUseCase getIdentityFileUseCase;
    private final DeleteIdentityFileUseCase deleteIdentityFileUseCase;
    private final MinioService minioService;

    @GetMapping("/{documentId}/files")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tệp đính kèm theo danh tính")
    public ResponseEntity<ApiResponse<List<FileDto>>> listFiles(
            @Parameter(description = "Document ID (ảo, tuỳ tính năng tự tính)", required = true)
            @PathVariable UUID documentId) {
        var response = listIdentityFilesUseCase.execute(documentId);
        return ok(response);
    }

    @GetMapping("/{documentId}/files/{fileId}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tải xuống tệp đính kèm theo danh tính")
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId) {
        var request = DownloadFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .build();
        var fileResult = getIdentityFileUseCase.execute(request);
        if (!fileResult.isSuccess() || fileResult.getData() == null) {
            return badRequest(Result.failure(fileResult.getError() != null ? fileResult.getError() : "File không tồn tại"));
        }

        var file = fileResult.getData();
        try {
            InputStream inputStream = minioService.getObject(file.getStoragePath());
            var mediaType = (file.getMimeType() != null && !file.getMimeType().isBlank())
                    ? MediaType.parseMediaType(file.getMimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;
            var fileName = file.getOriginalFileName() != null ? file.getOriginalFileName() : file.getFileName();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentLength(file.getFileSize() != null ? file.getFileSize() : -1)
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi tải tệp tin: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{documentId}/files/{fileId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xoá tệp đính kèm theo danh tính")
    public ResponseEntity<ApiResponse<UUID>> deleteFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId,
            @RequestParam(value = "reason", required = false) String reason) {
        var request = DeleteFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .reason(reason)
                .build();
        var result = deleteIdentityFileUseCase.execute(request);
        return ok(result);
    }
}
