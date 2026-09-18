package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.tenant.ActivateTenantUseCase;
import com.td.application.tenant.CreateTenantRequest;
import com.td.application.tenant.CreateTenantUseCase;
import com.td.application.tenant.DeactivateTenantUseCase;
import com.td.application.tenant.GetTenantUseCase;
import com.td.application.tenant.ListTenantsUseCase;
import com.td.application.tenant.TenantDto;
import com.td.application.tenant.TestTenantConnectionRequest;
import com.td.application.tenant.TestTenantConnectionUseCase;
import com.td.application.tenant.UpdateTenantRequest;
import com.td.application.tenant.UpdateTenantUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// BL-117: quản lý/khởi tạo đơn vị (tenant) — thao tác NỀN TẢNG (platform-level), KHÔNG thuộc phạm
// vi 1 tenant cụ thể nào (tạo/xem/khoá CẢ registry mọi tenant, kể cả các tenant khác tenant hiện
// tại). Chỉ role PLATFORM_ADMIN (role MỚI, KHÔNG dùng chung ADMIN — ADMIN hiện tại chỉ là quyền cao
// nhất TRONG 1 tenant, không nên tự động có quyền tạo/xem/xoay vòng credential của MỌI tenant khác;
// xem docs/architecture/06-database-per-tenant-multi-tenant.md).
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
@Validated
@Tag(name = "Platform Tenants", description = "Quản lý/khởi tạo đơn vị (tenant) — database-per-tenant (BL-117)")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantsController extends BaseController {

    private final CreateTenantUseCase createTenantUseCase;
    private final UpdateTenantUseCase updateTenantUseCase;
    private final GetTenantUseCase getTenantUseCase;
    private final ListTenantsUseCase listTenantsUseCase;
    private final ActivateTenantUseCase activateTenantUseCase;
    private final DeactivateTenantUseCase deactivateTenantUseCase;
    private final TestTenantConnectionUseCase testTenantConnectionUseCase;

    @GetMapping
    @Operation(summary = "Danh sách tenant", description = "Không trả mật khẩu dưới bất kỳ hình thức nào")
    public ResponseEntity<ApiResponse<List<TenantDto>>> listTenants() {
        return ok(listTenantsUseCase.execute());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết tenant")
    public ResponseEntity<ApiResponse<TenantDto>> getTenant(
            @Parameter(description = "Tenant ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(getTenantUseCase.execute(id));
    }

    @PostMapping("/test-connection")
    @Operation(summary = "Kiểm tra kết nối trước khi lưu",
               description = "Không tạo database, không lưu gì — chỉ mở thử 1 connection tới host/port/database/username/password rồi đóng ngay")
    public ResponseEntity<ApiResponse<Void>> testConnection(@Valid @RequestBody TestTenantConnectionRequest request) {
        return ok(testTenantConnectionUseCase.execute(request));
    }

    @PostMapping
    @Operation(summary = "Khởi tạo đơn vị (tenant) mới",
               description = "mode=CREATE_NEW (mặc định): tự CREATE DATABASE + chạy Flyway vào database mới hoàn toàn " +
                   "trống (yêu cầu username/password có quyền CREATEDB trên server Postgres đích). " +
                   "mode=REGISTER_EXISTING: đăng ký 1 database ĐÃ TỒN TẠI/ĐÃ CÓ DỮ LIỆU (không CREATE DATABASE) " +
                   "— dùng khi đưa 1 đơn vị đã vận hành từ trước vào registry.")
    public ResponseEntity<ApiResponse<UUID>> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return created(createTenantUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật tenant",
               description = "displayName sửa độc lập. Để XOAY VÒNG CREDENTIAL phải gửi ĐỦ host+port+username+password " +
                   "cùng lúc (không hỗ trợ sửa từng phần). KHÔNG thể đổi tenantId/databaseName (bất biến — đổi = tạo tenant mới).")
    public ResponseEntity<ApiResponse<UUID>> updateTenant(
            @Parameter(description = "Tenant ID (UUID)", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request) {
        return ok(updateTenantUseCase.execute(id, request));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt lại tenant", description = "Hot-add lại connection pool nếu chưa có, không cần restart app")
    public ResponseEntity<ApiResponse<UUID>> activateTenant(
            @Parameter(description = "Tenant ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(activateTenantUseCase.execute(id));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Vô hiệu hoá tenant",
               description = "KHÔNG xoá database thật (chỉ khoá) — mọi request tiếp theo của tenant này bị từ chối (403), " +
                   "không fallback sang tenant mặc định")
    public ResponseEntity<ApiResponse<UUID>> deactivateTenant(
            @Parameter(description = "Tenant ID (UUID)", required = true) @PathVariable UUID id) {
        return ok(deactivateTenantUseCase.execute(id));
    }
}
