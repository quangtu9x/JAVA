package com.td.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ghi lỗi xác thực/phân quyền ở tầng filter (trước khi vào Controller) ra cùng envelope
 * {@code ApiResponse} với {@code GlobalExceptionHandler} — dùng chung bởi
 * {@link ApiResponseAuthenticationEntryPoint} (401) và {@link ApiResponseAccessDeniedHandler} (403),
 * vì cả 2 xảy ra ở filter chain nên {@code @RestControllerAdvice} không bắt được.
 *
 * <p>BL-117: mở {@code public} (trước đây package-private) để {@code TenantFilter}
 * (package {@code com.td.infrastructure.tenant}) cũng dùng được — trả 403 đúng envelope khi tenant
 * bị vô hiệu hoá ({@code TenantNotActiveException}), thay vì tự viết JSON response trùng lặp.
 */
public final class SecurityResponseWriter {

    private SecurityResponseWriter() {
    }

    public static void writeError(HttpServletResponse response, ObjectMapper objectMapper, int httpStatus, String message)
            throws IOException {
        writeError(response, objectMapper, httpStatus, message, null);
    }

    /**
     * @param errorId Error ID sinh bởi {@code ErrorTrackingService} (có thể {@code null} nếu tính
     *                năng Error Tracking chưa sẵn sàng) — {@code null} bị lược khỏi JSON tự động
     *                nhờ {@code @JsonInclude(NON_NULL)} trên {@code ApiResponse.errorId}.
     */
    public static void writeError(HttpServletResponse response, ObjectMapper objectMapper, int httpStatus, String message, String errorId)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(httpStatus, message, errorId)));
    }
}
