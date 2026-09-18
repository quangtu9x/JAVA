package com.td.domain.messaging;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu người dùng 04/09/2026 ("hoàn thiện Gửi văn bản để publish RabbitMQ/Kafka... viết class
 * chung để tái dùng"): bảng outbox GENERIC (không riêng cho vbdi) — theo đúng "Outbox pattern" đã
 * thiết kế ở {@code docs/architecture/08-thay-the-domino-router-voi-rabbitmq-minio-postgresql.md
 * §3.1/§6.2} và cụ thể hoá cho vbdi ở {@code docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-
 * trang-thai.md §6.1}: ghi 1 dòng TRONG CÙNG transaction với nghiệp vụ (không publish trực tiếp
 * trong request thread) — {@link com.td.infrastructure.messaging.OutboxDispatcherScheduler}
 * (module {@code td-infrastructure}) đọc {@code PENDING} định kỳ rồi publish qua
 * {@code MessagePublisher} (Rabbit/Kafka, chọn bằng cấu hình {@code app.messaging.broker}).
 *
 * <p>{@link #id} được DÙNG LẠI làm {@code messageId} của envelope khi publish (kể cả re-publish
 * do lỗi/backoff) — đúng nguyên tắc idempotent-theo-messageId của Worker (checkpoint dedup, xem
 * {@code td-woker/docs/07-idempotency-checkpoint.md}): publish lại nhiều lần vẫn CÙNG 1 messageId,
 * Worker tự loại trùng, không cần logic dedup riêng ở tầng này.</p>
 *
 * <p>{@code payloadJson} lưu SẴN mảng {@code units[]} đã serialize (JSON) — scheduler không cần
 * biết ý nghĩa nghiệp vụ của payload, chỉ đọc lại & publish nguyên văn, đúng tinh thần "outbox
 * generic, không riêng cho vbdi".</p>
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    /** vd {@code "vbdi"} — chỉ để truy vết/lọc, không ảnh hưởng logic publish. */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /** vd id dòng {@code vbdi_send_log_documents} vừa ghi — để truy vết. */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** vd {@code "vbdi.delivery"} — quyết định routing key (Rabbit)/handler (Worker). */
    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** Nguồn phát sinh message — {@code "td-web"} (đúng {@code app.messaging.source}). */
    @Column(name = "source", length = 50)
    private String source;

    /** Mảng {@code units[]} đã serialize JSON — xem {@link com.td.application.messaging.MessageUnitSpec}. */
    @Column(name = "payload_json", columnDefinition = "jsonb", nullable = false)
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_try_at")
    private LocalDateTime nextTryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public OutboxEvent(String aggregateType, UUID aggregateId, String taskType,
            String correlationId, String source, String payloadJson) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.taskType = taskType;
        this.correlationId = correlationId;
        this.source = source;
        this.payloadJson = payloadJson;
        this.status = STATUS_PENDING;
        this.nextTryAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public void markSent() {
        this.status = STATUS_SENT;
    }

    /** Thất bại tạm thời — tăng attempts + backoff, GIỮ {@code PENDING} để scheduler thử lại. */
    public void markRetry(String error, LocalDateTime nextTryAt, int maxAttempts) {
        this.attempts += 1;
        this.lastError = error;
        this.nextTryAt = nextTryAt;
        if (this.attempts >= maxAttempts) {
            this.status = STATUS_FAILED;
        }
    }
}
