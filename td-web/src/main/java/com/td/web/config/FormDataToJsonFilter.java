package com.td.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filter chuyển đổi request dạng FormData (application/x-www-form-urlencoded
 * hoặc multipart/form-data text-only) sang JSON, giúp tất cả các endpoint
 * POST/PUT/PATCH có thể nhận dữ liệu từ cả JSON body lẫn HTML form / JS FormData
 * mà không cần thay đổi controller hay request DTO.
 *
 * <p><b>Cơ chế:</b>
 * <ol>
 *   <li>Nếu Content-Type là {@code application/x-www-form-urlencoded}: đọc
 *       {@code request.getParameterMap()}, chuyển thành JSON, bọc request với
 *       {@code application/json}.</li>
 *   <li>Nếu Content-Type là {@code multipart/form-data}: đọc từng Part, bỏ qua các
 *       part có file (có {@code filename=} trong Content-Disposition), chuyển text
 *       fields thành JSON.</li>
 *   <li>Endpoint upload file thực sự (path chứa {@code /files} hoặc {@code /upload})
 *       được bỏ qua hoàn toàn để không làm hỏng MultipartFile binding.</li>
 * </ol>
 *
 * <p><b>Type coercion:</b> Giá trị được parse heuristic:
 * {@code "true"/"false"} → Boolean, số nguyên không có leading-zero → Integer/Long,
 * còn lại giữ nguyên là String. Gson/Jackson sẽ deserialize đúng type theo field
 * của DTO đích.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FormDataToJsonFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FormDataToJsonFilter.class);

    /** HTTP methods có thể mang body. */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");

    /**
     * URI segments của các endpoint upload file thực sự.
     * Những request này giữ nguyên multipart/form-data để Spring/controller
     * xử lý {@code @RequestParam MultipartFile}.
     */
    private static final Set<String> FILE_UPLOAD_SEGMENTS = Set.of("/files", "/upload");

    private final ObjectMapper objectMapper;

    @Autowired
    public FormDataToJsonFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String method = request.getMethod();
        if (!WRITE_METHODS.contains(method)) {
            chain.doFilter(request, response);
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null) {
            chain.doFilter(request, response);
            return;
        }

        String ct = contentType.toLowerCase(Locale.ROOT);

        if (ct.startsWith("application/x-www-form-urlencoded")) {
            Map<String, Object> params = readUrlEncodedParams(request);
            chain.doFilter(asJsonRequest(request, params), response);

        } else if (ct.startsWith("multipart/form-data")) {
            // Bỏ qua endpoint upload file thực sự
            String uri = request.getRequestURI();
            boolean isFileUpload = FILE_UPLOAD_SEGMENTS.stream().anyMatch(uri::contains);
            if (isFileUpload) {
                chain.doFilter(request, response);
                return;
            }

            Map<String, Object> params = readMultipartTextParams(request);
            if (params == null) {
                // Đọc multipart thất bại → pass-through nguyên gốc
                chain.doFilter(request, response);
            } else {
                chain.doFilter(asJsonRequest(request, params), response);
            }

        } else {
            chain.doFilter(request, response);
        }
    }

    // ─── Param readers ────────────────────────────────────────────────────────

    /** Đọc tất cả form fields từ URL-encoded body. */
    private Map<String, Object> readUrlEncodedParams(HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0 && !values[0].isBlank()) {
                params.put(key, coerce(values[0]));
            }
        });
        return params;
    }

    /**
     * Đọc text fields từ multipart request (bỏ qua file parts).
     *
     * @return map field → value, hoặc {@code null} nếu parse thất bại
     */
    private Map<String, Object> readMultipartTextParams(HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        try {
            for (Part part : request.getParts()) {
                // Bỏ qua file parts (có filename= trong Content-Disposition)
                String disposition = part.getHeader("Content-Disposition");
                if (disposition != null && disposition.contains("filename=")) {
                    continue;
                }
                String name = part.getName();
                String value = new String(
                        part.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!value.isBlank()) {
                    params.put(name, coerce(value));
                }
            }
        } catch (Exception ex) {
            log.warn("[FormDataToJsonFilter] Không thể đọc multipart parts ({}), request sẽ pass-through nguyên gốc",
                    ex.getMessage());
            return null;
        }
        return params;
    }

    // ─── Wrap helper ─────────────────────────────────────────────────────────

    private HttpServletRequest asJsonRequest(HttpServletRequest original,
                                             Map<String, Object> params)
            throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(params);
        return new JsonBodyWrapper(original, json);
    }

    // ─── Type coercion ────────────────────────────────────────────────────────

    /**
     * Heuristic nhỏ: chuyển boolean/integer literals sang native Java type
     * để JSON output có đúng type. String thuần (kể cả UUID, mã đơn vị…) giữ nguyên.
     *
     * <ul>
     *   <li>"true"/"false" (insensitive) → Boolean</li>
     *   <li>Số nguyên không leading-zero → Integer hoặc Long</li>
     *   <li>Còn lại → String</li>
     * </ul>
     */
    static Object coerce(String value) {
        if ("true".equalsIgnoreCase(value))  return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;

        // Chỉ parse số nếu không có leading-zero (tránh mất "001" → 1)
        if (value.matches("-?(?:0|[1-9]\\d*)")) {
            try {
                long l = Long.parseLong(value);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return value;
    }

    // ─── Request wrapper ──────────────────────────────────────────────────────

    /**
     * Bọc request gốc, thay thế body bằng JSON bytes và đổi Content-Type sang
     * {@code application/json;charset=UTF-8}.
     */
    private static final class JsonBodyWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        JsonBodyWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public String getContentType() {
            return "application/json;charset=UTF-8";
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished()   { return stream.available() == 0; }
                @Override public boolean isReady()      { return true; }
                @Override public void setReadListener(ReadListener rl) { /* no-op */ }
                @Override public int read() throws IOException { return stream.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
