package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.privatecatalog.CreatePrivateCategoryRequest;
import com.td.application.privatecatalog.CreatePrivateCategoryUseCase;
import com.td.application.privatecatalog.DeletePrivateCategoryUseCase;
import com.td.application.privatecatalog.GetGroupPeopleForIdentifierUseCase;
import com.td.application.privatecatalog.GetGroupUnitForIdentifierUseCase;
import com.td.application.privatecatalog.GetPrivateCategoryUseCase;
import com.td.application.privatecatalog.PrivateCategoryDto;
import com.td.application.privatecatalog.SearchPrivateCategoriesRequest;
import com.td.application.privatecatalog.SearchPrivateCategoriesUseCase;
import com.td.application.privatecatalog.UpdatePrivateCategoryRequest;
import com.td.application.privatecatalog.UpdatePrivateCategoryUseCase;
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
import java.util.Map;
import java.util.UUID;

// BL-116: "Danh mục riêng" (dungrieng/*) — bảng private_categories, tenant-scoped (KHÔNG global như
// CategoriesController/BL-113 — xem docs/architecture/05-shared-schema-multi-tenant.md). Dùng cho
// các danh mục phẳng đơn giản (ký hiệu văn bản, dữ liệu quá khứ...); các danh mục phức tạp hơn (sổ
// văn bản, nhóm đơn vị/người nhận, phông tài liệu...) có controller/entity riêng.
@RestController
@RequestMapping("/api/v1/private-categories")
@RequiredArgsConstructor
@Validated
@Tag(name = "Private Categories", description = "Danh mục riêng theo từng đơn vị/tenant (BL-116)")
public class PrivateCategoriesController extends BaseController {

    private final CreatePrivateCategoryUseCase       createPrivateCategoryUseCase;
    private final UpdatePrivateCategoryUseCase       updatePrivateCategoryUseCase;
    private final DeletePrivateCategoryUseCase       deletePrivateCategoryUseCase;
    private final GetPrivateCategoryUseCase          getPrivateCategoryUseCase;
    private final SearchPrivateCategoriesUseCase     searchPrivateCategoriesUseCase;
    private final GetGroupPeopleForIdentifierUseCase getGroupPeopleForIdentifierUseCase;
    private final GetGroupUnitForIdentifierUseCase   getGroupUnitForIdentifierUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách danh mục riêng",
               description = "Hỗ trợ lọc theo keyword, parentId, level, isActive, forms; sắp xếp theo sortOrder mặc định")
    public ResponseEntity<ApiResponse<List<PrivateCategoryDto>>> listPrivateCategories(
            @RequestParam(defaultValue = "0")          int     pageNumber,
            @RequestParam(defaultValue = "20")         int     pageSize,
            @RequestParam(defaultValue = "sortOrder")  String  sortBy,
            @RequestParam(defaultValue = "asc")        String  sortDirection,
            @RequestParam(required = false)            String  keyword,
            @RequestParam(required = false)            UUID    parentId,
            @RequestParam(required = false)            Integer level,
            @RequestParam(required = false)            Boolean isActive,
            @RequestParam(required = false)            List<String> forms,
            @RequestParam(required = false)            String  identifier,
            @RequestParam(required = false)            Boolean activeMembersOnly) {

        var request = new SearchPrivateCategoriesRequest();
        request.setActiveMembersOnly(activeMembersOnly);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setParentId(parentId);
        request.setLevel(level);
        request.setIsActive(isActive);
        request.setForms(forms);
        request.setIdentifier(identifier);

        return ok(searchPrivateCategoriesUseCase.execute(request));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm danh mục riêng theo bộ lọc")
    public ResponseEntity<ApiResponse<List<PrivateCategoryDto>>> searchPrivateCategories(
            @Valid @RequestBody SearchPrivateCategoriesRequest request) {
        return ok(searchPrivateCategoriesUseCase.execute(request));
    }

    // BL-116 (tiếp, 25/08/2026) — tương đương DXL Domino nhom_nguoi_phathanh_by_identifier, dùng bởi
    // JSTree.jsx (TreeComponent) để chèn "nhóm cấu hình riêng" phía trên cây tổ chức. Lọc theo field
    // ĐỘNG donvi_sudung_identifier (đơn vị ĐƯỢC PHÉP DÙNG nhóm) — KHÁC "identifier" (cột thật, đơn
    // vị đã TẠO nhóm, dùng bởi endpoint list phía trên) — xem javadoc GetGroupPeopleForIdentifierUseCase.
    @GetMapping("/groups-for-identifier")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách nhóm người nhận dùng được bởi 1 đơn vị (identifier)",
               description = "Tương đương view Domino nhom_nguoi_phathanh_by_identifier — lọc theo field động donvi_sudung_identifier")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupPeopleForIdentifier(
            @RequestParam String identifier) {
        return ok(getGroupPeopleForIdentifierUseCase.execute(identifier));
    }

    // Yêu cầu người dùng 28/08/2026 ("port luôn nhóm phát hành nhanh") — tương đương 3 view Domino
    // nhom_CQN/CQLH/DVNB_phathanh_by_identifier, dùng bởi JSTreeCheckAll.jsx/JSTreePhatHanhCQLH.jsx
    // (ModalPhatHanhVanBan.jsx) — khác getGroupPeopleForIdentifier ở trên (form group_people, không
    // lọc type): form group_unit BẮT BUỘC lọc thêm theo type (xem javadoc GetGroupUnitForIdentifierUseCase).
    @GetMapping("/groups-for-identifier-and-type")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách nhóm đơn vị nhận dùng được bởi 1 đơn vị (identifier) + loại",
               description = "Tương đương view Domino nhom_CQN/CQLH/DVNB_phathanh_by_identifier — lọc theo "
                   + "field động donvi_sudung_identifier VÀ type")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupUnitForIdentifier(
            @RequestParam String identifier,
            @RequestParam String type) {
        return ok(getGroupUnitForIdentifierUseCase.execute(identifier, type));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết danh mục riêng")
    public ResponseEntity<ApiResponse<PrivateCategoryDto>> getPrivateCategory(
            @Parameter(description = "Private category ID", required = true) @PathVariable UUID id) {
        return ok(getPrivateCategoryUseCase.execute(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo danh mục riêng mới",
               description = "Tự động tính level và fullPath từ parentId. code tự sinh từ name nếu không gửi.")
    public ResponseEntity<ApiResponse<UUID>> createPrivateCategory(
            @Valid @RequestBody CreatePrivateCategoryRequest request) {
        return created(createPrivateCategoryUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật danh mục riêng",
               description = "Truyền parentId để thay đổi vị trí trong cây (null = lên gốc). Không truyền = giữ nguyên parent hiện tại.")
    public ResponseEntity<ApiResponse<UUID>> updatePrivateCategory(
            @Parameter(description = "Private category ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdatePrivateCategoryRequest request) {
        return ok(updatePrivateCategoryUseCase.execute(id, request));
    }

    // BL-118 (bugfix 19/08/2026): trước đây thiếu 'USER' — khác 4 endpoint còn lại của CHÍNH
    // controller này (list/get/create/update đều cho USER) và khác UX FE (nút "Xóa" luôn hiện,
    // không gate theo quyền nào — xem ItemsList.jsx các màn dùng privateCatalogApi.js). Khớp lại
    // cho nhất quán: "danh mục riêng" (ký hiệu văn bản, dữ liệu quá khứ...) là dữ liệu vận hành
    // thường ngày, không phải thao tác quản trị nền tảng như xóa User/Role/RoleGroup/Organization
    // (các controller đó CỐ Ý giữ nguyên chỉ ADMIN/PRODUCT_MANAGER/BRAND_MANAGER).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm danh mục riêng")
    public ResponseEntity<ApiResponse<UUID>> deletePrivateCategory(
            @Parameter(description = "Private category ID", required = true) @PathVariable UUID id) {
        return ok(deletePrivateCategoryUseCase.execute(id));
    }
}
