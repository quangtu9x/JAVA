package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.PaginationResponse;
import com.td.application.edithistory.AddEditHistoryRequest;
import com.td.application.edithistory.AddEditHistoryUseCase;
import com.td.application.edithistory.ListEditHistoryRequest;
import com.td.application.edithistory.ListEditHistoryUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026 (cutover qlvb/add + qlvb/danhmuc/GetRes): vết hiệu chỉnh dùng chung cho các màn không có
 * bảng vết riêng (duthao/vbdi/vbden giữ endpoint LuuVetHieuChinh/VetHieuChinh của module).
 */
@RestController
@RequestMapping("/api/v1/vethieuchinh")
@RequiredArgsConstructor
@Validated
@Tag(name = "Vet hieu chinh", description = "Vet hieu chinh dung chung (thay qlvb/add form=log)")
public class EditHistoryController extends BaseController {

    private final AddEditHistoryUseCase addEditHistoryUseCase;
    private final ListEditHistoryUseCase listEditHistoryUseCase;

    @PostMapping("/Luu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi 1 vết hiệu chỉnh {field:{oldvalue,newvalue}}; user_creator do server gán")
    public ResponseEntity<ApiResponse<UUID>> luu(@Valid @RequestBody AddEditHistoryRequest request) {
        return created(addEditHistoryUseCase.execute(request));
    }

    @PostMapping("/Xem")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử hiệu chỉnh theo bản ghi cha (khuôn item qlvb/danhmuc/GetRes), mới nhất trước")
    public ResponseEntity<ApiResponse<PaginationResponse<Map<String, Object>>>> xem(
            @Valid @RequestBody ListEditHistoryRequest request) {
        // Giữ ApiResponse<PaginationResponse> (FE đọc data.items) — không dùng BaseController.ok(PaginationResponse).
        return ResponseEntity.ok(ApiResponse.success(listEditHistoryUseCase.execute(request)));
    }
}
