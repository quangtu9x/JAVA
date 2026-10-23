package com.td.application.tonghop.national;

import java.util.Optional;

/**
 * GHI hàng đợi lệnh quản trị trong DB TD Gateway ({@code worker_national_exchange_operation_request}, V8) — lần đầu td-web
 * ghi DB gateway (docs/24 §4.3). Chỉ 2 thao tác: tạo lệnh PENDING (idempotent theo key) và huỷ lệnh còn PENDING; mọi
 * chuyển trạng thái khác do bộ thực thi của gateway làm. Mỗi lệnh kèm 1 sự kiện audit {@code actor_type=ADMIN}.
 */
public interface NationalExchangeCommandRepository {

    record NewOperation(String requestId, String operationType, String exchangeId, String tenantId, String agencyCode,
                        String reason, String requestedBy, String requestedByName, String idempotencyKey,
                        String parametersJson) { }

    Optional<NationalExchangeDetail.Operation> findByIdempotencyKey(String idempotencyKey);

    /** Có lệnh cùng loại đang chờ/đang chạy trên gói không (chặn bấm lặp). */
    boolean hasActive(String exchangeId, String operationType);

    NationalExchangeDetail.Operation create(NewOperation operation);

    Optional<NationalExchangeDetail.Operation> find(String requestId);

    /** Huỷ (PENDING → CANCELLED) — false nếu lệnh không còn PENDING. */
    boolean cancel(String requestId, String exchangeId, String cancelledBy, String cancelledByName);
}
