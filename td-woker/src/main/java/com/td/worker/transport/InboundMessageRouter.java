package com.td.worker.transport;

import com.td.worker.config.WorkerMetrics;
import com.td.worker.core.envelope.EnvelopeParser;
import com.td.worker.core.envelope.EnvelopeValidationException;
import com.td.worker.core.envelope.MessageEnvelope;
import com.td.worker.core.flow.FlowResult;
import com.td.worker.core.flow.MessageFlowRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Biên giới CHUNG giữa transport (RabbitMQ/Kafka) và Worker Core: parse envelope ->
 * chạy flow -> trả về RoutedResult để transport quyết định ACK/requeue/DLQ/commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboundMessageRouter {

    private final EnvelopeParser parser;
    private final MessageFlowRunner flowRunner;
    private final WorkerMetrics metrics;

    public RoutedResult route(String rawBody) {
        metrics.messageReceived();
        final MessageEnvelope envelope;
        try {
            envelope = parser.parse(rawBody);
        } catch (EnvelopeValidationException e) {
            metrics.messageParseFailed();
            log.error("Envelope không hợp lệ -> DLQ: {}", e.getMessage());
            return RoutedResult.parseError(e.getMessage());
        }
        try {
            FlowResult flow = flowRunner.run(envelope);
            return RoutedResult.flow(flow.canAck(), flow.unitOutcomes());
        } catch (RuntimeException e) {
            // Bất kỳ lỗi hạ tầng (DB checkpoint down...) → transport xử lý requeue/no-commit.
            log.error("Flow message {} thất bại (lỗi hạ tầng): {}", envelope.messageId(), e.getMessage());
            return RoutedResult.transientError(e.getMessage());
        }
    }
}