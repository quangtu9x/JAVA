package com.td.application.common.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.Map;

/**
 * Response envelope thay thế {@code TDMessage} của Domino: {status, code, message, data, properties, total}.
 * Mọi response từ controller phải đi qua wrapper này để FE hiện tại gọi được mà không cần sửa code.
 */
@Getter
public class ApiResponse<T> {

    private final boolean status;
    private final int code;
    private final String message;
    private final T data;
    private final Map<String, Object> properties;
    private final Integer total;

    /**
     * Error Tracking: Error ID tra vết ngược lỗi (xem {@code com.td.infrastructure.errortracking}).
     * Chỉ có giá trị ở response lỗi đi qua {@code GlobalExceptionHandler}/security handler — mọi
     * response khác (success, hoặc lỗi nghiệp vụ qua {@code Result.failure}) giữ nguyên {@code null}.
     * {@code @JsonInclude(NON_NULL)} đảm bảo field này KHÔNG xuất hiện trong JSON khi null, giữ
     * nguyên 100% hình dạng response cũ (6 field) cho mọi trường hợp không liên quan exception.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String errorId;

    private ApiResponse(boolean status, int code, String message, T data, Map<String, Object> properties, Integer total) {
        this(status, code, message, data, properties, total, null);
    }

    private ApiResponse(boolean status, int code, String message, T data, Map<String, Object> properties, Integer total, String errorId) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
        this.properties = properties;
        this.total = total;
        this.errorId = errorId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, 200, "", data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, Map<String, Object> properties) {
        return new ApiResponse<>(true, 200, "", data, properties, null);
    }

    public static <T> ApiResponse<T> success(T data, int total) {
        return new ApiResponse<>(true, 200, "", data, null, total);
    }

    public static <T> ApiResponse<T> success(T data, int total, Map<String, Object> properties) {
        return new ApiResponse<>(true, 200, "", data, properties, total);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, 201, "", data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, 400, friendlyMessage(message), null, null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(false, code, friendlyMessage(message), null, null, null);
    }

    private static final java.util.regex.Pattern VALUE_TOO_LONG =
        java.util.regex.Pattern.compile("value too long for type character varying\\((\\d+)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 19/10/2026 (báo lỗi "Trích yếu nhập quá số ký tự cho phép"): nhiều use case bắt exception rồi trả nguyên
     * câu lỗi PostgreSQL ("... value too long for type character varying(300)") — dịch thành câu người dùng
     * hiểu được, nêu đúng số ký tự tối đa. Các thông báo khác giữ nguyên. FE vẫn báo trước khi nhập
     * (utils/fieldLimits.js); đây là lưới cuối cho trường hợp gọi thẳng API/ô chưa gắn giới hạn.
     */
    public static String friendlyMessage(String message) {
        if (message == null) {
            return null;
        }
        var matcher = VALUE_TOO_LONG.matcher(message);
        if (matcher.find()) {
            return "Dữ liệu nhập vượt quá " + matcher.group(1) + " ký tự cho phép (vd Trích yếu tối đa 300 ký tự) — vui lòng rút gọn.";
        }
        return message;
    }

    /**
     * Dùng bởi {@code GlobalExceptionHandler} và các security handler (401/403) khi Error Tracking
     * đã sinh được Error ID cho exception này — client có thể gửi lại {@code errorId} này cho support
     * để tra cứu context đầy đủ (xem {@code ErrorTrackingController}).
     */
    public static <T> ApiResponse<T> error(int code, String message, String errorId) {
        return new ApiResponse<>(false, code, friendlyMessage(message), null, null, null, errorId);
    }
}
