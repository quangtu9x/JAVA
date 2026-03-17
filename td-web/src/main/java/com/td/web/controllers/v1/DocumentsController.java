package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.CreateDocumentRequest;
import com.td.application.documents.CreateDocumentUseCase;
import com.td.application.documents.DeleteDocumentUseCase;
import com.td.application.documents.DocumentDto;
import com.td.application.documents.GetDeletedDocumentsUseCase;
import com.td.application.documents.GetDocumentRequest;
import com.td.application.documents.GetDocumentUseCase;
import com.td.application.documents.HardDeleteDocumentUseCase;
import com.td.application.documents.SearchDocumentsRequest;
import com.td.application.documents.SearchDocumentsUseCase;
import com.td.application.documents.UpdateDocumentRequest;
import com.td.application.documents.UpdateDocumentUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tài liệu")
    public ResponseEntity<PaginationResponse<DocumentDto>> listDocuments(
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "sortBy", defaultValue = "lastModifiedOn") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "documentType", required = false) String documentType,
            @RequestParam(name = "status", required = false) String status) {
        var request = new SearchDocumentsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setDocumentType(documentType);
        request.setStatus(status);

        var response = searchDocumentsUseCase.execute(request);
        return ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết tài liệu")
        public ResponseEntity<Result<DocumentDto>> getDocument(
                @Parameter(description = "Document ID", required = true) @PathVariable("id") UUID id) {
        var result = getDocumentUseCase.execute(new GetDocumentRequest(id));
        return ok(result);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm tài liệu theo bộ lọc")
    public ResponseEntity<PaginationResponse<DocumentDto>> searchDocuments(
            @Valid @RequestBody SearchDocumentsRequest request) {
        var response = searchDocumentsUseCase.execute(request);
        return ok(response);
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
        if (!id.equals(request.getId())) {
            return badRequest(Result.<UUID>failure("ID tài liệu không khớp"));
        }

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
}
