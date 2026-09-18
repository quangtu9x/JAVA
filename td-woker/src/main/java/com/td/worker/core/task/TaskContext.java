package com.td.worker.core.task;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

/**
 * Context vòng đời 1 unit — các mã truy vết bắt buộc (tenantId/messageId/unitId/
 * correlationId) + payload + metadata định tuyến, đúng docs/promt.txt mục Production.
 *
 * @param taskType      loại task
 * @param messageId     message gốc
 * @param unitId        unit hiện tại
 * @param correlationId mã truy vết xuyên hệ thống
 * @param tenantId      tenant đích (đã set vào WorkerTenantContext khi gọi handler)
 * @param payload       payload nghiệp vụ
 * @param target        metadata định tuyến tùy chọn (target từ envelope)
 * @param maxAttempts   số retry tối đa của unit
 * @param deadline      deadline cứng của unit (null = dùng unitTimeout cấu hình)
 * @param headers       header của envelope
 */
public record TaskContext(
        String taskType,
        String messageId,
        String unitId,
        String correlationId,
        String tenantId,
        JsonNode payload,
        Map<String, String> target,
        int maxAttempts,
        Instant deadline,
        Map<String, String> headers) {

    public boolean hasDeadline() {
        return deadline != null;
    }
}