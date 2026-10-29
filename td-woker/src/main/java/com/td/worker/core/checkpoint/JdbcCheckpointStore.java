package com.td.worker.core.checkpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.core.envelope.MessageEnvelope;
import com.td.worker.core.envelope.MessageUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import com.td.worker.config.WorkerProperties;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link CheckpointStore} trên PostgreSQL (SQL là tập con portabler giữa PG và H2 ở
 * PostgreSQL mode — tiện cho unit test). Bảng: {@code worker_units}/{@code worker_messages}
 * (xem db/migration-worker/V1__create_worker_tables.sql).
 */
@Slf4j
@Repository
public class JdbcCheckpointStore implements CheckpointStore {

    // Một unit được claim nếu: (PENDING/RETRY đã tới hạn) HOẶC (PROCESSING nhưng lease hết hạn).
    private static final String CLAIM_CONDITION =
        " ( (status IN ('PENDING','RETRY','WAITING_DEPENDENCY') AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)) "
            + " OR (status = 'PROCESSING' AND lease_expires_at < CURRENT_TIMESTAMP) ) ";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkerProperties props;

    private final RowMapper<UnitRecord> unitRowMapper = (rs, i) -> new UnitRecord(
        rs.getLong("id"),
        rs.getString("unique_key"),
        rs.getString("tenant_id"),
        rs.getString("message_id"),
        rs.getString("unit_id"),
        rs.getString("task_type"),
        rs.getString("correlation_id"),
        UnitKeys.parseStatus(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getInt("max_attempts"),
        rs.getString("payload_json"),
        rs.getString("last_error"),
        rs.getTimestamp("next_retry_at") == null ? null : rs.getTimestamp("next_retry_at").toInstant());

    public JdbcCheckpointStore(DataSource dataSource, ObjectMapper objectMapper) {
        this(dataSource, objectMapper, new WorkerProperties());
    }

    @Autowired
    public JdbcCheckpointStore(DataSource dataSource, ObjectMapper objectMapper, WorkerProperties props) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public int storeMessage(MessageEnvelope envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            // Portabler SQL (PG + H2): trả 1 nếu insert, 0 nếu message đã tồнат (idempotent).
            return jdbc.update("""
                    INSERT INTO worker_messages
                     (message_id, task_type, correlation_id, source, envelope_json, received_at)
                    SELECT ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (SELECT 1 FROM worker_messages WHERE message_id = ?)
                    """,
                envelope.messageId(),
                envelope.taskType(),
                envelope.correlationId(),
                envelope.source(),
                json,
                envelope.messageId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được envelope: " + envelope.messageId(), e);
        }
    }

    @Override
    public int initializeUnits(MessageEnvelope envelope) {
        List<MessageUnit> units = envelope.units();
        if (units.isEmpty()) {
            return 0;
        }
        String insert = """
                INSERT INTO worker_units
                 (unique_key, tenant_id, message_id, unit_id, task_type, correlation_id,
                  status, attempts, max_attempts, payload_json, created_at, updated_at)
                SELECT ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM worker_units WHERE unique_key = ?)
                """;
        List<Object[]> batchArgs = new java.util.ArrayList<>(units.size());
        for (MessageUnit u : units) {
            int maxAttempts = (u.maxAttempts() != null && u.maxAttempts() > 0)
                ? u.maxAttempts() : 5;
            String key = UnitKeys.of(u.tenantId(), envelope.messageId(), u.unitId());
            batchArgs.add(new Object[] {
                key,
                u.tenantId(),
                envelope.messageId(),
                u.unitId(),
                envelope.taskType(),
                envelope.correlationId(),
                maxAttempts,
                u.payload() == null ? null : u.payload().toString(),
                key
            });
        }
        int inserted = 0;
        int already = 0;
        String mode = props.getDb().getUnitInsertMode().trim().toLowerCase(java.util.Locale.ROOT);
        if ("single".equals(mode)) {
            for (Object[] args : batchArgs) {
                int c = jdbc.update(insert, args);
                if (c > 0) inserted++; else already++;
            }
        } else if ("batch".equals(mode)) {
            int batchSize = props.getDb().getUnitInsertBatchSize();
            if (batchSize < 1) {
                throw new IllegalStateException("worker.db.unit-insert-batch-size must be >= 1");
            }
            for (int start = 0; start < batchArgs.size(); start += batchSize) {
                int end = Math.min(start + batchSize, batchArgs.size());
                for (int c : jdbc.batchUpdate(insert, batchArgs.subList(start, end))) {
                    if (c > 0) inserted++; else already++;
                }
            }
        } else {
            throw new IllegalStateException("worker.db.unit-insert-mode must be 'batch' or 'single'");
        }
        if (already > 0) {
            log.debug("initializeUnits: {} unit mới insert, {} unit đã tồn tại (redelivery/worker khác)",
                inserted, already);
        }
        return inserted;
    }
    // 23/10/2026: moi moc thoi gian lease tinh bang DONG HO DB (CURRENT_TIMESTAMP +/- n giay) - truoc day
    // lease_expires_at = Instant.now() cua JVM nhung lai so voi CURRENT_TIMESTAMP cua DB, cac may chu
    // lech gio lam lease het som -> worker khac giut unit dang chay (xu ly kep).
    private static final String SECONDS = " CAST(? AS INTEGER) * INTERVAL '1' SECOND ";

    @Override
    public Optional<UnitRecord> claim(String uniqueKey, String workerId, int leaseSeconds) {
        int updated = jdbc.update("""
                UPDATE worker_units
                SET status = 'PROCESSING',
                    claimed_by = ?,
                    claimed_at = CURRENT_TIMESTAMP,
                    lease_expires_at = CURRENT_TIMESTAMP + %s,
                    attempts = attempts + 1,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE unique_key = ? AND ( %s )
                """.formatted(SECONDS, CLAIM_CONDITION),
            workerId, leaseSeconds, uniqueKey);
        if (updated == 0) {
            return Optional.empty();
        }
        return findByUniqueKey(uniqueKey);
    }

    @Override
    public List<String> renewLeases(String workerId, List<String> uniqueKeys, int leaseSeconds) {
        if (uniqueKeys.isEmpty()) {
            return List.of();
        }
        String sql = """
                UPDATE worker_units
                SET lease_expires_at = CURRENT_TIMESTAMP + %s
                WHERE unique_key = ? AND claimed_by = ? AND status = 'PROCESSING'
                """.formatted(SECONDS);
        List<Object[]> args = new ArrayList<>(uniqueKeys.size());
        for (String key : uniqueKeys) {
            args.add(new Object[] {leaseSeconds, key, workerId});
        }
        int[] counts = jdbc.batchUpdate(sql, args);
        List<String> lost = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            // SUCCESS_NO_INFO (-2): driver khong bao so dong - coi nhu da gia han.
            if (counts[i] == 0) {
                lost.add(uniqueKeys.get(i));
            }
        }
        return lost;
    }

    @Override
    public boolean complete(String uniqueKey, String workerId) {
        int updated = jdbc.update("""
                UPDATE worker_units
                SET status = 'DONE',
                    claimed_by = NULL, claimed_at = NULL, lease_expires_at = NULL,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE unique_key = ? AND claimed_by = ? AND status = 'PROCESSING'
                """, uniqueKey, workerId);
        return updated == 1;
    }

    @Override
    public boolean fail(String uniqueKey, String workerId, String error, Instant nextRetryAt) {
        // Tại thời điểm này attempts đã tăng sau claim. Quyết định RETRY hay FAILED theo max_attempts.
        Timestamp retryTs = nextRetryAt == null ? null : Timestamp.from(nextRetryAt);
        int updated = jdbc.update("""
                UPDATE worker_units
                SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'RETRY' END,
                    -- PostgreSQL không suy luận được kiểu của ? khi nhánh còn lại là NULL.
                    -- Cast tường minh giữ timestamptz cho cả retry và lần thử cuối.
                    next_retry_at = CASE WHEN attempts >= max_attempts THEN NULL
                                         ELSE CAST(? AS TIMESTAMP WITH TIME ZONE) END,
                    last_error = ?,
                    claimed_by = NULL, claimed_at = NULL, lease_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE unique_key = ? AND claimed_by = ? AND status = 'PROCESSING'
                """, retryTs, truncate(error, 4000), uniqueKey, workerId);
        return updated == 1;
    }

    @Override
    public boolean defer(String uniqueKey, String workerId, String error, Instant nextCheckAt) {
        Timestamp next = Timestamp.from(nextCheckAt);
        int updated = jdbc.update("""
                UPDATE worker_units
                SET status = 'WAITING_DEPENDENCY',
                    attempts = CASE WHEN attempts > 0 THEN attempts - 1 ELSE 0 END,
                    next_retry_at = CAST(? AS TIMESTAMP WITH TIME ZONE),
                    last_error = ?,
                    claimed_by = NULL, claimed_at = NULL, lease_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE unique_key = ? AND claimed_by = ? AND status = 'PROCESSING'
                """, next, truncate(error, 4000), uniqueKey, workerId);
        return updated == 1;
    }

    @Override
    public List<String> findDueRetries(int limit) {
        return jdbc.query("""
                SELECT unique_key FROM worker_units
                WHERE status IN ('RETRY', 'WAITING_DEPENDENCY')
                  AND next_retry_at IS NOT NULL AND next_retry_at <= CURRENT_TIMESTAMP
                ORDER BY next_retry_at
                LIMIT ?
                """, (rs, i) -> rs.getString("unique_key"), limit);
    }

    @Override
    public int requeueExpiredLeases(Duration grace, int limit) {
        int graceSeconds = (int) Math.max(0, grace.toSeconds());
        return jdbc.update("""
                UPDATE worker_units
                SET status = 'RETRY',
                    next_retry_at = CURRENT_TIMESTAMP,
                    claimed_by = NULL, claimed_at = NULL, lease_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'PROCESSING' AND lease_expires_at < CURRENT_TIMESTAMP - %1$s
                  AND id IN (SELECT id FROM worker_units
                             WHERE status='PROCESSING' AND lease_expires_at < CURRENT_TIMESTAMP - %1$s LIMIT ?)
                """.formatted(SECONDS), graceSeconds, graceSeconds, limit);
    }

    @Override
    public int requeueStalePending(Duration age, int limit) {
        int ageSeconds = (int) Math.max(0, age.toSeconds());
        return jdbc.update("""
                UPDATE worker_units
                SET status = 'RETRY',
                    next_retry_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'PENDING' AND updated_at < CURRENT_TIMESTAMP - %1$s
                  AND id IN (SELECT id FROM worker_units
                             WHERE status='PENDING' AND updated_at < CURRENT_TIMESTAMP - %1$s LIMIT ?)
                """.formatted(SECONDS), ageSeconds, ageSeconds, limit);
    }

    @Override
    public Map<UnitStatus, Long> countByStatus(String taskType) {
        Map<UnitStatus, Long> result = new EnumMap<>(UnitStatus.class);
        jdbc.query("""
                SELECT status, COUNT(*) AS cnt FROM worker_units
                WHERE (? = '' OR task_type = ?)
                GROUP BY status
                """, rs -> {
            result.put(UnitKeys.parseStatus(rs.getString("status")), rs.getLong("cnt"));
        }, taskType == null ? "" : taskType, taskType);
        return result;
    }

    @Override
    public Map<UnitStatus, Long> countByStatusForMessage(String messageId) {
        Map<UnitStatus, Long> result = new EnumMap<>(UnitStatus.class);
        jdbc.query("""
                SELECT status, COUNT(*) AS cnt FROM worker_units
                WHERE message_id = ?
                GROUP BY status
                """, rs -> {
            result.put(UnitKeys.parseStatus(rs.getString("status")), rs.getLong("cnt"));
        }, messageId);
        return result;
    }

    @Override
    public boolean permanentFail(String uniqueKey, String workerId, String error) {
        int updated = jdbc.update("""
                UPDATE worker_units
                SET status = 'FAILED',
                    next_retry_at = NULL,
                    last_error = ?,
                    claimed_by = NULL, claimed_at = NULL, lease_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE unique_key = ? AND claimed_by = ? AND status = 'PROCESSING'
                """, truncate(error, 4000), uniqueKey, workerId);
        return updated == 1;
    }

    @Override
    public Optional<UnitRecord> findByUniqueKey(String uniqueKey) {
        List<UnitRecord> rows = jdbc.query(
            "SELECT * FROM worker_units WHERE unique_key = ?", unitRowMapper, uniqueKey);
        return rows.stream().findFirst();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
