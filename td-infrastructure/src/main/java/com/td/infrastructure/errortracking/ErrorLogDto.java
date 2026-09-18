package com.td.infrastructure.errortracking;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * DTO tra cứu Error Tracking (chi tiết + kết quả tìm kiếm) — dùng cho tầng API
 * ({@code ErrorTrackingController}), tách khỏi JPA entity {@link ErrorLogEntity} theo đúng quy ước
 * hiện có của project (controller không trả trực tiếp entity JPA).
 */
@Getter
@Builder
public class ErrorLogDto {

    private String errorId;
    private LogType logType;
    private Severity severity;
    private Instant occurredAt;
    private Integer httpStatus;
    private String httpMethod;
    private String requestPath;
    private String traceId;
    private String username;
    private String remoteAddr;
    private String exceptionClass;
    private String message;
    private String stackTrace;
    private String requestBody;
    private Long durationMs;

    static ErrorLogDto from(ErrorLogEntity entity) {
        return ErrorLogDto.builder()
            .errorId(entity.getErrorId())
            .logType(entity.getLogType())
            .severity(entity.getSeverity())
            .occurredAt(entity.getOccurredAt())
            .httpStatus(entity.getHttpStatus())
            .httpMethod(entity.getHttpMethod())
            .requestPath(entity.getRequestPath())
            .traceId(entity.getTraceId())
            .username(entity.getUsername())
            .remoteAddr(entity.getRemoteAddr())
            .exceptionClass(entity.getExceptionClass())
            .message(entity.getMessage())
            .stackTrace(entity.getStackTrace())
            .requestBody(entity.getRequestBody())
            .durationMs(entity.getDurationMs())
            .build();
    }
}
