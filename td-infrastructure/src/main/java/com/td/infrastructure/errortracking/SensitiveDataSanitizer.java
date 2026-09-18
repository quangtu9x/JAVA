package com.td.infrastructure.errortracking;

import java.util.regex.Pattern;

/**
 * Che dấu các chuỗi có khả năng nhạy cảm (token, password, secret...) trước khi lưu vào Error
 * Tracking DB, và cắt bớt độ dài để tránh phình bảng vô hạn khi exception message/stack trace quá
 * dài (ví dụ exception lồng nhiều tầng cause).
 *
 * <p>Lưu ý: {@link ErrorTrackingService} chủ động KHÔNG capture request body/header (nơi thường
 * chứa secret thật như {@code Authorization}, {@code Tdtoken}, mật khẩu form-data) — class này chỉ
 * là lớp phòng vệ bổ sung (defense-in-depth) cho trường hợp message/stack trace của exception vô
 * tình echo lại giá trị nhạy cảm (ví dụ exception validate in ra chuỗi input gốc).</p>
 */
final class SensitiveDataSanitizer {

    private static final int DEFAULT_MESSAGE_MAX_LENGTH = 2000;
    private static final int DEFAULT_STACK_TRACE_MAX_LENGTH = 8000;
    private static final int DEFAULT_REQUEST_BODY_MAX_LENGTH = 4000;
    private static final String REDACTED = "***REDACTED***";

    // Bearer <token>
    private static final Pattern BEARER_TOKEN_PATTERN =
        Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-]+");

    // password=..., "password": "...", secret=..., token=... (JSON hoặc query-string style)
    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
        "(?i)(\"?(?:password|passwd|pwd|secret|token|access_token|refresh_token|api[_-]?key)\"?\\s*[:=]\\s*)\"?[^\\s\"&,}]+");

    private SensitiveDataSanitizer() {
    }

    /**
     * Redact pattern nhạy cảm rồi cắt độ dài theo {@code maxLength}. Trả về {@code null} nếu input
     * {@code null} (giữ nguyên ngữ nghĩa "không có message").
     */
    static String sanitizeAndTruncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        String redacted = KEY_VALUE_SECRET_PATTERN.matcher(text).replaceAll("$1" + REDACTED);
        redacted = BEARER_TOKEN_PATTERN.matcher(redacted).replaceAll(REDACTED);

        return truncate(redacted, maxLength);
    }

    static String sanitizeMessage(String message) {
        return sanitizeAndTruncate(message, DEFAULT_MESSAGE_MAX_LENGTH);
    }

    static String sanitizeStackTrace(String stackTrace) {
        return sanitizeAndTruncate(stackTrace, DEFAULT_STACK_TRACE_MAX_LENGTH);
    }

    /**
     * Sanitize request body (JSON/form đã cache bởi {@code RequestBodyCachingFilter}) — che các
     * field password/token/secret bằng regex tương tự message/stack trace (best-effort, không parse
     * JSON đầy đủ) rồi cắt độ dài tránh phình bảng với payload lớn.
     */
    static String sanitizeRequestBody(String requestBody) {
        return sanitizeAndTruncate(requestBody, DEFAULT_REQUEST_BODY_MAX_LENGTH);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [truncated]";
    }
}
