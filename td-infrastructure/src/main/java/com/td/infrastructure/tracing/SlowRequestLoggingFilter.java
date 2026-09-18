package com.td.infrastructure.tracing;

import com.td.infrastructure.errortracking.ErrorTrackingProperties;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đo thời gian xử lý mỗi request — ghi 1 "log hệ thống" ({@code LogType.SYSTEM}) khi vượt ngưỡng
 * cấu hình ({@code app.error-tracking.slow-request.*}), KỂ CẢ KHI request thành công (không có
 * exception nào) — đúng yêu cầu phát hiện sự cố "chậm"/"treo" ở tầng hệ thống, tách biệt khỏi lỗi
 * nghiệp vụ cụ thể.
 *
 * <p>Đặt ngay sau {@link RequestTraceIdFilter} ({@code HIGHEST_PRECEDENCE + 1}) để đo trọn thời
 * gian của gần như toàn bộ pipeline còn lại (bảo mật, tenant routing, controller...).</p>
 *
 * <p>2 dependency đều tùy chọn ({@code required = false}) — {@code Filter} bean bị
 * {@code @WebMvcTest} tự động quét vào MỌI context (khác với {@code @Service} thường), nên phải
 * chịu được trường hợp {@code ErrorTrackingService}/{@code ErrorTrackingProperties} vắng mặt trong
 * các context hẹp (xem lý do tương tự tại {@code GlobalExceptionHandler}).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class SlowRequestLoggingFilter extends OncePerRequestFilter {

    @Setter
    @Autowired(required = false)
    private ErrorTrackingService errorTrackingService;

    @Setter
    @Autowired(required = false)
    private ErrorTrackingProperties errorTrackingProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isSlowRequestDetectionEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (durationMs >= errorTrackingProperties.getSlowRequest().getWarnThresholdMs()) {
                captureSlowRequest(request, response, durationMs);
            }
        }
    }

    private boolean isSlowRequestDetectionEnabled() {
        return errorTrackingService != null
            && errorTrackingProperties != null
            && errorTrackingProperties.isEnabled()
            && errorTrackingProperties.getSlowRequest().isEnabled();
    }

    private void captureSlowRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        try {
            HttpStatus status = HttpStatus.resolve(response.getStatus());
            errorTrackingService.captureSlowRequest(request, status, durationMs);
        } catch (Exception ex) {
            // Khong duoc de loi cua chinh viec ghi log lam anh huong request that (da tra response roi).
            log.error("Khong the ghi log request cham", ex);
        }
    }
}
