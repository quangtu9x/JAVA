package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.sharedcore.ManageMyDelegationUseCase;
import com.td.application.sharedcore.SaveMyDelegationRequest;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 09/10/2026 — màn "Ủy quyền" (menu người dùng), thay Domino {@code qlvb/danhmuc/UyQuyen} +
 * {@code qlvb/get?databasetype=qlvb}. Chỉ thao tác ủy quyền của CHÍNH người đăng nhập (không nhận id từ
 * client) — xem {@link ManageMyDelegationUseCase}.
 */
@RestController
@RequestMapping("/api/v1/shared-core/delegations")
@RequiredArgsConstructor
@Tag(name = "Shared Core - Ủy quyền", description = "Ủy quyền xử lý thay của người đăng nhập")
public class UserDelegationsController extends BaseController {

    private final ManageMyDelegationUseCase manageMyDelegationUseCase;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ủy quyền đang/sắp hiệu lực + lịch sử ủy quyền của tôi")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMine() {
        return ok(manageMyDelegationUseCase.getMine());
    }

    @PostMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lưu ủy quyền (thay thế ủy quyền đang/sắp hiệu lực trước đó)")
    public ResponseEntity<ApiResponse<UUID>> saveMine(@RequestBody SaveMyDelegationRequest request) {
        return ok(manageMyDelegationUseCase.saveMine(request));
    }

    @DeleteMapping("/me/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Huỷ 1 ủy quyền của tôi")
    public ResponseEntity<ApiResponse<UUID>> revokeMine(@PathVariable UUID id) {
        return ok(manageMyDelegationUseCase.revokeMine(id));
    }
}
