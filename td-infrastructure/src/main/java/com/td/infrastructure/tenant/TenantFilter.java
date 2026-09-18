package com.td.infrastructure.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.infrastructure.security.SecurityResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Set {@link TenantContext} cho request hiện tại (BL-303), dựa trên {@link Authentication} đã
 * được xác thực bởi Tdtoken hoặc Keycloak (BL-004) — nên đặt SAU cả 2 filter xác thực trong
 * chain ({@code SecurityConfig}). LUÔN clear trong {@code finally} vì Tomcat dùng thread pool —
 * không clear sẽ làm tenant của request này rò rỉ sang request tiếp theo tái sử dụng cùng thread.
 *
 * <p>BL-117: bắt riêng {@link TenantNotActiveException} (tenant xác định được nhưng đã bị khoá) —
 * trả 403 ngay tại filter, KHÔNG cho vào Controller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String tenantId = tenantResolver.resolve(authentication);
            TenantContext.setCurrentTenant(tenantId);
            // Bugfix 03/09/2026 (báo lỗi người dùng: đăng nhập lại tenant "skhcn" — kể cả tab ẩn
            // danh — tạm thời không thấy văn bản của chính mình, tự khỏi "1 lúc sau"; đã loại trừ
            // FE vì tái hiện được ở tab ẩn danh): log DEBUG tenant/username đã resolve cho MỌI
            // request — nếu lỗi tái diễn, so log này với thời điểm báo lỗi sẽ biết NGAY tenant có bị
            // resolve sai/null hay không, thay vì phải đoán qua nhiều vòng hỏi lại người dùng như đợt
            // trước (xem GetCapBanHanhOptionsUseCase — cùng cách tiếp cận, khác chỗ log).
            if (log.isDebugEnabled()) {
                log.debug("Resolved tenant={} cho request {} {} (authenticated={})",
                    tenantId, request.getMethod(), request.getRequestURI(),
                    authentication != null && authentication.isAuthenticated());
            }
            filterChain.doFilter(request, response);
        } catch (TenantNotActiveException ex) {
            SecurityResponseWriter.writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
