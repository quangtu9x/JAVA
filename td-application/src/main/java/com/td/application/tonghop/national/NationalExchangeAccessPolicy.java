package com.td.application.tonghop.national;

import com.td.application.tonghop.ProvinceScope;
import com.td.application.tonghop.ProvinceScopePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Phân quyền màn Trục quốc gia (tdgateway/docs/24 §2.8 — người dùng chốt 22/10/2026).
 *
 * <p><b>Quyền</b> = mã quyền Nhóm vai trò ({@code app_permissions}, seed V1.0.76) — authority
 * {@code ROLE_<MÃ>}:</p>
 * <ul>
 *   <li>{@link #VIEW} xem; {@link #DOWNLOAD} tải gói; {@link #REPLAY} phát lại nội tỉnh; {@link #RESEND} gửi lại ra
 *       Trục; {@link #AUDIT} xem lỗi kỹ thuật chi tiết + từng lần thao tác Trục + người thao tác quản trị.</li>
 *   <li>Role {@code ADMIN} và tài khoản tổng có ĐỦ 5 quyền — giữ tương thích, trước đây màn chỉ mở cho ADMIN.</li>
 *   <li>{@link #RESEND} chỉ hiệu lực ở phạm vi toàn tỉnh (gửi lại tác động cơ quan NGOÀI tỉnh — docs/22 §7).</li>
 * </ul>
 *
 * <p><b>Phạm vi</b> dùng chung {@link ProvinceScopePolicy} với các màn /tonghop khác (tài khoản tổng + tenant cấp tỉnh
 * xem toàn tỉnh, còn lại chỉ tenant mình; tenant chưa được hồ sơ người dùng xác nhận → từ chối).</p>
 */
@Service
public class NationalExchangeAccessPolicy {

    public static final String VIEW = "NATIONAL_EXCHANGE_VIEW";
    public static final String DOWNLOAD = "NATIONAL_EXCHANGE_DOWNLOAD";
    public static final String REPLAY = "NATIONAL_EXCHANGE_REPLAY";
    public static final String RESEND = "NATIONAL_EXCHANGE_RESEND";
    public static final String AUDIT = "NATIONAL_EXCHANGE_AUDIT";

    static final String ADMIN = "ADMIN";

    private final ProvinceScopePolicy scopePolicy;
    private final boolean operationsEnabled;
    private final boolean downloadEnabled;

    @Autowired
    public NationalExchangeAccessPolicy(
            ProvinceScopePolicy scopePolicy,
            @Value("${app.national-exchange.admin.operations-enabled:false}") boolean operationsEnabled,
            @Value("${app.national-exchange.admin.gateway.url:}") String gatewayUrl) {
        this.scopePolicy = scopePolicy;
        this.operationsEnabled = operationsEnabled;
        this.downloadEnabled = gatewayUrl != null && !gatewayUrl.isBlank();
    }

    /** Chỉ đọc (test/khi chưa bật thao tác). */
    public NationalExchangeAccessPolicy(ProvinceScopePolicy scopePolicy) {
        this(scopePolicy, false, "");
    }

    public NationalExchangeAccess current() {
        ProvinceScope scope = scopePolicy.current();
        if (!scope.isResolved()) {
            return NationalExchangeAccess.denied();
        }
        Set<String> roles = scopePolicy.currentRoles();
        boolean all = scope.isSuperAccount() || roles.contains(ADMIN);
        if (!all && !roles.contains(VIEW)) {
            return NationalExchangeAccess.denied();
        }
        boolean province = scope.isProvince();
        return NationalExchangeAccess.builder()
            .canView(true)
            .province(province)
            .tenantId(scope.getTenantId())
            .canDownload(all || roles.contains(DOWNLOAD))
            .canReplay(all || roles.contains(REPLAY))
            // Tải gói/phát lại của người phạm vi đơn vị: use case thao tác sau này PHẢI kiểm gói thuộc tenant (như ChiTiet).
            .canResend(province && (all || roles.contains(RESEND)))
            .canAudit(all || roles.contains(AUDIT))
            .operationsEnabled(operationsEnabled)
            .downloadEnabled(downloadEnabled)
            .build();
    }
}
