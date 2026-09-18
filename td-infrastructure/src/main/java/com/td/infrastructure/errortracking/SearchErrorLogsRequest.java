package com.td.infrastructure.errortracking;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tham số tìm kiếm/lọc Error Tracking cho UI quản trị — tất cả field đều tùy chọn (null = không lọc).
 */
@Getter
@Builder
public class SearchErrorLogsRequest {
    private LogType logType;
    private Severity severity;
    private Integer httpStatus;
    private String username;
    /** Tìm gần đúng (LIKE, không phân biệt hoa/thường) trên message/exceptionClass/requestPath. */
    private String keyword;
    private Instant fromDate;
    private Instant toDate;
}
