package com.td.web.controllers.v1;

import com.td.application.common.models.CachedPaginationResponse;
import com.td.application.common.models.CachedResult;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.CreateDocumentRequest;
import com.td.application.documents.CreateDocumentUseCase;
import com.td.application.documents.DeleteDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentCacheStatsDto;
import com.td.application.documents.DocumentDetailWithFilesDto;
import com.td.application.documents.DocumentDto;
import com.td.application.documents.DocumentXemChiTietDto;
import com.td.application.documents.GetDeletedDocumentsUseCase;
import com.td.application.documents.SimpleFileDto;
import com.td.application.documents.GetDocumentRequest;
import com.td.application.documents.GetDocumentUseCase;
import com.td.application.documents.HardDeleteDocumentUseCase;
import com.td.application.documents.SearchDocumentsRequest;
import com.td.application.documents.SearchDocumentsUseCase;
import com.td.application.documents.UpdateDocumentRequest;
import com.td.application.documents.UpdateDocumentUseCase;
import com.td.application.documents.UploadFileUseCase;
import com.td.application.documents.GetFileUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.DeleteFileUseCase;
import com.td.application.documents.UpdateDocumentWithFileUseCase;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.FileDto;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DownloadFileRequest;
import com.td.infrastructure.config.MinioService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Validated
@Tag(name = "Documents", description = "Quản lý tài liệu linh hoạt")
public class DocumentsController extends BaseController {

    private final CreateDocumentUseCase createDocumentUseCase;
    private final UpdateDocumentUseCase updateDocumentUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;
    private final HardDeleteDocumentUseCase hardDeleteDocumentUseCase;
    private final GetDocumentUseCase getDocumentUseCase;
    private final SearchDocumentsUseCase searchDocumentsUseCase;
    private final GetDeletedDocumentsUseCase getDeletedDocumentsUseCase;
    private final UploadFileUseCase uploadFileUseCase;
    private final GetFileUseCase getFileUseCase;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final DeleteFileUseCase deleteFileUseCase;
    private final UpdateDocumentWithFileUseCase updateDocumentWithFileUseCase;
    private final MinioService minioService;
    private final DocumentCacheService documentCacheService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tài liệu",
        description = "Lấy danh sách tài liệu, mỗi tài liệu có thêm trường files để mở/tải lại file")
    public ResponseEntity<PaginationResponse<DocumentDto>> listDocuments(
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "sortBy", defaultValue = "lastModifiedOn") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "documentType", required = false) String documentType,
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Bật/tắt cache. Khi false, luôn lấy dữ liệu mới từ DB và cập nhật lại cache")
            @RequestParam(name = "useCache", defaultValue = "true") boolean useCache) {
        var request = new SearchDocumentsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setDocumentType(documentType);
        request.setStatus(status);

        if (useCache) {
            var cachedResponse = documentCacheService.getList(request);
            if (cachedResponse != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(new CachedPaginationResponse<>(cachedResponse, documentCacheService.getListCacheKey(request)));
            }
        }

        var response = enrichDocumentsWithFiles(searchDocumentsUseCase.execute(request));
        documentCacheService.putList(request, response);
        return ResponseEntity.ok()
            .header("X-Cache", "MISS")
            .body(response);
    }

    @GetMapping("/cache/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Thống kê cache document",
        description = "Theo dõi hit, miss, put và evict của cache document chi tiết và danh sách")
    public ResponseEntity<Result<DocumentCacheStatsDto>> getDocumentCacheStats() {
        return ok(Result.success(documentCacheService.getStats()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết tài liệu")
        public ResponseEntity<Result<DocumentDto>> getDocument(
                @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id,
                @Parameter(description = "Bật/tắt cache. Khi false, luôn lấy dữ liệu mới từ DB và cập nhật lại cache")
                @RequestParam(name = "useCache", defaultValue = "true") boolean useCache) {
        boolean cached = useCache && documentCacheService.isCachedById(id);
        if (!useCache) {
            documentCacheService.evict(id);
        }
        var result = getDocumentUseCase.execute(new GetDocumentRequest(id));
        return ResponseEntity.ok()
            .header("X-Cache", cached ? "HIT" : "MISS")
            .body(cached ? new CachedResult<>(result, documentCacheService.getDocumentCacheKey(id)) : result);
    }

            @GetMapping("/{id}/with-files")
            @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
            @Operation(summary = "Xem chi tiết tài liệu kèm danh sách tệp tin",
                description = "Lấy chi tiết một tài liệu và toàn bộ tệp tin gắn với tài liệu đó trong một API")
            public ResponseEntity<Result<DocumentDetailWithFilesDto>> getDocumentWithFiles(
                @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id) {
            var documentResult = getDocumentUseCase.execute(new GetDocumentRequest(id));
            if (!documentResult.isSuccess() || documentResult.getData() == null) {
                return ok(Result.failure(documentResult.getError() != null
                    ? documentResult.getError()
                    : "Không tìm thấy tài liệu"));
            }

            var filesResponse = listDocumentFilesUseCase.execute(id);
            var payload = DocumentDetailWithFilesDto.builder()
                .document(documentResult.getData())
                .files(filesResponse != null && filesResponse.getItems() != null
                    ? filesResponse.getItems()
                    : Collections.emptyList())
                .build();

            return ok(Result.success(payload));
            }

    @GetMapping("/{id}/xem-chi-tiet")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết tài liệu kèm danh sách tệp tin (dạng đơn giản)",
        description = "Lấy chi tiết tài liệu và danh sách tệp tin để hiển thị nhanh và mở/tải lại file")
    public ResponseEntity<Result<DocumentXemChiTietDto>> getDocumentXemChiTiet(
            @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id) {
        var documentResult = getDocumentUseCase.execute(new GetDocumentRequest(id));
        if (!documentResult.isSuccess() || documentResult.getData() == null) {
            return ok(Result.failure(documentResult.getError() != null
                ? documentResult.getError()
                : "Không tìm thấy tài liệu"));
        }

        var filesResponse = listDocumentFilesUseCase.execute(id);
        List<SimpleFileDto> simpleFiles = mapSimpleFiles(filesResponse != null ? filesResponse.getItems() : null);

        var payload = DocumentXemChiTietDto.builder()
            .document(documentResult.getData())
            .files(simpleFiles)
            .build();

        return ok(Result.success(payload));
    }

    private String extractFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private String buildDownloadUrl(UUID documentId, UUID fileId) {
        if (documentId == null || fileId == null) {
            return null;
        }
        return "/api/v1/documents/" + documentId + "/files/" + fileId + "/download";
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm tài liệu theo bộ lọc",
        description = "Trả về danh sách tài liệu theo bộ lọc, mỗi tài liệu có thêm trường files")
    public ResponseEntity<PaginationResponse<DocumentDto>> searchDocuments(
            @Valid @RequestBody SearchDocumentsRequest request,
            @Parameter(description = "Bật/tắt cache. Khi false, luôn lấy dữ liệu mới từ DB và cập nhật lại cache")
            @RequestParam(name = "useCache", defaultValue = "true") boolean useCache) {
        if (useCache) {
            var cachedResponse = documentCacheService.getList(request);
            if (cachedResponse != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(new CachedPaginationResponse<>(cachedResponse, documentCacheService.getListCacheKey(request)));
            }
        }

        var response = enrichDocumentsWithFiles(searchDocumentsUseCase.execute(request));
        documentCacheService.putList(request, response);
        return ResponseEntity.ok()
            .header("X-Cache", "MISS")
            .body(response);
    }

    private PaginationResponse<DocumentDto> enrichDocumentsWithFiles(PaginationResponse<DocumentDto> response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return response;
        }

        List<DocumentDto> enrichedItems = response.getItems().stream()
            .map(this::attachFilesToDocument)
            .collect(java.util.stream.Collectors.toList());

        return new PaginationResponse<>(
            enrichedItems,
            response.getPageNumber(),
            response.getPageSize(),
            response.getTotalItems(),
            response.getTotalPages(),
            response.isFirst(),
            response.isLast()
        );
    }

    private DocumentDto attachFilesToDocument(DocumentDto document) {
        if (document == null || document.getId() == null) {
            return document;
        }

        var filesResponse = listDocumentFilesUseCase.execute(document.getId());
        List<SimpleFileDto> simpleFiles = mapSimpleFiles(filesResponse != null ? filesResponse.getItems() : null);

        document.setFiles(simpleFiles);
        return document;
    }

    private List<SimpleFileDto> mapSimpleFiles(List<FileDto> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        return files.stream()
            .filter(java.util.Objects::nonNull)
            .map(file -> {
                String fileName = file.getOriginalFileName() != null
                    ? file.getOriginalFileName()
                    : file.getFileName();

                return SimpleFileDto.builder()
                    .fileId(file.getFileId())
                    .documentId(file.getDocumentId())
                    .name(fileName)
                    .size(file.getFileSize())
                    .type(extractFileExtension(fileName))
                    .mimeType(file.getMimeType())
                    .downloadUrl(buildDownloadUrl(file.getDocumentId(), file.getFileId()))
                    .build();
            })
            .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo tài liệu mới")
    public ResponseEntity<Result<UUID>> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        var result = createDocumentUseCase.execute(request);
        return created(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật tài liệu")
    public ResponseEntity<Result<UUID>> updateDocument(
                @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        if (request.getId() != null && !id.equals(request.getId())) {
            return badRequest(Result.<UUID>failure("ID tài liệu không khớp"));
        }

        request.setId(id);

        var result = updateDocumentUseCase.execute(request);
        return ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa tài liệu (soft delete)")
    public ResponseEntity<Result<UUID>> deleteDocument(
            @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id) {
        var result = deleteDocumentUseCase.execute(id);
        return ok(result);
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa vĩnh viễn tài liệu",
               description = "Xóa hoàn toàn tài liệu khỏi database, không thể khôi phục. Chỉ dành cho Admin")
    public ResponseEntity<Result<UUID>> hardDeleteDocument(
            @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id) {
        var result = hardDeleteDocumentUseCase.execute(id);
        return ok(result);
    }

    @PostMapping("/deleted/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tài liệu đã xóa",
               description = "Lấy danh sách tài liệu đã bị soft delete (chưa hoạt động)")
    public ResponseEntity<PaginationResponse<DocumentDto>> searchDeletedDocuments(
            @Valid @RequestBody SearchDocumentsRequest request) {
        var response = getDeletedDocumentsUseCase.execute(request);
        return ok(response);
    }

    // ================= FILE MANAGEMENT ENDPOINTS =================

    @PostMapping(value = "/{documentId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'DOC_EDITOR')")
    @Operation(summary = "Tải lên tệp tin cho tài liệu",
            description = "Tải lên một tệp tin mới cho tài liệu, hỗ trợ đánh dấu tệp chính")
    public ResponseEntity<Result<UUID>> uploadFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "Tệp tin cần tải lên", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Đánh dấu là tệp chính") @RequestParam(value = "isPrimary", defaultValue = "false") Boolean isPrimary,
            @Parameter(description = "Mô tả tệp tin") @RequestParam(value = "description", required = false) String description) {
        try {
            var request = UploadFileRequest.builder()
                    .documentId(documentId)
                    .fileContent(file.getInputStream())
                    .fileName(file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .isPrimary(isPrimary)
                    .description(description)
                    .build();
            var result = uploadFileUseCase.execute(request);
            return created(result);
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi xử lý tệp tin: " + e.getMessage()));
        }
    }

    @GetMapping("/{documentId}/files")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tệp tin của tài liệu",
            description = "Lấy danh sách tất cả tệp tin được tải lên cho tài liệu")
    public ResponseEntity<PaginationResponse<FileDto>> listDocumentFiles(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        var response = listDocumentFilesUseCase.execute(documentId);
        return ok(response);
    }

    @GetMapping("/{documentId}/files/{fileId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết tệp tin",
            description = "Lấy thông tin chi tiết của một tệp tin")
    public ResponseEntity<Result<FileDto>> getFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId) {
        var request = DownloadFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .build();
        var result = getFileUseCase.execute(request);
        return ok(result);
    }

    @DeleteMapping("/{documentId}/files/{fileId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'DOC_EDITOR')")
    @Operation(summary = "Xóa tệp tin",
            description = "Xóa một tệp tin khỏi tài liệu")
    public ResponseEntity<Result<UUID>> deleteFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId,
            @Parameter(description = "Lý do xóa") @RequestParam(value = "reason", required = false) String reason) {
        var request = DeleteFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .reason(reason)
                .build();
        var result = deleteFileUseCase.execute(request);
        return ok(result);
    }

    @PutMapping(value = "/{documentId}/with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'DOC_EDITOR')")
    @Operation(summary = "Cập nhật tài liệu với tệp tin",
            description = "Cập nhật thông tin tài liệu đồng thời tải lên tệp tin mới qua Form Data")
    public ResponseEntity<Result<UUID>> updateDocumentWithFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "Tiêu đề tài liệu") @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "Loại tài liệu") @RequestParam(value = "documentType", required = false) String documentType,
            @Parameter(description = "Trạng thái tài liệu") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Nội dung") @RequestParam(value = "content", required = false) String content,
            @Parameter(description = "Tệp tin", required = false,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Đánh dấu tệp là chính") @RequestParam(value = "isPrimaryFile", defaultValue = "false") Boolean isPrimaryFile,
            @Parameter(description = "Mô tả tệp tin") @RequestParam(value = "fileDescription", required = false) String fileDescription) {
        try {
            InputStream fileContent = null;
            String fileName = null;
            String mimeType = null;
            Long fileSize = null;

            if (file != null && !file.isEmpty()) {
                fileContent = file.getInputStream();
                fileName = file.getOriginalFilename();
                mimeType = file.getContentType();
                fileSize = file.getSize();
            }

            var request = com.td.application.documents.UpdateDocumentWithFileRequest.builder()
                    .documentId(documentId)
                    .title(title)
                    .documentType(documentType)
                    .status(status)
                    .content(content)
                    .fileContent(fileContent)
                    .fileName(fileName)
                    .mimeType(mimeType)
                    .fileSize(fileSize)
                    .isPrimaryFile(isPrimaryFile)
                    .fileDescription(fileDescription)
                    .build();
            var result = updateDocumentWithFileUseCase.execute(request);
            return ok(result);
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi xử lý tệp tin: " + e.getMessage()));
        }
    }

    @GetMapping("/{documentId}/files/{fileId}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tải xuống tệp tin",
            description = "Tải xuống tệp tin từ máy chủ")
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "Document ID", required = true) @PathVariable UUID documentId,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId) {
        var request = DownloadFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .build();
        var fileResult = getFileUseCase.execute(request);
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
}
