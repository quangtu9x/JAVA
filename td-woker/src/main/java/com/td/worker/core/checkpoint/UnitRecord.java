package com.td.worker.core.checkpoint;

import java.time.Instant;

/**
 * Bản ghi checkpoint 1 unit — được đọc từ {@code worker_units} sau khi claim.
 *
 * @param id            PK dòng
 * @param uniqueKey     tenantId|messageId|unitId
 * @param tenantId      tenant đích
 * @param messageId     message gốc
 * @param unitId        unit trong message
 * @param taskType      loại task
 * @param correlationId mã truy vết
 * @param status        trạng thái hiện tại
 * @param attempts      số lần đã thử (đã tăng sau claim)
 * @param maxAttempts   số retry tối đa
 * @param payloadJson   payload nghiệp vụ (JSON) — nguồn để worker khác tiếp tục
 * @param lastError     lỗi gần nhất
 */
public record UnitRecord(
        long id,
        String uniqueKey,
        String tenantId,
        String messageId,
        String unitId,
        String taskType,
        String correlationId,
        UnitStatus status,
        int attempts,
        int maxAttempts,
        String payloadJson,
        String lastError,
        Instant nextRetryAt) {
}