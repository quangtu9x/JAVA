package com.td.infrastructure.errortracking;

import com.td.application.common.models.PaginationResponse;
import com.td.infrastructure.security.CurrentUserContextResolver;
import com.td.infrastructure.tracing.RequestTraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Điểm ghi lỗi tập trung duy nhất cho Error Tracking: sinh Error ID, thu thập context (user, API,
 * thời điểm, trace ID, status, exception, stack trace, request body), sanitize dữ liệu nhạy cảm rồi
 * lưu vào DB Error Tracking riêng biệt (xem {@link ErrorTrackingDataSourceConfig}). Ghi cả log
 * {@link LogType#BUSINESS} (exception cụ thể — {@link #captureError}) lẫn {@link LogType#SYSTEM}
 * (sự cố hạ tầng — {@link #captureSlowRequest}/{@link #captureSystemEvent}).
 *
 * <p><b>Cam kết KHÔNG BAO GIỜ throw</b>: mọi lỗi phát sinh trong quá trình ghi (DB log down, sai
 * mapping...) chỉ được log cục bộ (logger ứng dụng bình thường) — API nghiệp vụ đang xử lý exception
 * gốc không được phép bị ảnh hưởng bởi sự cố của chính tính năng Error Tracking. Error ID luôn được
 * sinh và trả về (kể cả khi lưu DB thất bại) để client vẫn có 1 mã tham chiếu báo cho support, dù
 * trong trường hợp đó tra cứu theo ID sẽ không ra kết quả — chấp nhận được, vì đã có log cục bộ kèm
 * đúng Error ID đó để đối chiếu thủ công.</p>
 *
 * <p><b>Không log dữ liệu nhạy cảm</b>: KHÔNG capture header (nơi có Authorization/Tdtoken) và
 * KHÔNG capture body của request multipart (file upload) — xem {@code RequestBodyCachingFilter}.
 * Với request có body cache được (JSON/form), body được đưa qua {@link SensitiveDataSanitizer} để
 * che các field password/token/secret trước khi lưu.</p>
 *
 * <p><b>Vì sao {@link ErrorLogJdbcRepository} dùng {@code JdbcTemplate} thuần thay vì JPA</b>: xem
 * javadoc {@code ErrorTrackingDataSourceConfig}/{@code ErrorLogEntity} — tóm tắt: tạo bean
 * {@code JdbcTemplate} không bao giờ chạm DB, nên inject trực tiếp ở đây vẫn AN TOÀN tuyệt đối cho
 * việc khởi động app; kết nối thật sự chỉ xảy ra bên trong các khối try-catch dưới đây khi query
 * thực sự được gọi.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorTrackingService {

    private static final String ERROR_ID_PREFIX = "ERR-";

    private final ErrorLogJdbcRepository errorLogRepository;
    private final ErrorTrackingProperties errorTrackingProperties;
    private final CurrentUserContextResolver currentUserContextResolver;

    /**
     * Sinh Error ID cho exception hiện tại (log {@link LogType#BUSINESS}), cố gắng lưu context vào
     * DB Error Tracking, LUÔN trả về Error ID (không bao giờ throw, không bao giờ trả null).
     */
    public String captureError(HttpServletRequest request, HttpStatus httpStatus, Throwable exception) {
        String errorId = generateErrorId();
        if (!errorTrackingProperties.isEnabled()) {
            log.debug("Error Tracking dang tat - bo qua luu context cho {}", errorId);
            return errorId;
        }

        Severity severity = classifySeverityByHttpStatus(httpStatus);
        save(errorId, () -> ErrorLogEntity.builder()
            .errorId(errorId)
            .logType(LogType.BUSINESS)
            .severity(severity)
            .occurredAt(Instant.now())
            .httpStatus(httpStatus != null ? httpStatus.value() : null)
            .httpMethod(request != null ? request.getMethod() : null)
            .requestPath(request != null ? request.getRequestURI() : null)
            .traceId(resolveTraceId())
            .username(resolveUsername())
            .remoteAddr(request != null ? request.getRemoteAddr() : null)
            .exceptionClass(exception != null ? exception.getClass().getName() : null)
            .message(SensitiveDataSanitizer.sanitizeMessage(exception != null ? exception.getMessage() : null))
            .stackTrace(SensitiveDataSanitizer.sanitizeStackTrace(toStackTraceString(exception)))
            .requestBody(extractRequestBody(request))
            .build());

        return errorId;
    }

    /**
     * Ghi 1 log {@link LogType#SYSTEM} khi request vượt ngưỡng thời gian xử lý cấu hình tại
     * {@code app.error-tracking.slow-request.*} — dùng bởi {@code SlowRequestLoggingFilter}, kể cả
     * khi request KHÔNG có exception nào (thành công nhưng chậm).
     */
    public String captureSlowRequest(HttpServletRequest request, HttpStatus httpStatus, long durationMs) {
        String errorId = generateErrorId();
        if (!errorTrackingProperties.isEnabled()) {
            return errorId;
        }

        long criticalThreshold = errorTrackingProperties.getSlowRequest().getCriticalThresholdMs();
        long warnThreshold = errorTrackingProperties.getSlowRequest().getWarnThresholdMs();
        Severity severity = durationMs >= criticalThreshold ? Severity.CRITICAL : Severity.WARN;
        String message = String.format(Locale.ROOT,
            "Request xu ly cham: %dms (nguong canh bao %dms, nguong nghiem trong %dms)",
            durationMs, warnThreshold, criticalThreshold);

        save(errorId, () -> ErrorLogEntity.builder()
            .errorId(errorId)
            .logType(LogType.SYSTEM)
            .severity(severity)
            .occurredAt(Instant.now())
            .httpStatus(httpStatus != null ? httpStatus.value() : null)
            .httpMethod(request != null ? request.getMethod() : null)
            .requestPath(request != null ? request.getRequestURI() : null)
            .traceId(resolveTraceId())
            .username(resolveUsername())
            .remoteAddr(request != null ? request.getRemoteAddr() : null)
            .message(message)
            .requestBody(extractRequestBody(request))
            .durationMs(durationMs)
            .build());

        return errorId;
    }

    /**
     * Ghi 1 log {@link LogType#SYSTEM} không gắn với request cụ thể nào — dùng bởi
     * {@code DeadlockWatchdog} (JVM deadlock) hoặc các cơ chế giám sát hệ thống khác trong tương lai.
     *
     * @param detail nội dung chi tiết (vd. thread dump) — lưu vào field {@code stackTrace}, đã sanitize.
     */
    public String captureSystemEvent(String message, Severity severity, String detail) {
        String errorId = generateErrorId();
        if (!errorTrackingProperties.isEnabled()) {
            return errorId;
        }

        save(errorId, () -> ErrorLogEntity.builder()
            .errorId(errorId)
            .logType(LogType.SYSTEM)
            .severity(severity != null ? severity : Severity.CRITICAL)
            .occurredAt(Instant.now())
            .traceId(resolveTraceId())
            .message(SensitiveDataSanitizer.sanitizeMessage(message))
            .stackTrace(SensitiveDataSanitizer.sanitizeStackTrace(detail))
            .build());

        return errorId;
    }

    /**
     * Tra cứu 1 Error ID. Trả {@code Optional.empty()} cả khi không tìm thấy LẪN khi bản thân DB
     * Error Tracking đang gặp sự cố (không phân biệt 2 trường hợp này với caller — endpoint tra cứu
     * chỉ nên trả 404, không nên làm sập request bằng 1 exception hạ tầng không liên quan).
     */
    public Optional<ErrorLogDto> findByErrorId(String errorId) {
        if (errorId == null || errorId.isBlank()) {
            return Optional.empty();
        }
        try {
            return errorLogRepository.findById(errorId).map(ErrorLogDto::from);
        } catch (Exception ex) {
            log.error("Khong the tra cuu Error Tracking cho {}", errorId, ex);
            return Optional.empty();
        }
    }

    /**
     * Tìm kiếm/lọc Error Tracking cho UI quản trị — phân trang, sắp xếp mới nhất trước. Tất cả
     * field trong {@code request} đều tùy chọn (null = không lọc theo field đó). Trả trang rỗng nếu
     * DB Error Tracking đang gặp sự cố, không làm sập request tra cứu.
     */
    public PaginationResponse<ErrorLogDto> search(SearchErrorLogsRequest request, int pageNumber, int pageSize) {
        try {
            int safePageNumber = Math.max(pageNumber, 0);
            int safePageSize = Math.max(pageSize, 1);
            long total = errorLogRepository.count(request);
            List<ErrorLogDto> items = errorLogRepository.search(request, safePageNumber, safePageSize).stream()
                .map(ErrorLogDto::from)
                .toList();
            int totalPages = (int) Math.ceil((double) total / safePageSize);
            boolean first = safePageNumber == 0;
            boolean last = safePageNumber >= totalPages - 1;
            return new PaginationResponse<>(items, safePageNumber, safePageSize, total, totalPages, first, last);
        } catch (Exception ex) {
            log.error("Khong the tim kiem Error Tracking", ex);
            return new PaginationResponse<>(List.of(), pageNumber, pageSize, 0, 0, true, true);
        }
    }

    private void save(String errorId, java.util.function.Supplier<ErrorLogEntity> entitySupplier) {
        try {
            errorLogRepository.save(entitySupplier.get());
        } catch (Exception persistFailure) {
            // Khong duoc de loi cua chinh Error Tracking lam hong flow xu ly chinh (request/exception goc).
            // Bao gom ca truong hop DB Error Tracking chua san sang: day la lan dau tien THAT SU chạm
            // vao DB (JdbcTemplate.update() moi thuc su mo connection) - moi ket noi that bai o day,
            // khong o luc app khoi dong (xem javadoc class).
            log.error("Khong the luu Error Tracking cho {} (van tra ve Error ID cho client de doi " +
                "chieu qua log ung dung)", errorId, persistFailure);
        }
    }

    private Severity classifySeverityByHttpStatus(HttpStatus httpStatus) {
        return (httpStatus != null && httpStatus.is5xxServerError()) ? Severity.ERROR : Severity.WARN;
    }

    /**
     * Đọc lại request body ĐÃ ĐƯỢC CACHE bởi {@code RequestBodyCachingFilter} (multipart/file
     * upload không được cache — xem javadoc filter đó). Best-effort: trả {@code null} nếu request
     * không có body/không phải kiểu được cache/chưa từng bị đọc bởi tầng nào phía trên.
     */
    private String extractRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        return SensitiveDataSanitizer.sanitizeRequestBody(new String(content, StandardCharsets.UTF_8));
    }

    private String resolveTraceId() {
        String traceId = MDC.get(RequestTraceIdFilter.TRACE_ID_MDC_KEY);
        return (traceId == null || traceId.isBlank()) ? null : traceId;
    }

    private String resolveUsername() {
        try {
            return currentUserContextResolver.resolveCurrentUserContext()
                .map(ctx -> ctx.getUsername() != null ? ctx.getUsername() : ctx.getUserId())
                .orElse(null);
        } catch (Exception ex) {
            log.debug("Khong the resolve current user cho Error Tracking: {}", ex.getMessage());
            return null;
        }
    }

    private String toStackTraceString(Throwable exception) {
        if (exception == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String generateErrorId() {
        return ERROR_ID_PREFIX + UUID.randomUUID();
    }
}
