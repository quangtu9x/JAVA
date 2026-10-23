package com.td.worker.transport;

import com.td.worker.core.flow.UnitOutcome;

import java.util.List;

/**
 * Kết quả xử lý 1 message body tại tầng transport.
 *
 * @param type      PARSE_ERROR | FLOW_OK | TRANSIENT_ERROR
 * @param canAck    (FLOW_OK) được phép ACK theo policy — transport quyết định ACK/nack/commit
 * @param units     (FLOW_OK) chi tiết từng unit đã chờ xong lượt này — xem {@link UnitOutcome};
 *                  rỗng cho PARSE_ERROR/TRANSIENT_ERROR (chưa chạy tới unit nào). RabbitMQ/Kafka
 *                  listener hiện có KHÔNG cần field này (chỉ dùng {@code canAck}) — thêm để HTTP
 *                  debug endpoint trả được payload nghiệp vụ thật mà không đổi hợp đồng ACK hiện có.
 */
public record RoutedResult(Type type, boolean canAck, String detail, List<UnitOutcome> units) {

    public enum Type {
        /** Envelope không hợp lệ — phải DLQ (không retry vô ích). */
        PARSE_ERROR,
        /** Flow chạy xong; canAck theo ACK policy. */
        FLOW_OK,
        /** Lỗi hạ tầng tạm thời (vd DB checkpoint down) — transport quyết định requeue/no-commit. */
        TRANSIENT_ERROR
    }

    public static RoutedResult parseError(String detail) {
        return new RoutedResult(Type.PARSE_ERROR, false, detail, List.of());
    }

    public static RoutedResult flow(boolean canAck) {
        return new RoutedResult(Type.FLOW_OK, canAck, null, List.of());
    }

    public static RoutedResult flow(boolean canAck, List<UnitOutcome> units) {
        return new RoutedResult(Type.FLOW_OK, canAck, null, units == null ? List.of() : units);
    }

    public static RoutedResult transientError(String detail) {
        return new RoutedResult(Type.TRANSIENT_ERROR, false, detail, List.of());
    }
}
