package com.td.worker.transport.http;

import com.td.worker.core.flow.UnitOutcome;
import com.td.worker.transport.InboundMessageRouter;
import com.td.worker.transport.RoutedResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint HTTP debug/test — dùng để test độc lập module bất kỳ mà không cần dựng RabbitMQ/Kafka
 * thật. CHỈ active khi {@code worker.debug-endpoint.enabled=true} (mặc định {@code false}).
 *
 * <p>Gọi thẳng {@link InboundMessageRouter#route} — ĐÚNG biên giới transport-agnostic đã có sẵn
 * (docs/04-rabbitmq-kafka-flow.md §1: "Envelope là lớp trừu tượng — đổi broker không đổi code
 * nghiệp vụ"), HTTP ở đây chỉ là 1 "transport" THỨ 3 bên cạnh RabbitMQ/Kafka, dùng CHUNG toàn bộ
 * {@code EnvelopeParser}/{@code MessageFlowRunner}/checkpoint/idempotency — KHÔNG viết lại logic
 * dispatch. Body request = ĐÚNG format envelope JSON (messageId/taskType/correlationId/source/
 * createdAt/headers/units[]) như khi publish qua broker.</p>
 *
 * <p>Request xử lý ĐỒNG BỘ (giống hệt 1 Rabbit consumer thread), vì vậy HTTP client phải chấp nhận
 * thời gian chờ tương ứng với task được kích hoạt.</p>
 *
 * <p>⚠️ CHỈ dùng dev/test — KHÔNG bật ở production/expose port này ra Internet: endpoint KHÔNG có
 * xác thực riêng (tương đương quyền 1 producer hợp lệ, cùng mô hình tin cậy {@code /actuator}).</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "worker.debug.endpoint", name = "enabled", havingValue = "true")
public class DebugTriggerController {

    private final InboundMessageRouter router;

    @PostConstruct
    void warnEnabled() {
        log.warn("worker.debug.endpoint.enabled=true: endpoint POST /internal/debug/publish ĐANG "
            + "BẬT — CHỈ dùng dev/test, KHÔNG bật ở production/expose port này ra Internet (endpoint "
            + "không có xác thực riêng, tương đương quyền 1 producer hợp lệ publish message).");
    }

    /**
     * @param rawBody envelope JSON THÔ (KHÔNG bọc thêm — cùng format publish qua RabbitMQ/Kafka).
     */
    @PostMapping(value = "/internal/debug/publish", consumes = "application/json", produces = "application/json")
    public ResponseEntity<DebugPublishResponse> publish(@RequestBody String rawBody) {
        RoutedResult result = router.route(rawBody);
        DebugPublishResponse body = new DebugPublishResponse(
            result.type().name(), result.canAck(), result.detail(), result.units());
        HttpStatus status = switch (result.type()) {
            case FLOW_OK -> HttpStatus.OK;
            case PARSE_ERROR -> HttpStatus.BAD_REQUEST;
            case TRANSIENT_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(body);
    }

    /**
     * @param units chi tiết từng unit — {@code detail} bên trong mỗi phần tử là payload nghiệp vụ
     *              thật do TaskHandler trả về, KHÔNG phải suy diễn từ
     *              {@code type}/{@code canAck} chung của cả message như trước.
     */
    public record DebugPublishResponse(String type, boolean canAck, String detail, List<UnitOutcome> units) {
    }
}
