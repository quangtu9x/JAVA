package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phạm vi dữ liệu xuyên tenant của người dùng hiện tại trên các màn tổng hợp toàn tỉnh — xem {@link ProvinceScopePolicy}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceScope {

    /** Đã xác định được phạm vi an toàn (có user + tenant được hồ sơ xác nhận, hoặc là tài khoản tổng). */
    private boolean resolved;
    /** Tài khoản tổng: username {@code admin} hoặc {@code PLATFORM_ADMIN}. */
    private boolean superAccount;
    /** true = toàn tỉnh; false = chỉ {@link #tenantId}. */
    private boolean province;
    /** Tenant đang phục vụ request. */
    private String tenantId;
    /** Tên hiển thị tenant — chỉ điền ở API {@code /tonghop/PhamVi} cho FE. */
    private String tenantName;

    public static ProvinceScope unresolved() {
        return new ProvinceScope();
    }

    /** Null = không giới hạn (toàn tỉnh); ngược lại tenant phải ép vào truy vấn. */
    public String restrictTenantId() {
        return province ? null : tenantId;
    }

    /** Dữ liệu của {@code ownerTenantId} có nằm trong phạm vi không. */
    public boolean covers(String ownerTenantId) {
        return resolved && (province || (tenantId != null && tenantId.equalsIgnoreCase(ownerTenantId)));
    }
}
