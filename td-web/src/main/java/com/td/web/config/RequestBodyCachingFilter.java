package com.td.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Locale;

/**
 * Bọc request bằng {@link ContentCachingRequestWrapper} để Error Tracking
 * ({@code com.td.infrastructure.errortracking.ErrorTrackingService}) có thể đọc lại body SAU KHI
 * Spring MVC đã đọc nó cho {@code @RequestBody} (InputStream chỉ đọc được 1 lần) — phục vụ chẩn
 * đoán lỗi nhanh: biết chính xác request nào gây lỗi, không chỉ message/stack trace.
 *
 * <p><b>Đặt SAU {@link FormDataToJsonFilter}</b> ({@code @Order(HIGHEST_PRECEDENCE + 20)} so với
 * {@code +10}) — để cache đúng body CUỐI CÙNG mà controller/use case thực sự nhận (JSON đã được quy
 * đổi từ form-data nếu có), hữu ích hơn cho debug so với body gốc dạng form.</p>
 *
 * <p><b>Bỏ qua multipart</b> (file upload): {@code ContentCachingRequestWrapper} không phù hợp với
 * luồng đọc multipart của Spring (rủi ro phá vỡ {@code MultipartFile} binding) và cũng không nên
 * cache nội dung file nhị phân vào log. Sau khi qua {@link FormDataToJsonFilter}, các request
 * multipart TEXT-only (không phải file thật) đã được quy đổi sang {@code application/json} nên vẫn
 * được cache bình thường ở đây — chỉ multipart file-upload THẬT (bị {@code FormDataToJsonFilter} cố
 * ý bỏ qua, vẫn giữ nguyên {@code Content-Type: multipart/form-data}) mới bị loại.</p>
 *
 * <p>Giới hạn {@value #MAX_CACHED_BYTES} byte đầu — chỉ giới hạn phần CACHE cho mục đích log, KHÔNG
 * ảnh hưởng dữ liệu thực tế mà controller/use case nhận được (stream gốc vẫn đọc đầy đủ).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    private static final int MAX_CACHED_BYTES = 8192;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean eligibleForCaching = contentType != null
            && !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");

        if (!eligibleForCaching) {
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(new ContentCachingRequestWrapper(request, MAX_CACHED_BYTES), response);
    }
}
