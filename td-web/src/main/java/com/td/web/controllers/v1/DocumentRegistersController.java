package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.documentregister.CopyDocumentRegistersRequest;
import com.td.application.documentregister.CopyDocumentRegistersResponse;
import com.td.application.documentregister.CopyDocumentRegistersUseCase;
import com.td.application.documentregister.CreateDocumentRegisterRequest;
import com.td.application.documentregister.CreateDocumentRegisterUseCase;
import com.td.application.documentregister.DeleteDocumentRegisterUseCase;
import com.td.application.documentregister.DocumentRegisterDto;
import com.td.application.documentregister.DocumentRegisterIdsRequest;
import com.td.application.documentregister.GetDefaultDocumentRegisterUseCase;
import com.td.application.documentregister.GetDocumentRegisterUseCase;
import com.td.application.documentregister.ListSelectableDocumentRegistersUseCase;
import com.td.application.documentregister.LockDocumentRegistersUseCase;
import com.td.application.documentregister.RecordIssuedDocumentRegisterNumberRequest;
import com.td.application.documentregister.RecordIssuedDocumentRegisterNumberUseCase;
import com.td.application.documentregister.SearchDocumentRegistersRequest;
import com.td.application.documentregister.SearchDocumentRegistersUseCase;
import com.td.application.documentregister.UnlockDocumentRegistersUseCase;
import com.td.application.documentregister.UpdateDocumentRegisterRequest;
import com.td.application.documentregister.UpdateDocumentRegisterUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.List;
import java.util.UUID;

// BL-116: "Sổ văn bản" (dungrieng/sovanban) — entity RIÊNG (không tái dùng NumberingConfig/BL-104,
// quyết định của người dùng). Tách theo `identifier` (đơn vị/phòng ban) — xem DocumentRegister.
@RestController
@RequestMapping("/api/v1/document-registers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Document Registers", description = "Sổ văn bản theo từng đơn vị (BL-116)")
public class DocumentRegistersController extends BaseController {

    private final CreateDocumentRegisterUseCase createDocumentRegisterUseCase;
    private final UpdateDocumentRegisterUseCase updateDocumentRegisterUseCase;
    private final DeleteDocumentRegisterUseCase deleteDocumentRegisterUseCase;
    private final GetDocumentRegisterUseCase getDocumentRegisterUseCase;
    private final GetDefaultDocumentRegisterUseCase getDefaultDocumentRegisterUseCase;
    private final ListSelectableDocumentRegistersUseCase listSelectableDocumentRegistersUseCase;
    private final SearchDocumentRegistersUseCase searchDocumentRegistersUseCase;
    private final LockDocumentRegistersUseCase lockDocumentRegistersUseCase;
    private final UnlockDocumentRegistersUseCase unlockDocumentRegistersUseCase;
    private final CopyDocumentRegistersUseCase copyDocumentRegistersUseCase;
    private final RecordIssuedDocumentRegisterNumberUseCase recordIssuedDocumentRegisterNumberUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách sổ văn bản",
               description = "Lọc theo keyword, identifier, documentRegisterType, year, isLocked")
    public ResponseEntity<ApiResponse<List<DocumentRegisterDto>>> listDocumentRegisters(
            @RequestParam(defaultValue = "0")   int     pageNumber,
            @RequestParam(defaultValue = "20")  int     pageSize,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false)     String  keyword,
            @RequestParam(required = false)     String  identifier,
            @RequestParam(required = false)     String  documentRegisterType,
            @RequestParam(required = false)     Integer year,
            @RequestParam(required = false)     Boolean isLocked) {

        var request = new SearchDocumentRegistersRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setIdentifier(identifier);
        request.setDocumentRegisterType(documentRegisterType);
        request.setYear(year);
        request.setIsLocked(isLocked);

        return ok(searchDocumentRegistersUseCase.execute(request));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm sổ văn bản theo bộ lọc")
    public ResponseEntity<ApiResponse<List<DocumentRegisterDto>>> searchDocumentRegisters(
            @Valid @RequestBody SearchDocumentRegistersRequest request) {
        return ok(searchDocumentRegistersUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết sổ văn bản")
    public ResponseEntity<ApiResponse<DocumentRegisterDto>> getDocumentRegister(
            @Parameter(description = "Document register ID", required = true) @PathVariable UUID id) {
        return ok(getDocumentRegisterUseCase.execute(id));
    }

    // BL-210/211 (Nhập mới VBDen/VBDi): tương đương qlvb/danhmuc/DanhSachSo (Domino thật) — lấy đúng
    // 1 sổ MẶC ĐỊNH của 1 đơn vị + loại sổ để tự điền form "Nhập mới". Route literal "/default" được
    // Spring ưu tiên khớp trước "/{id}" (path pattern cụ thể hơn thắng path variable), không xung đột.
    @GetMapping("/default")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Lấy sổ văn bản mặc định theo đơn vị + loại sổ",
        description = "Tương thích qlvb/danhmuc/DanhSachSo — codeStartNumber trả về là số gợi ý đã "
            + "tính sẵn (chỉ có ý nghĩa khi isAutoIncrement=true)")
    public ResponseEntity<ApiResponse<DocumentRegisterDto>> getDefaultDocumentRegister(
            @RequestParam String identifier,
            @RequestParam String documentRegisterType) {
        return ok(getDefaultDocumentRegisterUseCase.execute(identifier, documentRegisterType));
    }

    // BL-210/211 (19/08/2026): dropdown "Chọn sổ văn bản" ở màn Nhập mới — cho phép chọn 1 sổ KHÁC
    // sổ mặc định. Route literal "/selectable" được Spring ưu tiên khớp trước "/{id}", không xung đột.
    @GetMapping("/selectable")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách sổ văn bản có thể chọn theo đơn vị + loại sổ",
        description = "Mọi sổ khớp identifier + loại, CHƯA khoá — mỗi sổ có codeStartNumber gợi ý "
            + "đã tính sẵn (chỉ có ý nghĩa khi isAutoIncrement=true), tương đương qlvb/danhmuc/DanhSachSo")
    public ResponseEntity<ApiResponse<List<DocumentRegisterDto>>> listSelectableDocumentRegisters(
            @RequestParam String identifier,
            @RequestParam String documentRegisterType) {
        return ok(listSelectableDocumentRegistersUseCase.execute(identifier, documentRegisterType));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo sổ văn bản mới",
               description = "Không được trùng (identifier, loại sổ, tên sổ); chỉ 1 sổ mặc định/loại sổ/đơn vị")
    public ResponseEntity<ApiResponse<UUID>> createDocumentRegister(
            @Valid @RequestBody CreateDocumentRegisterRequest request) {
        return created(createDocumentRegisterUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật sổ văn bản")
    public ResponseEntity<ApiResponse<UUID>> updateDocumentRegister(
            @Parameter(description = "Document register ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRegisterRequest request) {
        return ok(updateDocumentRegisterUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm sổ văn bản")
    public ResponseEntity<ApiResponse<UUID>> deleteDocumentRegister(
            @Parameter(description = "Document register ID", required = true) @PathVariable UUID id) {
        return ok(deleteDocumentRegisterUseCase.execute(id));
    }

    @PostMapping("/lock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'USER')")
    @Operation(summary = "Khoá hàng loạt sổ văn bản")
    public ResponseEntity<ApiResponse<List<UUID>>> lockDocumentRegisters(
            @Valid @RequestBody DocumentRegisterIdsRequest request) {
        return ok(lockDocumentRegistersUseCase.execute(request.getIds()));
    }

    @PostMapping("/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'USER')")
    @Operation(summary = "Mở khoá hàng loạt sổ văn bản")
    public ResponseEntity<ApiResponse<List<UUID>>> unlockDocumentRegisters(
            @Valid @RequestBody DocumentRegisterIdsRequest request) {
        return ok(unlockDocumentRegistersUseCase.execute(request.getIds()));
    }

    @PostMapping("/copy")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'USER')")
    @Operation(summary = "Sao chép hàng loạt sổ văn bản sang năm mới")
    public ResponseEntity<ApiResponse<CopyDocumentRegistersResponse>> copyDocumentRegisters(
            @Valid @RequestBody CopyDocumentRegistersRequest request) {
        return ok(copyDocumentRegistersUseCase.execute(request));
    }

    // 28/08/2026 ("Chọn sổ văn bản" vbdi, port updateSoVanBan) - xem javadoc use case ve ly do can
    // endpoint rieng nay (Domino van con tao van ban di, khong the tu ghi nhan so qua
    // SoanThaoVbdiUseCase).
    @PostMapping("/{id}/record-issued-number")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Ghi nhận số vừa phát cho sổ văn bản",
        description = "Tương thích updateSoVanBan (ControllModal.jsx, module vbdi) - tăng "
            + "codeStartNumber + thêm vào lịch sử codeStartNumbers. Dùng khi luồng tạo document "
            + "chưa gọi trực tiếp DocumentRegister#recordIssuedNumber (vd Domino cũ)")
    public ResponseEntity<ApiResponse<UUID>> recordIssuedNumber(
            @Parameter(description = "Document register ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody RecordIssuedDocumentRegisterNumberRequest request) {
        return ok(recordIssuedDocumentRegisterNumberUseCase.execute(id, request));
    }
}
