package com.td.application.tenant;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

// BL-117: "khởi tạo đơn vị" — CREATE DATABASE thật (mode=CREATE_NEW, mặc định) hoặc đăng ký 1
// database đã có sẵn (mode=REGISTER_EXISTING, dùng đúng 1 lần cho tenant H05 hiện tại khi cutover
// sang registry mới, xem docs/architecture/06-database-per-tenant-multi-tenant.md). Chạy trên
// EntityManagerFactory/TransactionManager RIÊNG của database control-plane (KHÔNG phải
// TenantRoutingDataSource nghiệp vụ) — xem ControlPlaneDataSourceConfig.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class CreateTenantUseCase {

    // Whitelist nghiêm ngặt cho identifier dùng trực tiếp trong DDL không bind param được
    // (CREATE DATABASE "<tên>") — chặn SQL injection qua tenantId/databaseName.
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,62}$");
    private static final Set<String> RESERVED_DATABASE_NAMES = Set.of("postgres", "template0", "template1");

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public Result<UUID> execute(CreateTenantRequest request) {
        try {
            String tenantId = TextNormalizer.normalize(request.getTenantId());
            String displayName = TextNormalizer.normalizeAndSanitize(request.getDisplayName());
            String host = TextNormalizer.normalize(request.getHost());
            String username = TextNormalizer.normalize(request.getUsername());
            String databaseName = TextNormalizer.normalize(request.getDatabaseName());
            String plainPassword = request.getPassword();
            TenantProvisionMode mode = request.getMode() != null ? request.getMode() : TenantProvisionMode.CREATE_NEW;

            if (tenantId == null || tenantId.isBlank()) {
                return Result.failure("Mã tenant không được để trống");
            }
            if (!SAFE_IDENTIFIER.matcher(tenantId).matches()) {
                return Result.failure("Mã tenant chỉ được chứa chữ/số/dấu gạch dưới, bắt đầu bằng chữ, tối đa 63 ký tự");
            }
            if (databaseName == null || databaseName.isBlank()) {
                databaseName = tenantId.toLowerCase();
            }
            if (!SAFE_IDENTIFIER.matcher(databaseName).matches()) {
                return Result.failure("Tên database chỉ được chứa chữ/số/dấu gạch dưới, bắt đầu bằng chữ, tối đa 63 ký tự");
            }
            if (RESERVED_DATABASE_NAMES.contains(databaseName.toLowerCase())) {
                return Result.failure("Tên database '" + databaseName + "' bị cấm sử dụng (tên hệ thống Postgres)");
            }
            if (displayName == null || displayName.isBlank()) {
                return Result.failure("Tên hiển thị đơn vị không được để trống");
            }
            if (host == null || host.isBlank()) {
                return Result.failure("Host không được để trống");
            }
            if (request.getPort() == null || request.getPort() < 1 || request.getPort() > 65535) {
                return Result.failure("Port không hợp lệ");
            }
            if (username == null || username.isBlank()) {
                return Result.failure("Username không được để trống");
            }
            if (plainPassword == null || plainPassword.isBlank()) {
                return Result.failure("Password không được để trống");
            }
            if (tenantRepository.existsByTenantIdAndDeletedOnIsNull(tenantId)) {
                return Result.failure("Mã tenant '" + tenantId + "' đã tồn tại");
            }
            if (tenantRepository.existsByDatabaseNameAndDeletedOnIsNull(databaseName)) {
                return Result.failure("Tên database '" + databaseName + "' đã được dùng bởi tenant khác");
            }

            var command = new ProvisionTenantCommand(
                tenantId, host, request.getPort(), databaseName, username, plainPassword,
                request.getDriverClassName(), mode);

            // CREATE DATABASE thật (nếu CREATE_NEW) + Flyway + hot-add pool — xem
            // PostgresTenantProvisioningService. Trả về mật khẩu ĐÃ MÃ HOÁ, sẵn sàng lưu DB.
            String encryptedPassword = tenantProvisioningService.provision(command);

            var entity = new Tenant(tenantId, displayName, host, request.getPort(), databaseName,
                username, encryptedPassword, request.getDriverClassName());
            var saved = tenantRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Khởi tạo tenant thất bại", ex);
            return Result.failure("Khởi tạo tenant thất bại: " + ex.getMessage());
        }
    }
}
