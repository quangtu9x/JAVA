package com.td.infrastructure.errortracking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataSanitizerTest {

    @Test
    void sanitizeMessage_shouldRedactBearerToken() {
        String message = "Loi goi API voi header Authorization: Bearer abc.def-123_XYZ";

        String result = SensitiveDataSanitizer.sanitizeMessage(message);

        assertThat(result).doesNotContain("abc.def-123_XYZ").contains("***REDACTED***");
    }

    @Test
    void sanitizeMessage_shouldRedactPasswordKeyValue() {
        String message = "Dang nhap that bai voi password=SuperSecret123";

        String result = SensitiveDataSanitizer.sanitizeMessage(message);

        assertThat(result).doesNotContain("SuperSecret123").contains("password=***REDACTED***");
    }

    @Test
    void sanitizeMessage_shouldRedactJsonStyleSecret() {
        String message = "Request body: {\"username\":\"a\",\"secret\":\"top-secret-value\"}";

        String result = SensitiveDataSanitizer.sanitizeMessage(message);

        assertThat(result).doesNotContain("top-secret-value");
    }

    @Test
    void sanitizeMessage_shouldKeepNonSensitiveMessageUnchanged() {
        String message = "Khong tim thay danh muc voi ID: 123";

        String result = SensitiveDataSanitizer.sanitizeMessage(message);

        assertThat(result).isEqualTo(message);
    }

    @Test
    void sanitizeMessage_shouldReturnNull_whenInputNull() {
        assertThat(SensitiveDataSanitizer.sanitizeMessage(null)).isNull();
    }

    @Test
    void sanitizeRequestBody_shouldRedactPasswordField_inJsonBody() {
        String body = "{\"username\":\"a\",\"password\":\"SuperSecret123\"}";

        String result = SensitiveDataSanitizer.sanitizeRequestBody(body);

        assertThat(result).doesNotContain("SuperSecret123").contains("\"username\":\"a\"");
    }

    @Test
    void sanitizeAndTruncate_shouldTruncateLongText() {
        String longText = "x".repeat(100);

        String result = SensitiveDataSanitizer.sanitizeAndTruncate(longText, 20);

        assertThat(result).hasSize(20 + "... [truncated]".length());
        assertThat(result).startsWith("x".repeat(20));
        assertThat(result).endsWith("... [truncated]");
    }

    @Test
    void sanitizeAndTruncate_shouldNotTruncate_whenWithinLimit() {
        String text = "short message";

        String result = SensitiveDataSanitizer.sanitizeAndTruncate(text, 2000);

        assertThat(result).isEqualTo(text);
    }
}
