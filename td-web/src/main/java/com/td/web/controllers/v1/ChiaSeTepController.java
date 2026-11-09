package com.td.web.controllers.v1;

import com.td.application.chiasetep.ChiaSeTepCommandUseCase;
import com.td.application.chiasetep.ChiaSeTepQueryUseCase;
import com.td.application.chiasetep.ChiaSeTepRequests;
import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
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
import java.util.UUID;

/**
 * 30/10/2026 (port module "Quản lý tài liệu" - Domino ChiaSeTepController + qlvb/add|get|view/query
 * ?databasetype=chiasetep). Tệp đính kèm dùng API tệp chung {@code /api/v1/documents/{id}/files}.
 */
@RestController
@RequestMapping("/api/v1/chiasetep")
@RequiredArgsConstructor
@Tag(name = "Chia sẻ tệp", description = "Quản lý tài liệu cá nhân / chia sẻ thư mục")
public class ChiaSeTepController extends BaseController {

    private final ChiaSeTepQueryUseCase queryUseCase;
    private final ChiaSeTepCommandUseCase commandUseCase;

    @PostMapping("/DanhSachThuMuc")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cây thư mục theo loại (canhan/dachiase/duocchiase/..._theonguoi)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> danhSachThuMuc(
            @RequestBody(required = false) ChiaSeTepRequests.Tree request) {
        return ok(queryUseCase.tree(request == null ? new ChiaSeTepRequests.Tree() : request));
    }

    @PostMapping("/DanhSachNguoiDung")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người dùng đã/được chia sẻ ({key, data})")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> danhSachNguoiDung(
            @RequestBody(required = false) ChiaSeTepRequests.Users request) {
        return ok(queryUseCase.users(request == null ? new ChiaSeTepRequests.Users() : request));
    }

    @PostMapping("/DanhSachTaiLieu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tài liệu trong thư mục (không chọn thư mục = mọi tài liệu được truy cập)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> danhSachTaiLieu(
            @RequestBody(required = false) ChiaSeTepRequests.ListDocuments request) {
        var result = queryUseCase.listDocuments(request == null ? new ChiaSeTepRequests.ListDocuments() : request);
        if (!result.isSuccess()) {
            return badRequest(Result.failure(result.getError()));
        }
        return ok(result.getData());
    }

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(@RequestBody ChiaSeTepRequests.Id request) {
        return ok(queryUseCase.detail(request));
    }

    @PostMapping("/Luu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thêm/sửa thư mục hoặc tài liệu (id rỗng = thêm mới)")
    public ResponseEntity<ApiResponse<UUID>> luu(@RequestBody ChiaSeTepRequests.Save request) {
        return ok(commandUseCase.save(request));
    }

    @PostMapping("/XoaThuMuc")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UUID>>> xoaThuMuc(@RequestBody ChiaSeTepRequests.Ids request) {
        return ok(commandUseCase.deleteFolders(request.getIds()));
    }

    @PostMapping("/XoaTaiLieu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UUID>>> xoaTaiLieu(@RequestBody ChiaSeTepRequests.Ids request) {
        return ok(commandUseCase.deleteDocuments(request.getIds()));
    }

    @PostMapping("/DanThuMuc")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UUID>> danThuMuc(@RequestBody ChiaSeTepRequests.Paste request) {
        return ok(commandUseCase.paste(request));
    }

    @PostMapping("/BoChiaSeThuMuc")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UUID>>> boChiaSeThuMuc(@RequestBody ChiaSeTepRequests.Unshare request) {
        return ok(commandUseCase.unshare(request));
    }

    @PostMapping("/GanVanBan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UUID>> ganVanBan(@RequestBody ChiaSeTepRequests.Attach request) {
        return ok(commandUseCase.attach(request));
    }
}
