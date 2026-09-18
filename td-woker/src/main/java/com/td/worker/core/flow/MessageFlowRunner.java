package com.td.worker.core.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.config.WorkerMetrics;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.checkpoint.CheckpointStore;
import com.td.worker.core.checkpoint.UnitKeys;
import com.td.worker.core.checkpoint.UnitStatus;
import com.td.worker.core.dispatch.RetryPolicy;
import com.td.worker.core.dispatch.UnitDispatcher;
import com.td.worker.core.envelope.MessageEnvelope;
import com.td.worker.core.envelope.MessageUnit;
import com.td.worker.core.logging.CorrelationContext;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Chạy 1 message qua toàn bộ vòng đời:
 * <ol>
 *   <li>Lưu message gốc + insert các unit (idempotent theo uniqueKey).</li>
 *   <li>Dispatch TỪNG unit (mỗi unit thuộc tenant khác nhau xử lý độc lập).</li>
 *   <li>Chờ hoàn tất với timeout cấp unit/message; unit timeout được FAIL retry (không mù).</li>
 *   <li>Đánh giá ACK policy — "Chỉ ACK message theo chính sách hoàn tất được thiết kế".</li>
 * </ol>
 *
 * <p>Nếu Worker crash giữa chừng: trạng thái unit đã persisted trong PostgreSQL; message
 * redelivery (hoặc retry-poller) chỉ tiếp tục các unit chưa DONE — KHÔNG bao giờ xử lý lại
 * unit đã thành công (docs/promt.txt mục "Message gồm nhiều Unit").</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageFlowRunner {

    private final CheckpointStore store;
    private final UnitDispatcher dispatcher;
    private final RetryPolicy retryPolicy;
    private final WorkerProperties props;
    private final WorkerMetrics metrics;
    private final ObjectMapper objectMapper;

    /**
     * @return kết quả flow + quyết định ACK theo policy cấu hình
     */
    public FlowResult run(MessageEnvelope envelope) {
        CorrelationContext.putMessage(envelope.correlationId(), envelope.messageId(), envelope.taskType());
        try {
            MessageEnvelope normalized = normalize(envelope);
            store.storeMessage(envelope);
            store.initializeUnits(normalized);

            int total = normalized.units().size();
            Instant deadline = Instant.now().plus(props.getCore().getMessageTimeout());
            List<MessageUnit> units = normalized.units();
            List<String> keys = new ArrayList<>(total);
            List<TaskContext> contexts = new ArrayList<>(total);
            List<Future<UnitResult>> futures = new ArrayList<>(total);
            int notDispatched = 0;

            for (MessageUnit unit : units) {
                TaskContext ctx = buildContext(normalized, unit);
                contexts.add(ctx);
                String key = UnitKeys.of(unit.tenantId(), envelope.messageId(), unit.unitId());
                keys.add(key);
                var f = dispatcher.dispatch(key, ctx, deadline);
                futures.add(f.orElse(null));
                if (f.isEmpty()) {
                    notDispatched++;
                    log.debug("Unit {} chưa điều phối lượt này (backpressure/khác) - retry-poller xử lý sau",
                        unit.unitId());
                }
            }

            List<UnitOutcome> outcomes = new ArrayList<>(total);
            int timedOut = waitForCompletion(futures, contexts, keys, deadline, outcomes);

            Map<UnitStatus, Long> counts = store.countByStatusForMessage(envelope.messageId());
            boolean canAck = evaluateAckPolicy(counts, total);
            logAckSummary(envelope, counts, canAck, timedOut);
            return new FlowResult(total, counts, canAck, timedOut, notDispatched, outcomes);
        } finally {
            CorrelationContext.clear();
        }
    }
private int waitForCompletion(List<Future<UnitResult>> futures, List<TaskContext> contexts,
                                  List<String> keys, Instant messageDeadline, List<UnitOutcome> outcomes) {
        int timedOut = 0;
        for (int i = 0; i < futures.size(); i++) {
            Future<UnitResult> f = futures.get(i);
            TaskContext ctx = contexts.get(i);
            String key = keys.get(i);
            if (f == null) {
                continue;
            }
            long remainingMs = Duration.between(Instant.now(), messageDeadline).toMillis();
            long unitTimeoutMs = retryPolicy.unitTimeout(ctx).toMillis();
            long waitMs = Math.min(remainingMs, unitTimeoutMs);
            if (waitMs <= 0) {
                f.cancel(true);
                timedOut++;
                failTimedOut(key, ctx);
                outcomes.add(new UnitOutcome(ctx.unitId(), false, "timeout", null));
                continue;
            }
            try {
                // Trước đây (bug đã sửa): kết quả Future.get() bị BỎ QUA hoàn toàn ở đây — chỉ
                // dùng để "chờ xong", nên payload chi tiết TaskHandler trả về (vd transId/
                // verificationCode simpki.sign) không bao giờ ra khỏi Worker Core. Giờ giữ lại
                // thành UnitOutcome để transport (HTTP debug endpoint) trả ra được.
                UnitResult result = f.get(waitMs, TimeUnit.MILLISECONDS);
                outcomes.add(new UnitOutcome(ctx.unitId(), result.isSuccess(), result.error(), result.detail()));
            } catch (java.util.concurrent.TimeoutException e) {
                f.cancel(true);
                timedOut++;
                failTimedOut(key, ctx);
                outcomes.add(new UnitOutcome(ctx.unitId(), false, "timeout", null));
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("ExecutionException khi chờ unit {}: {}", ctx.unitId(), e.getMessage());
                outcomes.add(new UnitOutcome(ctx.unitId(), false, "ExecutionException: " + e.getMessage(), null));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return timedOut;
            }
        }
        return timedOut;
    }

    private void failTimedOut(String key, TaskContext ctx) {
        try {
            store.findByUniqueKey(key).ifPresent(rec -> {
                if (rec.status() == UnitStatus.PROCESSING) {
                    Instant next = retryPolicy.nextRetryAt(rec.attempts());
                    boolean ok = store.fail(key, dispatcher.workerId(), "timeout", next);
                    if (ok) {
                        metrics.unitFinal(ctx.taskType(),
                            rec.attempts() >= rec.maxAttempts() ? UnitStatus.FAILED : UnitStatus.RETRY);
                        log.warn("Unit {} timeout -> fail-in-place (attempt={}, nextRetryAt={})",
                            ctx.unitId(), rec.attempts(), next);
                    }
                }
            });
        } catch (RuntimeException ex) {
            log.error("Không fail-in-place được unit timeout {}: {}", key, ex.getMessage());
        }
    }

    private boolean evaluateAckPolicy(Map<UnitStatus, Long> counts, int total) {
        long pending = counts.getOrDefault(UnitStatus.PENDING, 0L);
        long processing = counts.getOrDefault(UnitStatus.PROCESSING, 0L);
        long retry = counts.getOrDefault(UnitStatus.RETRY, 0L);
        long waitingDependency = counts.getOrDefault(UnitStatus.WAITING_DEPENDENCY, 0L);
        return switch (props.getCore().getAckPolicy()) {
            // Chặt: CHỈ khi mọi unit về trạng thái tận cùng (DONE/FAILED).
            case ALL_TERMINAL -> pending == 0 && processing == 0 && retry == 0 && waitingDependency == 0;
            // Mặc định: mọi unit đã "accounted" — terminal HOẶC đã lên lịch retry în DB
            // (RetryPoller xử lý theo unit, không trông lại broker, đúng docs/promt.txt).
            case ALL_ACCOUNTED -> pending == 0;
        };
    }

    private void logAckSummary(MessageEnvelope envelope, Map<UnitStatus, Long> counts,
                               boolean canAck, int timedOut) {
        log.info("Message {} (task {}): DONE={} RETRY={} FAILED={} PROCESSING={} PENDING={}; "
                + "canAck={} (policy {}), timeouts={}",
            envelope.messageId(), envelope.taskType(),
            counts.getOrDefault(UnitStatus.DONE, 0L),
            counts.getOrDefault(UnitStatus.RETRY, 0L),
            counts.getOrDefault(UnitStatus.FAILED, 0L),
            counts.getOrDefault(UnitStatus.PROCESSING, 0L),
            counts.getOrDefault(UnitStatus.PENDING, 0L),
            canAck, props.getCore().getAckPolicy(), timedOut);
    }

    /** Chuẩn hoá: gán maxAttempts mặc định từ cấu hình, correlationId fallback = messageId. */
    private MessageEnvelope normalize(MessageEnvelope envelope) {
        int defaultMax = props.getCore().getRetriesPerUnit();
        String corr = (envelope.correlationId() == null || envelope.correlationId().isBlank())
            ? envelope.messageId() : envelope.correlationId();
        List<MessageUnit> normalized = envelope.units().stream()
            .map(u -> u.maxAttempts() == null ? u.withMaxAttempts(defaultMax) : u)
            .toList();
        return new MessageEnvelope(envelope.messageId(), envelope.taskType(), corr,
            envelope.source(), envelope.createdAt(), envelope.headers(), normalized);
    }

    private TaskContext buildContext(MessageEnvelope envelope, MessageUnit unit) {
        int maxAttempts = (unit.maxAttempts() != null && unit.maxAttempts() > 0)
            ? unit.maxAttempts() : props.getCore().getRetriesPerUnit();
        return new TaskContext(
            envelope.taskType(),
            envelope.messageId(),
            unit.unitId(),
            envelope.correlationId(),
            unit.tenantId(),
            unit.payload(),
            unit.target(),
            maxAttempts,
            unit.deadline(),
            envelope.headers());
    }
}
