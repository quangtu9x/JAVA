package com.td.infrastructure.hosocv;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G8: {@link HttpLgspTokenClient}. Mặc định
 * {@code hosocv.lgsp.enabled=false} — PHẢI trả lỗi nghiệp vụ rõ ràng, KHÔNG được thử kết nối/NPE
 * (đúng tiền lệ {@code MAIL_SENDING_ENABLED=false} BL-221).
 */
class HttpLgspTokenClientTest {

    @Test
    void fetchToken_whenDisabledByDefault_shouldReturnFailureWithoutAttemptingConnection() {
        HttpLgspTokenClient client = new HttpLgspTokenClient();
        ReflectionTestUtils.setField(client, "enabled", false);

        var result = client.fetchToken();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("chưa được cấu hình");
    }

    @Test
    void fetchToken_whenEnabledButMissingEndpoint_shouldReturnFailure() {
        HttpLgspTokenClient client = new HttpLgspTokenClient();
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "tokenEndpoint", "");

        var result = client.fetchToken();

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void fetchToken_whenEndpointUnreachable_shouldReturnFailureNotThrow() {
        HttpLgspTokenClient client = new HttpLgspTokenClient();
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "tokenEndpoint", "http://127.0.0.1:1/unreachable");
        ReflectionTestUtils.setField(client, "basicAuthUsername", "user");
        ReflectionTestUtils.setField(client, "basicAuthPassword", "pass");

        var result = client.fetchToken();

        assertThat(result.isSuccess()).isFalse();
    }
}
