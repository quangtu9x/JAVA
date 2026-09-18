package com.td.application.messaging;

import com.td.domain.messaging.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Port lưu trữ bảng outbox (BL — "hoàn thiện Gửi văn bản", 04/09/2026). KHÔNG kế thừa
 * {@code IRepository<T>} chung (giống tiền lệ {@code VbdiSendLogDocumentRepository}) — chỉ khai
 * báo đúng thao tác {@link OutboxPublisherService}/{@code OutboxDispatcherScheduler} cần.
 */
public interface OutboxEventRepository {

    <S extends OutboxEvent> S save(S entity);

    /**
     * Lấy batch {@code PENDING} SẴN SÀNG thử (lại) — {@code next_try_at <= now} — CHẠY TRONG
     * DATABASE CỦA TENANT HIỆN TẠI ({@code TenantContext}), khoá bằng {@code FOR UPDATE SKIP
     * LOCKED} để nhiều instance/thread không cùng lấy trùng 1 dòng (chuẩn bị cho scale nhiều
     * instance sau này — {@code docs/architecture/08 §7.1}).
     */
    List<OutboxEvent> lockNextBatch(LocalDateTime now, int batchSize);
}
