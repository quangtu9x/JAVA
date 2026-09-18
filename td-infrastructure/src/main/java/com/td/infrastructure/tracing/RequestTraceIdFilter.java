package com.td.infrastructure.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Sinh/propagate 1 Trace ID duy nhất cho mỗi request — dùng để nối context Error Tracking
 * ({@code com.td.infrastructure.errortracking.ErrorTrackingService}) với các dòng log khác của
 * cùng request, và để client/hạ tầng upstream tự truyền {@code X-Request-Id} sẵn có (nếu có) thay
 * vì luôn sinh mới.
 *
 * <p>Đặt {@code @Order(HIGHEST_PRECEDENCE)} — chạy trước TOÀN BỘ filter chain, kể cả chain bảo mật
 * của Spring Security (đăng ký như 1 filter duy nhất trong servlet container, xem
 * {@code SecurityConfig}) — để traceId có mặt kể cả khi request bị chặn 401/403 ngay tại filter,
 * chưa tới được Controller/{@code GlobalExceptionHandler}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveIncomingTraceId(request);

        try {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            request.setAttribute(TRACE_ID_REQUEST_ATTRIBUTE, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat dung thread pool - PHAI clear de tranh MDC "ro ri" sang request tiep theo dung lai thread nay.
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveIncomingTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        return (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming.trim();
    }
}
