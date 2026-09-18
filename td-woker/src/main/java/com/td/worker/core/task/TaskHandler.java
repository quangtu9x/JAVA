package com.td.worker.core.task;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SPI của Worker Core — handler xử lý 1 UNIT của 1 task.
 *
 * <p>Đây chính là điểm mở rộng của generic Worker Platform (docs/promt.txt): mỗi loại task
 * (VBDI, OCR/AI, ES indexing, file processing, data sync, notification, ...) là 1 handler
 * {@code @Component} đăng ký với {@link TaskHandlerRegistry}. Worker Core KHÔNG biết nghiệp vụ.</p>
 */
public interface TaskHandler {

    /** Loại task handler này phụ trách, vd "vbdi.delivery", "ocr.extract", "es.index". */
    String taskType();

    /**
     * Xử lý 1 unit.
     *
     * @param context context vòng đời unit (messageId/tenantId/unitId/deadline...)
     * @param payload payload nghiệp vụ của unit (JSON, handler tự parse)
     * @return kết quả unit
     * @throws Exception lỗi tạm thời — Worker quyết định RETRY/FAILED theo maxAttempts
     */
    UnitResult execute(TaskContext context, JsonNode payload) throws Exception;
}