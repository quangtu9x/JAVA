package com.td.worker.core.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.config.WorkerMetrics;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.checkpoint.CheckpointStore;
import com.td.worker.core.checkpoint.UnitRecord;
import com.td.worker.core.task.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Retry/khôi phục THEO UNIT (độc lập với broker):
 * <ol>
 *   <li>Unit {@code RETRY} đã tới hạn (next_retry_at) → claim lại và xử lý — retry có
 *       exponential backoff per-unit, KHÔNG retry mù toàn bộ message.</li>
 *   <li>Unit {@code PROCESSING} có lease hết hạn (Worker A crash/timeout/mất kết nối) →
 *       Worker B/poller claim lại và tiếp tục.</li>
 *   <li>Unit {@code PENDING} quá lâu (crash trước khi dispatch) → đưa vào retry.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryPoller {

    private final CheckpointStore store;
    private final UnitDispatcher dispatcher;
    private final WorkerProperties props;
    private final WorkerMetrics metrics;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "#{@workerScheduleIntervals.retryPollIntervalMs()}")
    public void poll() {
        try {
            int batch = props.getCore().getRetryPollBatchSize();
            List<String> due = store.findDueRetries(batch);
            metrics.retryDue.set(due.size());
            Instant deadline = Instant.now().plusSeconds(30);
            int dispatched = 0;
            for (String key : due) {
                var recOpt = store.findByUniqueKey(key);
                if (recOpt.isEmpty()) {
                    continue;
                }
                UnitRecord rec = recOpt.get();
                var f = dispatcher.dispatch(rec.uniqueKey(), toContext(rec), deadline);
                if (f.isPresent()) {
                    dispatched++;
                }
            }
            if (dispatched > 0) {
                log.debug("RetryPoller: đã điều phối {} unit retry", dispatched);
            }

            int reclaimed = store.requeueExpiredLeases(Duration.ofSeconds(5), batch);
            if (reclaimed > 0) {
                log.warn("RetryPoller: {} unit có lease hết hạn được đưa về RETRY (crash recovery)", reclaimed);
            }
            int stale = store.requeueStalePending(props.getCore().getPendingStaleTimeout(), batch);
            if (stale > 0) {
                log.warn("RetryPoller: {} unit PENDING quá lâu được đưa về RETRY", stale);
            }
        } catch (RuntimeException e) {
            log.warn("RetryPoller lượt này lỗi: {}", e.getMessage());
        }
    }

    private TaskContext toContext(UnitRecord rec) {
        JsonNode payload = null;
        if (rec.payloadJson() != null && !rec.payloadJson().isBlank()) {
            try {
                payload = objectMapper.readTree(rec.payloadJson());
            } catch (Exception e) {
                log.warn("Không parse được payload unit {}: {}", rec.uniqueKey(), e.getMessage());
            }
        }
        return new TaskContext(
            rec.taskType(),
            rec.messageId(),
            rec.unitId(),
            rec.correlationId(),
            rec.tenantId(),
            payload,
            Map.of(),
            rec.maxAttempts(),
            null,
            Map.of());
    }

    public long intervalMs() {
        return props.getCore().getRetryPollInterval().toMillis();
    }
}