package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.dbserver.ActivateDbServerUseCase;
import com.td.application.dbserver.CreateDbServerRequest;
import com.td.application.dbserver.CreateDbServerUseCase;
import com.td.application.dbserver.DbServerCredentialDto;
import com.td.application.dbserver.DbServerDto;
import com.td.application.dbserver.DeactivateDbServerUseCase;
import com.td.application.dbserver.DeleteDbServerUseCase;
import com.td.application.dbserver.GetDbServerCredentialUseCase;
import com.td.application.dbserver.GetDbServerUseCase;
import com.td.application.dbserver.ListDbServersUseCase;
import com.td.application.dbserver.TestDbServerConnectionRequest;
import com.td.application.dbserver.TestDbServerConnectionUseCase;
import com.td.application.dbserver.UpdateDbServerRequest;
import com.td.application.dbserver.UpdateDbServerUseCase;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// BL-117 (bổ sung 19/08/2026): registry "Máy chủ CSDL" (host+port Postgres) — CHỈ để tiện chọn nhanh
// lúc khởi tạo đơn vị (PlatformTenantsController) thay vì gõ tay host/port mỗi lần. KHÔNG nhầm với
// "Máy chủ" cũ (menu Hệ thống > Máy chủ, mail-in/file/search/cache/log server Domino — xem javadoc
// DbServer.java). Cùng phạm vi PLATFORM_ADMIN với Tenant (dữ liệu nền tảng dùng chung mọi tenant).
@RestController
@RequestMapping("/api/v1/platform/db-servers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Platform DB Servers", description = "Registry máy chủ CSDL (host+port) dùng khi khởi tạo đơn vị (BL-117)")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDbServersController extends BaseController {

    private final CreateDbServerUseCase createDbServerUseCase;
    private final UpdateDbServerUseCase updateDbServerUseCase;
    private final GetDbServerUseCase getDbServerUseCase;
    private final ListDbServersUseCase listDbServersUseCase;
    private final ActivateDbServerUseCase activateDbServerUseCase;
    private final DeactivateDbServerUseCase deactivateDbServerUseCase;
    private final DeleteDbServerUseCase deleteDbServerUseCase;
    private final GetDbServerCredentialUseCase getDbServerCredentialUseCase;
    private final TestDbServerConnectionUseCase testDbServerConnectionUseCase;

    @GetMapping
    @Operation(summary = "Danh sách máy chủ CSDL", description = "KHÔNG trả mật khẩu — chỉ trả cờ hasPassword")
    public ResponseEntity<ApiResponse<List<DbServerDto>>> listDbServers() {
        return ok(listDbServersUseCase.execute());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết máy chủ CSDL", description = "KHÔNG trả mật khẩu — chỉ trả cờ hasPassword")
    public ResponseEntity<ApiResponse<DbServerDto>> getDbServer(
            @Parameter(description = "DB server ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(getDbServerUseCase.execute(id));
    }

    @GetMapping("/{id}/credential")
    @Operation(summary = "Lấy credential ĐÃ GIẢI MÃ để autofill lúc khởi tạo đơn vị",
               description = "Ngoại lệ CÓ CHỦ Ý so với nguyên tắc chung — CHỈ dùng để autofill form tạo/sửa đơn vị khi admin chủ động chọn máy chủ này, xem javadoc DbServer.java")
    public ResponseEntity<ApiResponse<DbServerCredentialDto>> getDbServerCredential(
            @Parameter(description = "DB server ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(getDbServerCredentialUseCase.execute(id));
    }

    @PostMapping("/test-connection")
    @Operation(summary = "Kiểm tra kết nối trước khi lưu credential",
               description = "Mở thử 1 connection tới database \"postgres\" mặc định trên host/port đích — không lưu gì")
    public ResponseEntity<ApiResponse<Void>> testConnection(@Valid @RequestBody TestDbServerConnectionRequest request) {
        return ok(testDbServerConnectionUseCase.execute(request));
    }

    @PostMapping
    @Operation(summary = "Thêm máy chủ CSDL mới", description = "Chỉ đăng ký thông tin host/port — không tạo/kết nối gì tới server")
    public ResponseEntity<ApiResponse<UUID>> createDbServer(@Valid @RequestBody CreateDbServerRequest request) {
        return created(createDbServerUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật máy chủ CSDL")
    public ResponseEntity<ApiResponse<UUID>> updateDbServer(
            @Parameter(description = "DB server ID (UUID)", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateDbServerRequest request) {
        return ok(updateDbServerUseCase.execute(id, request));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt lại máy chủ CSDL", description = "Hiện lại trong danh sách chọn lúc khởi tạo đơn vị")
    public ResponseEntity<ApiResponse<UUID>> activateDbServer(
            @Parameter(description = "DB server ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(activateDbServerUseCase.execute(id));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Tạm ẩn máy chủ CSDL", description = "Ẩn khỏi danh sách chọn lúc khởi tạo đơn vị, KHÔNG xoá (vd. đang bảo trì)")
    public ResponseEntity<ApiResponse<UUID>> deactivateDbServer(
            @Parameter(description = "DB server ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(deactivateDbServerUseCase.execute(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá mềm máy chủ CSDL")
    public ResponseEntity<ApiResponse<UUID>> deleteDbServer(
            @Parameter(description = "DB server ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(deleteDbServerUseCase.execute(id));
    }
}
