package com.td.worker.core.dispatch;

import com.td.worker.config.WorkerProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Chính sách retry THEO UNIT (không retry mù toàn bộ message — docs/promt.txt):
 * exponential backoff + jitter nhẹ, bị cap bởi {@code retry-max-backoff}.
 */
@Component
public class RetryPolicy {

    private final WorkerProperties props;

    public RetryPolicy(WorkerProperties props) {
        this.props = props;
    }

    /**
     * Thời điểm retry tiếp theo.
     *
     * @param attempts số lần đã thử (SAU claim — bắt đầu từ 1)
     */
    public Instant nextRetryAt(int attempts) {
        WorkerProperties.Core core = props.getCore();
        long base = core.getRetryInitialBackoff().toMillis();
        double exponent = Math.max(0, attempts - 1);
        long computed = (long) (base * Math.pow(core.getRetryBackoffMultiplier(), exponent));
        long capped = Math.min(computed, core.getRetryMaxBackoff().toMillis());
        // Jitter nhỏ (0-10%) tránh thundering herd khi hàng loạt unit cùng retry.
        double jitter = 0.9 + (Math.random() * 0.1);
        long withJitter = Math.min((long) (capped * jitter), core.getRetryMaxBackoff().toMillis());
        return Instant.now().plusMillis(Math.max(withJitter, 1));
    }

    /** Timeout xử lý 1 unit: unit.deadline (nếu có) có hiệu lực sớm hơn unitTimeout cấu hình. */
    public Duration unitTimeout(com.td.worker.core.task.TaskContext ctx) {
        Duration configured = props.getCore().getUnitTimeout();
        if (ctx.hasDeadline()) {
            Duration untilDeadline = Duration.between(Instant.now(), ctx.deadline());
            if (untilDeadline.compareTo(configured) < 0) {
                return untilDeadline.isNegative() ? Duration.ZERO : untilDeadline;
            }
        }
        return configured;
    }
}