package com.td.worker.core.logging;

import org.slf4j.MDC;

/**
 * Structured logging context — set/clear MDC các key bắt buộc của Worker:
 * {@code correlationId}, {@code messageId}, {@code tenantId}, {@code unitId}, {@code taskType}.
 * Logback pattern trong logback-spring.xml đã tự in các key này.
 */
public final class CorrelationContext {

    private CorrelationContext() {
    }

    public static void putMessage(String correlationId, String messageId, String taskType) {
        MDC.put("correlationId", correlationId == null ? "" : correlationId);
        MDC.put("messageId", messageId == null ? "" : messageId);
        MDC.put("taskType", taskType == null ? "" : taskType);
    }

    public static void putUnit(String tenantId, String unitId) {
        MDC.put("tenantId", tenantId == null ? "" : tenantId);
        MDC.put("unitId", unitId == null ? "" : unitId);
    }

    public static void clear() {
        MDC.remove("correlationId");
        MDC.remove("messageId");
        MDC.remove("taskType");
        MDC.remove("tenantId");
        MDC.remove("unitId");
    }
}