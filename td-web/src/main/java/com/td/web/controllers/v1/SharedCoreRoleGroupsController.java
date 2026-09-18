package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.sharedcore.CreateRoleGroupRequest;
import com.td.application.sharedcore.CreateRoleGroupUseCase;
import com.td.application.sharedcore.DeleteRoleGroupUseCase;
import com.td.application.sharedcore.GetRoleGroupUseCase;
import com.td.application.sharedcore.RoleGroupDto;
import com.td.application.sharedcore.SearchRoleGroupsRequest;
import com.td.application.sharedcore.SearchRoleGroupsUseCase;
import com.td.application.sharedcore.UpdateRoleGroupRequest;
import com.td.application.sharedcore.UpdateRoleGroupUseCase;
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

// BL-111: "Nhóm vai trò" (Domino form group_roles) — đơn vị THỰC SỰ gán cho user (AppUser.roleGroupId,
// xem SharedCoreUsersController). Khác "Vai trò" (SharedCoreRolesController) chỉ là 1 thành phần con.
@RestController
@RequestMapping("/api/v1/shared-core/role-groups")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Role Groups", description = "Nhóm vai trò trong shared core (BL-111)")
public class SharedCoreRoleGroupsController extends BaseController {

    private final SearchRoleGroupsUseCase searchRoleGroupsUseCase;
    private final GetRoleGroupUseCase getRoleGroupUseCase;
    private final CreateRoleGroupUseCase createRoleGroupUseCase;
    private final UpdateRoleGroupUseCase updateRoleGroupUseCase;
    private final DeleteRoleGroupUseCase deleteRoleGroupUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách nhóm vai trò shared core")
    public ResponseEntity<ApiResponse<List<RoleGroupDto>>> listRoleGroups(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {

        var request = new SearchRoleGroupsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setIsActive(isActive);

        return ok(searchRoleGroupsUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết nhóm vai trò shared core",
        description = "Trả kèm roleIds/roleNames (vai trò con) và permissionIds/permissionCodes (quyền gán trực tiếp).")
    public ResponseEntity<ApiResponse<RoleGroupDto>> getRoleGroup(
            @Parameter(description = "Role group ID", required = true)
            @PathVariable UUID id) {
        return ok(getRoleGroupUseCase.execute(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo mới nhóm vai trò shared core")
    public ResponseEntity<ApiResponse<UUID>> createRoleGroup(
            @Valid @RequestBody CreateRoleGroupRequest request) {
        return created(createRoleGroupUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật nhóm vai trò shared core",
        description = "Partial update — field null nghĩa là không đổi. Riêng roleIds/permissionIds: "
            + "null = giữ nguyên danh sách hiện có, có giá trị (kể cả rỗng) = THAY TOÀN BỘ danh sách.")
    public ResponseEntity<ApiResponse<UUID>> updateRoleGroup(
            @Parameter(description = "Role group ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleGroupRequest request) {
        return ok(updateRoleGroupUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm nhóm vai trò shared core")
    public ResponseEntity<ApiResponse<UUID>> deleteRoleGroup(
            @Parameter(description = "Role group ID", required = true) @PathVariable UUID id) {
        return ok(deleteRoleGroupUseCase.execute(id));
    }
}
