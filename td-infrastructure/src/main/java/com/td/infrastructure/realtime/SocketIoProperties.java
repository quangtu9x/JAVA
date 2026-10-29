package com.td.infrastructure.realtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình server socket.io thông báo realtime ({@code docs/socketio/index_socketio.js}) — đọc từ
 * {@code REALTIME_SOCKET_*} ở {@code JAVA/.env}. Tương đương biến môi trường Notes
 * {@code $tdo_socketio_api_url} + {@code TDKeySettings.MA_TINH} phía Domino.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.realtime.socket")
public class SocketIoProperties {

    /** Tắt hẳn việc đẩy thông báo (nghiệp vụ vẫn chạy bình thường). */
    private boolean enabled = true;

    /**
     * URL REST của server socket, gồm cả {@code /api}, KHÔNG có {@code /} cuối — vd
     * {@code http://10.10.10.116:9010/api}. Rỗng = không gửi.
     */
    private String baseUrl = "";

    /** {@code api_key} server socket kiểm tra (query string). KHÔNG đặt vào biến {@code VITE_*}. */
    private String apiKey = "";

    private int connectTimeoutMillis = 1000;
    private int requestTimeoutMillis = 3000;
}
