package com.td.infrastructure.errortracking;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 1 bản ghi = 1 sự kiện Error Tracking — hoặc {@link LogType#BUSINESS} (exception đi qua
 * {@code GlobalExceptionHandler}/security handler, gắn với 1 request cụ thể) hoặc
 * {@link LogType#SYSTEM} (sự cố hạ tầng: request chậm, deadlock — xem
 * {@code SlowRequestLoggingFilter}/{@code DeadlockWatchdog}). Lưu trong DB Error Tracking RIÊNG
 * BIỆT, tách hoàn toàn khỏi DB nghiệp vụ (xem {@link ErrorTrackingDataSourceConfig}).
 *
 * <p><b>POJO thuần, KHÔNG dùng JPA/Hibernate</b> (không {@code @Entity}) — {@link ErrorLogJdbcRepository}
 * đọc/ghi bằng {@code JdbcTemplate} thuần. Lý do: {@code LocalContainerEntityManagerFactoryBean}
 * (Hibernate) LUÔN cố mở 1 connection đọc JDBC metadata NGAY LÚC BEAN ĐƯỢC TẠO — kể cả đánh dấu
 * {@code @Lazy}/{@code bootstrapMode=LAZY} (đã kiểm chứng bằng test thực tế vẫn không ngăn được) —
 * nếu DB Error Tracking chưa sẵn sàng, việc này ném exception làm SẬP TOÀN BỘ APP lúc khởi động
 * (xem {@code ErrorTrackingStartupResilienceTest}). {@code JdbcTemplate} thuần thì ngược lại: tạo
 * bean không bao giờ chạm DB — chỉ chạm khi code THẬT SỰ gọi query, đúng thời điểm nằm trong
 * try-catch của {@link ErrorTrackingService}.</p>
 *
 * <p>Không phải aggregate root nghiệp vụ (không dùng {@code AuditableEntity}/{@code IAggregateRoot}
 * của {@code td-domain}) — đây thuần là bản ghi vận hành/chẩn đoán.</p>
 *
 * <p><b>Request body</b>: chỉ cache khi request KHÔNG phải multipart (tránh nuốt file upload vào
 * log — xem {@code RequestBodyCachingFilter}), giới hạn kích thước, và đã qua
 * {@link SensitiveDataSanitizer} để che các field như password/token/secret trước khi lưu.</p>
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorLogEntity {

    /** Error ID duy nhất, dạng {@code ERR-<uuid>} — sinh tại {@link ErrorTrackingService}, trả về client. */
    private String errorId;

    /** BUSINESS (exception cụ thể) hay SYSTEM (sự cố hạ tầng) — để quản trị lọc trên UI. */
    private LogType logType;

    /** WARN/ERROR/CRITICAL — để quản trị sắp xếp/ưu tiên xử lý trên UI. */
    private Severity severity;

    private Instant occurredAt;

    /** Null cho log SYSTEM không gắn với 1 request cụ thể (vd. deadlock watchdog). */
    private Integer httpStatus;

    private String httpMethod;

    /** Chỉ path, KHÔNG kèm query string (query có thể chứa token/param nhạy cảm). */
    private String requestPath;

    private String traceId;

    /** Username người dùng hiện tại nếu đã xác thực được tại thời điểm lỗi xảy ra; null nếu chưa xác thực (vd. lỗi 401). */
    private String username;

    private String remoteAddr;

    /** Null cho log SYSTEM không gắn với 1 exception cụ thể (vd. request chậm nhưng không lỗi). */
    private String exceptionClass;

    /** Message mô tả sự kiện — message của exception (BUSINESS) hoặc mô tả sự cố (SYSTEM, vd. "Request cham: 8000ms"). */
    private String message;

    /** Stack trace (BUSINESS) hoặc thread dump (SYSTEM, vd. deadlock watchdog) — đã sanitize + cắt độ dài. */
    private String stackTrace;

    /** Request body (nếu có, không phải multipart) — đã sanitize + cắt độ dài, xem javadoc class. */
    private String requestBody;

    /** Thời gian xử lý request (ms) — chỉ có giá trị cho log SYSTEM loại "request chậm". */
    private Long durationMs;
}
