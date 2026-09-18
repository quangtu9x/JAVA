package com.td.infrastructure.persistence.repository;

import com.td.domain.messaging.OutboxEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bugfix 04/09/2026 (người dùng báo lỗi khởi động thật: "No qualifying bean of type
 * OutboxEventJpaRepository") — di chuyển từ {@code com.td.infrastructure.messaging} sang ĐÚNG
 * package repository "primary" (tenant-routed) mà {@code PrimaryJpaRepositoriesConfig
 * @EnableJpaRepositories(basePackages = "com.td.infrastructure.persistence.repository")} thật sự
 * quét — {@code outbox_events} là bảng NGHIỆP VỤ theo tenant (giống {@code vbdi_send_log_documents}),
 * KHÔNG phải bảng control-plane/identity, nên phải cùng package với các repository primary khác
 * (vd {@code VbdiSendLogDocumentJpaRepository}) để Spring Data JPA thực sự tạo bean.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} — khoá đúng batch đang lấy, KHÔNG chặn instance/thread khác lấy
 * batch tiếp theo (chuẩn bị an toàn cho nhiều instance app sau này, đúng
 * {@code docs/architecture/08 §7.1}). Native SQL bắt buộc vì JPQL không hỗ trợ {@code SKIP LOCKED}.</p>
 */
public interface OutboxEventJpaRepository extends BaseRepository<OutboxEvent> {

    @Query(value = """
        SELECT * FROM outbox_events
         WHERE status = 'PENDING' AND next_try_at <= :now
         ORDER BY created_on ASC
         LIMIT :batchSize
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    /**
     * Bugfix 05/09/2026 (người dùng báo: message bị RabbitMQ "trả lại" — NO_ROUTE, chưa có consumer
     * bind queue — vẫn bị đánh dấu {@code SENT} vì confirm/return đến BẤT ĐỒNG BỘ, sau khi
     * {@code OutboxTenantDispatcher} đã lạc quan gọi {@code markSent()}): chuyển NGƯỢC lại
     * {@code PENDING} để lần poll sau tự thử lại — CHỈ khi đang ở {@code SENT} (tránh ghi đè nếu
     * dòng đã bị xử lý khác đi vì lý do gì đó, dù hiếm).
     *
     * <p>Bugfix 05/09/2026 (LẦN 2 — người dùng báo lỗi thật: {@code InvalidDataAccessApiUsageException:
     * Executing an update/delete query}): khác method CRUD chuẩn (kế thừa {@code SimpleJpaRepository},
     * TỰ có transaction), method {@code @Modifying @Query} tự khai báo KHÔNG tự động có transaction —
     * phải thêm {@code @Transactional} tường minh (gọi từ {@code OutboxDeliveryFeedbackHandler}, 1
     * bean KHÁC không có transaction bao quanh, vì đây là callback bất đồng bộ của Rabbit client).</p>
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE OutboxEvent e SET e.status = 'PENDING', e.attempts = e.attempts + 1,
               e.lastError = :reason, e.nextTryAt = :nextTryAt
         WHERE e.id = :id AND e.status = 'SENT'
        """)
    int markUndeliveredForRetry(@Param("id") UUID id, @Param("reason") String reason,
        @Param("nextTryAt") LocalDateTime nextTryAt);
}
