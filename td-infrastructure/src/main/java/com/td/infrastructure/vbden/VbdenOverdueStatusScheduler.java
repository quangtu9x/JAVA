package com.td.infrastructure.vbden;

import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantContext;
import com.td.infrastructure.tenant.TenantRoutingDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 09/11/2026 (báo lỗi: văn bản đến hạn 31/10 vẫn "Đang xử lý (Trong hạn)"): {@code documents.status} của vbden chỉ
 * được tính lại khi có thao tác (ChuyenXuLy/KetThucXuLy/ThuHoi... qua {@code VbdenStatusService}), nên văn bản
 * nằm yên qua ngày hạn giữ mãi mã "2". Job này lật "2" → "1" (đang xử lý quá hạn) cho văn bản có
 * {@code deadline_date} &lt; hôm nay, lần lượt từng DB đơn vị — cũng là điều kiện để lọt vào các view lọc
 * {@code status='1'} (quahan, dangxuly quá hạn, báo cáo).
 *
 * <p>Chạy lúc khởi động (sửa dữ liệu cũ sau deploy) và đầu mỗi ngày. Idempotent - nhiều instance cùng chạy chỉ là
 * việc thừa. {@code deadline_date} lưu chuỗi "dd/MM/yyyy" (xem PostgresDocumentRepository); chuỗi sai dạng/rỗng
 * bị bỏ qua (văn bản không hạn vẫn mã "2", FE tự hiện "Đang xử lý" - global.jsx#hasVbdenDeadline). Chạm
 * {@code last_modified_on} để kho tổng hợp toàn tỉnh đồng bộ tăng dần nhận thay đổi.</p>
 */
@Component
@Slf4j
public class VbdenOverdueStatusScheduler {

    // Trùng VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY / STATUS_QUA_HAN_DANG_XU_LY (td-application).
    private static final String STATUS_CON_HAN_DANG_XU_LY = "2";
    private static final String STATUS_QUA_HAN_DANG_XU_LY = "1";

    static final String UPDATE_SQL = "UPDATE documents SET status = ?, last_modified_on = NOW()"
        + " WHERE document_type = 'vbden' AND status = ? AND deleted_on IS NULL"
        + " AND (dynamic_fields->>'deadline_date') ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$'"
        + " AND TO_DATE(dynamic_fields->>'deadline_date', 'DD/MM/YYYY') < ?";

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final JdbcTemplate jdbc;

    @Value("${app.vbden.overdue-status.enabled:true}")
    private boolean enabled;

    public VbdenOverdueStatusScheduler(TenantRoutingDataSource tenantRoutingDataSource,
                                       TenantActiveRegistry tenantActiveRegistry) {
        this.tenantRoutingDataSource = tenantRoutingDataSource;
        this.tenantActiveRegistry = tenantActiveRegistry;
        this.jdbc = new JdbcTemplate(tenantRoutingDataSource);
    }

    @Scheduled(initialDelayString = "${app.vbden.overdue-status.initial-delay-ms:90000}", fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        markOverdueAllTenants();
    }

    @Scheduled(cron = "${app.vbden.overdue-status.cron:0 5 0 * * *}")
    public void daily() {
        markOverdueAllTenants();
    }

    void markOverdueAllTenants() {
        if (!enabled) {
            return;
        }
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        for (String tenantId : tenantRoutingDataSource.knownTenantIds()) {
            if (!tenantActiveRegistry.isActive(tenantId)) {
                continue;
            }
            try {
                int updated = TenantContext.runAsWithResult(tenantId,
                    () -> jdbc.update(UPDATE_SQL, STATUS_QUA_HAN_DANG_XU_LY, STATUS_CON_HAN_DANG_XU_LY, today));
                if (updated > 0) {
                    log.info("vbden quá hạn tenant '{}': {} văn bản '2' -> '1'", tenantId, updated);
                }
            } catch (Exception ex) {
                // 1 tenant lỗi không làm hỏng các tenant còn lại.
                log.warn("vbden quá hạn tenant '{}' lỗi: {}", tenantId, ex.getMessage());
            }
        }
    }
}
