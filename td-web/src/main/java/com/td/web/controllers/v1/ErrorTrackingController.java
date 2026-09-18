package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.infrastructure.errortracking.ErrorLogDto;
import com.td.infrastructure.errortracking.LogType;
import com.td.infrastructure.errortracking.SearchErrorLogsRequest;
import com.td.infrastructure.errortracking.Severity;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Tra cứu Error Tracking — dùng để support/vận hành đối chiếu ngược 1 lỗi cụ thể mà người dùng/FE
 * báo lại (Error ID trả kèm trong {@code ApiResponse.errorId} của mọi response lỗi đi qua
 * {@code GlobalExceptionHandler}/security handler), hoặc để quản trị chủ động rà soát/lọc log theo
 * {@link LogType} (nghiệp vụ/hệ thống), mức độ, thời gian... phục vụ giao diện quản trị.
 *
 * <p>Chỉ {@code ADMIN} được xem — dữ liệu gồm stack trace/request body/username/IP là thông tin
 * vận hành nội bộ, không dành cho end-user thông thường.</p>
 */
@RestController
@RequestMapping("/api/v1/error-logs")
@RequiredArgsConstructor
@Tag(name = "ErrorTracking", description = "Tra cứu/tìm kiếm Error Tracking (nội bộ/vận hành)")
public class ErrorTrackingController extends BaseController {

    private final ErrorTrackingService errorTrackingService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tìm kiếm/lọc Error Tracking cho giao diện quản trị",
        description = "Lọc theo logType (BUSINESS/SYSTEM) để phân biệt lỗi nghiệp vụ với sự cố hệ " +
            "thống (request chậm, deadlock...), severity (WARN/ERROR/CRITICAL), khoảng thời gian, " +
            "username, HTTP status, hoặc từ khóa (tìm gần đúng trên message/exception/path). Tất cả " +
            "tham số đều tùy chọn — không truyền gì trả về toàn bộ log, mới nhất trước.")
    public ResponseEntity<ApiResponse<List<ErrorLogDto>>> search(
            @Parameter(description = "Loại log: BUSINESS (lỗi nghiệp vụ) hoặc SYSTEM (sự cố hệ thống)")
            @RequestParam(required = false) LogType logType,
            @Parameter(description = "Mức độ: WARN, ERROR, CRITICAL")
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) String username,
            @Parameter(description = "Tìm gần đúng trên message/exceptionClass/requestPath")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "ISO-8601, ví dụ 2026-08-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {
        var searchRequest = SearchErrorLogsRequest.builder()
            .logType(logType)
            .severity(severity)
            .httpStatus(httpStatus)
            .username(username)
            .keyword(keyword)
            .fromDate(fromDate)
            .toDate(toDate)
            .build();
        return ok(errorTrackingService.search(searchRequest, pageNumber, pageSize));
    }

    @GetMapping("/{errorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tra cứu context đầy đủ của 1 lỗi theo Error ID",
        description = "Trả về user, API, thời điểm, trace ID, HTTP status, exception, stack trace, " +
            "request body (nếu có, đã che dữ liệu nhạy cảm)... đã ghi tại thời điểm lỗi xảy ra. " +
            "Trả 404 nếu Error ID không tồn tại (đã bị dọn tự động sau retention, hoặc chưa từng " +
            "được lưu do Error Tracking DB gặp sự cố).")
    public ResponseEntity<ApiResponse<ErrorLogDto>> getByErrorId(
            @Parameter(description = "Error ID trả về trong response lỗi, dạng ERR-<uuid>", required = true)
            @PathVariable String errorId) {
        return errorTrackingService.findByErrorId(errorId)
            .map(this::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "Khong tim thay Error Tracking voi ID: " + errorId)));
    }
}
