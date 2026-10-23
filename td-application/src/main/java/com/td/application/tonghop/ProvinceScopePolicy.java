package com.td.application.tonghop;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.tenant.ResolveUserTenantUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phạm vi dữ liệu toàn tỉnh dùng chung cho các màn /tonghop (Tìm kiếm, Giám sát VB đi, Trục quốc gia) — người dùng
 * chốt 22/10/2026 (tdgateway/docs/24 §2.8):
 * <ul>
 *   <li>Tài khoản tổng (username {@code admin} — cùng định nghĩa "quản trị tối cao" của
 *       {@code AccountProvisioningChecker} — hoặc {@code PLATFORM_ADMIN}): toàn tỉnh, ở mọi tenant.</li>
 *   <li>Đăng nhập tenant cấp tỉnh ({@code app.province.admin-tenant-ids}, mặc định {@code H05}): toàn tỉnh.</li>
 *   <li>Tenant khác (vd admin đơn vị H05_01): chỉ dữ liệu của tenant mình — server ép, không tin tham số client.</li>
 * </ul>
 * Chỉ tính PHẠM VI; ai được vào màn (ADMIN, mã quyền...) do từng màn quyết.
 *
 * <p><b>Bẫy fallback:</b> {@code TenantResolver} lặng lẽ dùng tenant MẶC ĐỊNH (H05 = chính tenant cấp tỉnh) khi không
 * suy được tenant của người dùng (không có AppUser, đơn vị chưa gắn {@code linked_tenant_id}, tenant bị khoá). Vì vậy
 * tenant chỉ được tin khi {@link ResolveUserTenantUseCase} (hồ sơ người dùng) xác nhận; không xác nhận được →
 * {@link ProvinceScope#unresolved()}; claim tenant lệch hồ sơ → chỉ phạm vi tenant đang phục vụ, không toàn tỉnh.</p>
 */
@Service
public class ProvinceScopePolicy {

    static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    static final String SUPER_ADMIN_USERNAME = "admin";

    private final CurrentUserProvider currentUserProvider;
    private final CurrentTenantProvider currentTenantProvider;
    /** username → tenant suy từ hồ sơ AppUser (cây cơ cấu); rỗng = không suy được. */
    private final Function<String, Optional<String>> userTenantLookup;
    private final Set<String> provinceTenantIds;

    @Autowired
    public ProvinceScopePolicy(CurrentUserProvider currentUserProvider, CurrentTenantProvider currentTenantProvider,
                               ResolveUserTenantUseCase resolveUserTenantUseCase,
                               @Value("${app.province.admin-tenant-ids:H05}") String provinceTenantIds) {
        this(currentUserProvider, currentTenantProvider, resolveUserTenantUseCase::execute, provinceTenantIds);
    }

    public ProvinceScopePolicy(CurrentUserProvider currentUserProvider, CurrentTenantProvider currentTenantProvider,
                               Function<String, Optional<String>> userTenantLookup, String provinceTenantIds) {
        this.currentUserProvider = currentUserProvider;
        this.currentTenantProvider = currentTenantProvider;
        this.userTenantLookup = userTenantLookup;
        this.provinceTenantIds = Arrays.stream(provinceTenantIds == null ? new String[0] : provinceTenantIds.split("[,;]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public ProvinceScope current() {
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return ProvinceScope.unresolved();
        }
        String tenantId = currentTenantProvider.currentTenantId().orElse(null);
        if (SUPER_ADMIN_USERNAME.equalsIgnoreCase(username) || currentRoles().contains(PLATFORM_ADMIN)) {
            return ProvinceScope.builder().resolved(true).superAccount(true).province(true).tenantId(tenantId).build();
        }
        Optional<String> userTenant = lookupUserTenant(username);
        if (tenantId == null || userTenant.isEmpty()) {
            return ProvinceScope.unresolved();
        }
        boolean confirmed = userTenant.get().equalsIgnoreCase(tenantId);
        boolean province = confirmed && provinceTenantIds.contains(tenantId.toUpperCase(Locale.ROOT));
        return ProvinceScope.builder().resolved(true).province(province).tenantId(tenantId).build();
    }

    public Set<String> currentRoles() {
        Set<String> roles = currentUserProvider.currentRoles();
        return roles == null ? Set.of() : roles;
    }

    private Optional<String> lookupUserTenant(String username) {
        try {
            Optional<String> tenant = userTenantLookup.apply(username);
            return tenant == null ? Optional.empty() : tenant;
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
