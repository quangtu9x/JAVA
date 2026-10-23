package com.td.application.tonghop.national;

import org.junit.jupiter.api.Test;

import static com.td.application.tonghop.national.NationalExchangeTestSupport.policy;
import static com.td.application.tonghop.national.NationalExchangeTestSupport.policyWithProfileTenant;
import static org.assertj.core.api.Assertions.assertThat;

class NationalExchangeAccessPolicyTest {

    @Test
    void superAccountSeesWholeProvinceFromAnyTenant() {
        NationalExchangeAccess access = policy("admin", "H05_01").current();
        assertThat(access.isCanView()).isTrue();
        assertThat(access.isProvince()).isTrue();
        assertThat(access.isCanAudit()).isTrue();
    }

    @Test
    void platformAdminSeesWholeProvince() {
        assertThat(policy("root01", "H05_02", "PLATFORM_ADMIN").current().isProvince()).isTrue();
    }

    @Test
    void adminOfProvinceTenantSeesWholeProvince() {
        NationalExchangeAccess access = policy("qtv.tinh", "H05", "ADMIN").current();
        assertThat(access.isProvince()).isTrue();
        assertThat(access.isCanResend()).isTrue();
    }

    @Test
    void unitAdminIsScopedToOwnTenantWithAllPermissions() {
        NationalExchangeAccess access = policy("qtv.skhcn", "H05_01", "ADMIN").current();
        assertThat(access.isCanView()).isTrue();
        assertThat(access.isProvince()).isFalse();
        assertThat(access.getTenantId()).isEqualTo("H05_01");
        assertThat(access.isCanAudit()).isTrue();
        assertThat(access.isCanReplay()).isTrue();
        assertThat(access.isCanResend()).as("gửi lại ra Trục chỉ cho phạm vi toàn tỉnh").isFalse();
    }

    @Test
    void resendCodeAtUnitScopeStillCannotResend() {
        NationalExchangeAccess access = policy("vt", "H05_01", "NATIONAL_EXCHANGE_VIEW", "NATIONAL_EXCHANGE_RESEND")
            .current();
        assertThat(access.isCanResend()).isFalse();
        assertThat(policy("vt", "H05", "NATIONAL_EXCHANGE_VIEW", "NATIONAL_EXCHANGE_RESEND").current().isCanResend())
            .isTrue();
    }

    @Test
    void viewPermissionWithoutAuditIsMasked() {
        NationalExchangeAccess access = policy("vanthu", "H05", "USER", "NATIONAL_EXCHANGE_VIEW").current();
        assertThat(access.isCanView()).isTrue();
        assertThat(access.isProvince()).isTrue();
        assertThat(access.isCanAudit()).isFalse();
        assertThat(access.isCanResend()).isFalse();
    }

    @Test
    void provinceTenantMatchIsCaseInsensitive() {
        assertThat(policy("u", "h05", "NATIONAL_EXCHANGE_VIEW").current().isProvince()).isTrue();
    }

    @Test
    void regularUserIsDenied() {
        assertThat(policy("chuyenvien", "H05", "USER", "CVQUANTRI").current().isCanView()).isFalse();
    }

    @Test
    void scopedUserWithoutTenantIsDenied() {
        assertThat(policy("qtv", null, "ADMIN").current().isCanView()).isFalse();
    }

    @Test
    void unitAdminFallingBackToDefaultProvinceTenantIsDenied() {
        // Không suy được tenant từ hồ sơ → TenantResolver dùng mặc định H05 (tenant cấp tỉnh): KHÔNG được xem toàn tỉnh.
        NationalExchangeAccess access = policyWithProfileTenant("qtv.moi", "H05", null, "ADMIN").current();
        assertThat(access.isCanView()).isFalse();
    }

    @Test
    void tokenTenantDifferentFromProfileIsScopedNotProvince() {
        NationalExchangeAccess access = policyWithProfileTenant("qtv", "H05", "H05_01", "ADMIN").current();
        assertThat(access.isCanView()).isTrue();
        assertThat(access.isProvince()).isFalse();
        assertThat(access.getTenantId()).isEqualTo("H05");
    }

    @Test
    void superAccountDoesNotNeedProfileTenant() {
        NationalExchangeAccess access = policyWithProfileTenant("admin", "H05", null).current();
        assertThat(access.isProvince()).isTrue();
    }

    @Test
    void profileLookupFailureIsTreatedAsUnknownTenant() {
        NationalExchangeAccessPolicy failing = new NationalExchangeAccessPolicy(new com.td.application.tonghop.ProvinceScopePolicy(
            new com.td.application.common.security.CurrentUserProvider() {
                @Override
                public java.util.Optional<String> currentUsername() {
                    return java.util.Optional.of("qtv");
                }

                @Override
                public java.util.Set<String> currentRoles() {
                    return java.util.Set.of("ADMIN");
                }
            },
            () -> java.util.Optional.of("H05"),
            u -> {
                throw new IllegalStateException("identity DB down");
            },
            "H05"));
        assertThat(failing.current().isCanView()).isFalse();
    }

    @Test
    void anonymousIsDenied() {
        assertThat(policy(null, "H05", "ADMIN").current().isCanView()).isFalse();
    }
}
