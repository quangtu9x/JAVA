package com.td.application.tonghop.national;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.tonghop.ProvinceScopePolicy;

import java.util.Optional;
import java.util.Set;

/** Tạo {@link NationalExchangeAccessPolicy} với người dùng/tenant giả — không cần Mockito. */
final class NationalExchangeTestSupport {

    private NationalExchangeTestSupport() {
    }

    /** Hồ sơ người dùng suy ra đúng tenant đang đăng nhập (trường hợp bình thường). */
    static NationalExchangeAccessPolicy policy(String username, String tenantId, String... roles) {
        return policyWithProfileTenant(username, tenantId, tenantId, roles);
    }

    /** {@code profileTenant} = kết quả ResolveUserTenantUseCase (null = không suy được → request dùng tenant mặc định). */
    static NationalExchangeAccessPolicy policyWithProfileTenant(String username, String tenantId, String profileTenant,
                                                                String... roles) {
        return new NationalExchangeAccessPolicy(scopePolicy(username, tenantId, profileTenant, roles));
    }

    static ProvinceScopePolicy scopePolicy(String username, String tenantId, String profileTenant, String... roles) {
        CurrentUserProvider user = new CurrentUserProvider() {
            @Override
            public Optional<String> currentUsername() {
                return Optional.ofNullable(username);
            }

            @Override
            public Set<String> currentRoles() {
                return Set.of(roles);
            }
        };
        CurrentTenantProvider tenant = () -> Optional.ofNullable(tenantId);
        return new ProvinceScopePolicy(user, tenant, u -> Optional.ofNullable(profileTenant), "H05");
    }

    static NationalExchangeAccessPolicy provinceAdmin() {
        return policy("admin", "H05", "ADMIN");
    }
}
