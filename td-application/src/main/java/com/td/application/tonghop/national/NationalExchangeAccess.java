package com.td.application.tonghop.national;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quyền + phạm vi dữ liệu của người dùng hiện tại trên màn Trục quốc gia (tính ở server, FE chỉ dùng để ẩn/hiện).
 * Xem {@link NationalExchangeAccessPolicy}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationalExchangeAccess {

    private boolean canView;
    /** true = xem toàn tỉnh; false = chỉ gói của {@link #tenantId}. */
    private boolean province;
    /** Tenant đang đăng nhập (phạm vi khi {@code province=false}). */
    private String tenantId;
    private String tenantName;
    private boolean canDownload;
    private boolean canReplay;
    private boolean canResend;
    /** Xem lỗi kỹ thuật chi tiết, từng lần thao tác Trục, người thao tác quản trị. */
    private boolean canAudit;
    /** Hệ thống đã bật thao tác quản trị (gateway có bộ thực thi) — {@code NATIONAL_EXCHANGE_OPERATIONS_ENABLED}. */
    private boolean operationsEnabled;
    /** Hệ thống đã cấu hình endpoint tải gói của gateway — {@code NATIONAL_EXCHANGE_GATEWAY_URL}. */
    private boolean downloadEnabled;

    public static NationalExchangeAccess denied() {
        return new NationalExchangeAccess();
    }
}
