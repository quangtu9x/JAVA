package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DeleteFileUseCase;
import com.td.application.documents.DownloadFileRequest;
import com.td.application.documents.FileResponse;
import com.td.application.documents.GetFileUseCase;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Upload, Download, View inline và Presigned URL cho file tài liệu")
public class FileController extends BaseController {

    private final UploadFileUseCase uploadFileUseCase;
    private final GetFileUseCase getFileUseCase;
    private final DeleteFileUseCase deleteFileUseCase;
    private final MinioService minioService;

    @PostMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Upload file", description = "Streaming 100%: InputStream đẩy thẳng sang MinIO")
    public ResponseEntity<ApiResponse<UUID>> upload(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        try {
            var request = UploadFileRequest.builder()
                    .documentId(documentId)
                    .fileName(file.getOriginalFilename())
                    .fileContent(file.getInputStream())
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .description(description)
                    .build();
            return created(uploadFileUseCase.execute(request));
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi upload: " + e.getMessage()));
        }
    }

    @GetMapping("/{documentId}/{fileId}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Download file", description = "Tải file dạng attachment")
    public ResponseEntity<?> download(
            @PathVariable UUID documentId,
            @PathVariable UUID fileId) {
        return streamFile(documentId, fileId, "attachment");
    }

    @GetMapping("/{documentId}/{fileId}/view")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "View inline", description = "Stream file dạng inline để xem trực tiếp")
    public ResponseEntity<?> viewInline(
            @PathVariable UUID documentId,
            @PathVariable UUID fileId) {
        return streamFile(documentId, fileId, "inline");
    }

    @GetMapping("/{documentId}/{fileId}/presigned-url")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get Presigned URL", description = "Lấy URL tạm thời để tải trực tiếp từ MinIO")
    public ResponseEntity<ApiResponse<FileResponse>> presignedUrl(
            @PathVariable UUID documentId,
            @PathVariable UUID fileId) {
        var fileResult = getFileUseCase.execute(
                DownloadFileRequest.builder().documentId(documentId).fileId(fileId).build());
        if (!fileResult.isSuccess() || fileResult.getData() == null) {
            return badRequest(Result.failure(fileResult.getError() != null ? fileResult.getError() : "File không tồn tại"));
        }

        var file = fileResult.getData();
        String url = minioService.getPresignedUrl(file.getStoragePath());
        var response = FileResponse.builder()
                .fileId(file.getFileId())
                .documentId(file.getDocumentId())
                .originalFileName(file.getOriginalFileName())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .uploadDate(file.getUploadDate())
                .presignedUrl(url)
                .presignedExpiresInSeconds(minioService.getPresignedUrlExpiry())
                .build();

        return ok(Result.success(response));
    }

    @DeleteMapping("/{documentId}/{fileId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Xóa file")
    public ResponseEntity<ApiResponse<UUID>> delete(
            @PathVariable UUID documentId,
            @PathVariable UUID fileId) {
        return ok(deleteFileUseCase.execute(
                DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build()));
    }

    private ResponseEntity<?> streamFile(UUID documentId, UUID fileId, String disposition) {
        var fileResult = getFileUseCase.execute(
                DownloadFileRequest.builder().documentId(documentId).fileId(fileId).build());
        if (!fileResult.isSuccess() || fileResult.getData() == null) {
            return badRequest(Result.failure(
                    fileResult.getError() != null ? fileResult.getError() : "File không tồn tại"));
        }

        var file = fileResult.getData();
        try {
            InputStream inputStream = minioService.getObject(file.getStoragePath());
            String rawName = file.getOriginalFileName() != null ? file.getOriginalFileName() : file.getFileName();
            String encoded = URLEncoder.encode(rawName, StandardCharsets.UTF_8).replace("+", "%20");
            String contentDisposition = disposition + "; filename=\"" + rawName + "\"; filename*=UTF-8''" + encoded;

            var mediaType = (file.getMimeType() != null && !file.getMimeType().isBlank())
                    ? MediaType.parseMediaType(file.getMimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentLength(file.getFileSize() != null ? file.getFileSize() : -1)
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi đọc file: " + e.getMessage()));
        }
    }
}