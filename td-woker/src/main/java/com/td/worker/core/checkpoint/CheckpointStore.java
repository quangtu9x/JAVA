package com.td.worker.core.checkpoint;

import com.td.worker.core.envelope.MessageEnvelope;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lưu trữ checkpoint/idempotency persistent (Worker dùng PostgreSQL làm nguồn sự thật về
 * trạng thái xử lý — KHÔNG dựa vào memory/cache, docs/promt.txt).
 *
 * <p>Mọi thao tác đều idempotent + an toàn khi N worker cùng chạy:
 * <ul>
 *   <li>{@link #initializeUnits} dùng {@code INSERT ... ON CONFLICT DO NOTHING}.</li>
 *   <li>{@link #claim} dùng UPDATE có điều kiện trạng thái — chỉ 1 worker thắng.</li>
 *   <li>Unit DONE không bao giờ bị xử lý lại (kể cả redelivery/restart).</li>
 * </ul>
 */
public interface CheckpointStore {

    /** Lưu message gốc (idempotent theo messageId) — audit + tái dựng sau này. */
    int storeMessage(MessageEnvelope envelope);

    /** Insert tất cả unit của message (idempotent theo uniqueKey). Trả về số unit MỚI insert. */
    int initializeUnits(MessageEnvelope envelope);

    /**
     * Claim 1 unit để xử lý. Atomic:
     * <ul>
     *   <li>PENDING/RETRY đã tới hạn -> PROCESSING (attempts+1).</li>
     *   <li>PROCESSING có lease hết hạn -> được claim lại (crash recovery).</li>
     *   <li>Các trạng thái khác (DONE/FAILED) hoặc lease chưa hết -> không claim được (trả empty).</li>
     * </ul>
     */
    Optional<UnitRecord> claim(String uniqueKey, String workerId, int leaseSeconds);

    /**
     * Gia hạn lease (heartbeat) cho các unit worker này ĐANG xử lý — lease hết hạn chỉ khi worker chết
     * thật / treo quá {@code max-processing-time}, không phải vì handler chạy lâu hơn lease.
     *
     * @return các uniqueKey KHÔNG gia hạn được (đã mất claim: worker khác nhận lại / đã settle)
     */
    List<String> renewLeases(String workerId, List<String> uniqueKeys, int leaseSeconds);

    /** Đánh dấu thành công — chỉ hiệu lực nếu đúng worker đang giữ claim. */
    boolean complete(String uniqueKey, String workerId);

    /**
     * Đánh dấu lỗi sau 1 lần thử — tự quyết định RETRY hay FAILED (attempts >= max_attempts).
     * {@code nextRetryAt} là thời điểm retry theo exponential backoff do unit processor tính.
     * Chỉ hiệu lực nếu đúng worker đang giữ claim.
     */
    boolean fail(String uniqueKey, String workerId, String error, Instant nextRetryAt);

    /** Đợi dependency/handler khôi phục; không tiêu số lần retry nghiệp vụ. */
    boolean defer(String uniqueKey, String workerId, String error, Instant nextCheckAt);

    /** Tìm unit RETRY đã tới hạn (dùng cho retry poller). */
    List<String> findDueRetries(int limit);

    /** Chuyển PROCESSING hết lease -> RETRY (watchdog crash recovery). */
    int requeueExpiredLeases(Duration grace, int limit);

    /** Chuyển PENDING quá lâu -> RETRY (phòng crash trước khi dispatch). */
    int requeueStalePending(Duration age, int limit);

    /** Đếm trạng thái theo taskType (metrics/giám sát DLQ-equivalent). */
    Map<UnitStatus, Long> countByStatus(String taskType);

    /** Đếm trạng thái của 1 message (dùng cho ACK policy). */
    Map<UnitStatus, Long> countByStatusForMessage(String messageId);

    /** FAILED ngay lập tức (không retry) — dùng cho lỗi cấu hình/poison unit. */
    boolean permanentFail(String uniqueKey, String workerId, String error);

    Optional<UnitRecord> findByUniqueKey(String uniqueKey);
}
