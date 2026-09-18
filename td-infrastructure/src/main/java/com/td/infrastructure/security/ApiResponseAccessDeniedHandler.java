package com.td.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Trả 403 theo envelope {@code ApiResponse} khi request đã xác thực (Tdtoken hoặc Keycloak)
 * nhưng không đủ quyền ({@code @PreAuthorize}/{@code hasAnyRole} thất bại).
 */
@Component
@RequiredArgsConstructor
public class ApiResponseAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "Khong co quyen thuc hien thao tac nay";

    private final ObjectMapper objectMapper;

    /**
     * Tùy chọn ({@code required = false}) — xem javadoc tương tự tại
     * {@code ApiResponseAuthenticationEntryPoint}/{@code GlobalExceptionHandler}.
     */
    @Setter
    @Autowired(required = false)
    private ErrorTrackingService errorTrackingService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        String errorId = errorTrackingService != null
            ? errorTrackingService.captureError(request, HttpStatus.FORBIDDEN, accessDeniedException)
            : null;
        SecurityResponseWriter.writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, MESSAGE, errorId);
    }
}
