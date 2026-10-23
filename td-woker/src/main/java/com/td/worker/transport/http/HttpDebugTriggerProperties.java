package com.td.worker.transport.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình endpoint HTTP debug/test {@code POST /internal/debug/publish} (xem
 * {@link DebugTriggerController}) — dùng để test độc lập không cần dựng RabbitMQ/Kafka thật.
 *
 * <p>File config MỚI, RIÊNG — KHÔNG sửa {@code WorkerProperties.java}/{@code application.yml} hiện
 * có (cùng cách làm với {@code GuiNhanQuocGiaProperties}). Mặc định
 * {@code enabled=false} — CHỈ bật trên môi trường dev/test, KHÔNG bật ở production/expose port này
 * ra Internet: endpoint gọi thẳng {@link com.td.worker.transport.InboundMessageRouter} — tương đương
 * quyền hạn 1 producer hợp lệ publish message vào broker (KHÔNG có xác thực riêng, dựa vào việc cổng
 * quản trị Worker vốn không public — cùng mô hình tin cậy của {@code /actuator}).</p>
 *
 * <p><b>Prefix CỐ Ý {@code worker.debug.endpoint}</b> (2 dot-segment, KHÔNG phải
 * {@code worker.debug-endpoint} 1 segment có gạch ngang) — đã tự phát hiện bằng thực nghiệm
 * (smoke test thật): với property source dạng biến môi trường, dấu gạch ngang TRONG 1 segment bị
 * XOÁ (không đổi thành gạch dưới) khi ánh xạ ngược ra tên biến môi trường quy ước — segment
 * {@code debug-endpoint} khớp {@code WORKER_DEBUGENDPOINT_ENABLED}, KHÔNG PHẢI
 * {@code WORKER_DEBUG_ENDPOINT_ENABLED} như trực giác thông thường. Tách thành 2 dot-segment
 * riêng biệt ({@code debug.endpoint}) tránh hẳn sự mơ hồ này — mỗi dấu chấm mới đúng là 1 dấu gạch
 * dưới trong tên biến môi trường, khớp {@code WORKER_DEBUG_ENDPOINT_ENABLED} như mong đợi.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "worker.debug.endpoint")
public class HttpDebugTriggerProperties {

    private boolean enabled = false;
}
