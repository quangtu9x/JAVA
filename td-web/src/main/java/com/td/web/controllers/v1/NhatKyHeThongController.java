package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.nhatkyhethong.NhatKyQuanTriUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 30/10/2026 (port nhatkyhethong): hiện chỉ có "Nhật ký quản trị" (đọc edit_history_documents). Nhật ký gửi/
 * nhận văn bản, liên thông HCC, lỗi phát sinh và nhật ký truy cập chưa có nguồn dữ liệu phía Java.
 */
@RestController
@RequestMapping("/api/v1/nhatkyhethong")
@RequiredArgsConstructor
@Tag(name = "Nhật ký hệ thống", description = "Nhật ký quản trị")
public class NhatKyHeThongController extends BaseController {

    private final NhatKyQuanTriUseCase nhatKyQuanTriUseCase;

    @PostMapping("/NhatKyQuanTri")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Nhật ký quản trị theo loại bản ghi (form_edit) - thay qlvb/search form=log")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> nhatKyQuanTri(
            @RequestBody(required = false) NhatKyQuanTriUseCase.Request request) {
        var result = nhatKyQuanTriUseCase.execute(request == null ? new NhatKyQuanTriUseCase.Request() : request);
        if (!result.isSuccess()) {
            return badRequest(Result.failure(result.getError()));
        }
        return ok(result.getData());
    }
}
