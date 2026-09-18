package com.td.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.infrastructure.errortracking.ErrorTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Trả 401 theo envelope {@code ApiResponse} thay vì response mặc định của Spring Security,
 * cho cả 2 luồng: {@code Tdtoken} verify thất bại ({@link TdTokenAuthenticationException}) và
 * request không có token nào / token Keycloak không hợp lệ.
 */
@Component
@RequiredArgsConstructor
public class ApiResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String DEFAULT_MESSAGE = "Chua xac thuc hoac token khong hop le";

    private final ObjectMapper objectMapper;

    /**
     * Tùy chọn ({@code required = false}): các test/context hẹp không nạp Error Tracking (không có
     * DataSource riêng) vẫn hoạt động bình thường, chỉ không sinh Error ID — xem
     * {@code GlobalExceptionHandler} để biết lý do dùng field injection tùy chọn thay vì constructor.
     */
    @Setter
    @Autowired(required = false)
    private ErrorTrackingService errorTrackingService;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String message = authException instanceof TdTokenAuthenticationException
            ? authException.getMessage()
            : DEFAULT_MESSAGE;
        String errorId = errorTrackingService != null
            ? errorTrackingService.captureError(request, HttpStatus.UNAUTHORIZED, authException)
            : null;
        SecurityResponseWriter.writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, message, errorId);
    }
}
