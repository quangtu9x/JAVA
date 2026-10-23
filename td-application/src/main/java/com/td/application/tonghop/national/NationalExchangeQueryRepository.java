package com.td.application.tonghop.national;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Đọc read model quản trị gửi nhận quốc gia trong database RIÊNG của TD Gateway (project {@code tdgateway},
 * V8/V9/V15). CHỈ ĐỌC: không ghi bảng runtime/read model; thao tác quản trị (gửi lại, nhận lại, phát lại) phải
 * qua bảng {@code worker_national_exchange_operation_request} + executor của gateway — chưa có executor nên chưa
 * mở (tdgateway/docs/23).
 *
 * <p>Bean chỉ tồn tại khi bật {@code app.national-exchange.admin.enabled}; use case nhận qua
 * {@code ObjectProvider} để tắt cấu hình không làm hỏng khởi động.</p>
 */
public interface NationalExchangeQueryRepository {

    NationalExchangeSearchResult search(NationalExchangeSearchRequest request, LocalDate from, LocalDate to,
                                        int offset, int limit);

    NationalExchangeSummary summary(NationalExchangeSearchRequest request, LocalDate from, LocalDate to);

    Optional<NationalExchangeDetail> findDetail(String exchangeId);

    /** Tên hiển thị tenant (bảng {@code tenants} control-plane), null nếu không có. */
    default String tenantDisplayName(String tenantId) {
        return null;
    }
}
