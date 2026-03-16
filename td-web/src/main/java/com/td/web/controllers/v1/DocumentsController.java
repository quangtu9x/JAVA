package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.CreateDocumentRequest;
import com.td.application.documents.CreateDocumentUseCase;
import com.td.application.documents.DeleteDocumentUseCase;
import com.td.application.documents.DocumentDto;
import com.td.application.documents.GetDocumentRequest;
import com.td.application.documents.GetDocumentUseCase;
import com.td.application.documents.SearchDocumentsRequest;
import com.td.application.documents.SearchDocumentsUseCase;
import com.td.application.documents.UpdateDocumentRequest;
import com.td.application.documents.UpdateDocumentUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Documents", description = "Dynamic document management endpoints")
public class DocumentsController extends BaseController {

    private final CreateDocumentUseCase createDocumentUseCase;
    private final UpdateDocumentUseCase updateDocumentUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;
    private final GetDocumentUseCase getDocumentUseCase;
    private final SearchDocumentsUseCase searchDocumentsUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "List documents")
    public ResponseEntity<PaginationResponse<DocumentDto>> listDocuments(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "lastModifiedOn") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String status) {
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
    @Operation(summary = "Get document details")
    public ResponseEntity<Result<DocumentDto>> getDocument(@PathVariable UUID id) {
        var result = getDocumentUseCase.execute(new GetDocumentRequest(id));
        return ok(result);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Search documents with dynamic filters")
    public ResponseEntity<PaginationResponse<DocumentDto>> searchDocuments(
            @Valid @RequestBody SearchDocumentsRequest request) {
        var response = searchDocumentsUseCase.execute(request);
        return ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Create a new document")
    public ResponseEntity<Result<UUID>> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        var result = createDocumentUseCase.execute(request);
        return created(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Update an existing document")
    public ResponseEntity<Result<UUID>> updateDocument(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        if (!id.equals(request.getId())) {
            return badRequest(Result.<UUID>failure("Document ID mismatch"));
        }

        var result = updateDocumentUseCase.execute(request);
        return ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Delete a document (soft delete)")
    public ResponseEntity<Result<UUID>> deleteDocument(@PathVariable UUID id) {
        var result = deleteDocumentUseCase.execute(id);
        return ok(result);
    }
}
