package com.td.infrastructure.tonghop.national;

import com.td.application.tonghop.national.NationalExchangeCommandRepository;
import com.td.application.tonghop.national.NationalExchangeDetail;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * Ghi hàng đợi lệnh quản trị trong DB TD Gateway (docs/24 §4.3). Pool RIÊNG có quyền ghi (khác pool chỉ đọc của
 * {@link PostgresNationalExchangeQueryRepository}); production nên cấp tài khoản chỉ được INSERT/UPDATE bảng
 * {@code worker_national_exchange_operation_request} + INSERT {@code worker_national_exchange_event}.
 *
 * <p>Tạo lệnh và sự kiện audit trong CÙNG transaction. Idempotency dựa ràng buộc UNIQUE {@code idempotency_key} (V8):
 * gửi trùng key → không chèn thêm, trả lệnh có sẵn.</p>
 */
public class PostgresNationalExchangeCommandRepository implements NationalExchangeCommandRepository, AutoCloseable {

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PostgresNationalExchangeCommandRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public Optional<NationalExchangeDetail.Operation> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(PostgresNationalExchangeQueryRepository.OPERATION_SELECT + " WHERE idempotency_key = :k",
                new MapSqlParameterSource("k", idempotencyKey),
                (rs, i) -> PostgresNationalExchangeQueryRepository.mapOperation(rs))
            .stream().findFirst();
    }

    @Override
    public boolean hasActive(String exchangeId, String operationType) {
        Boolean exists = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM worker_national_exchange_operation_request
                            WHERE exchange_id = :e AND operation_type = :t AND status IN ('PENDING','PROCESSING'))
            """, new MapSqlParameterSource("e", exchangeId).addValue("t", operationType), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public NationalExchangeDetail.Operation create(NewOperation op) {
        return tx.execute(status -> {
            MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", op.requestId()).addValue("type", op.operationType())
                .addValue("exchange", op.exchangeId()).addValue("tenant", op.tenantId())
                .addValue("agency", op.agencyCode()).addValue("reason", op.reason())
                .addValue("by", op.requestedBy()).addValue("byName", op.requestedByName())
                .addValue("key", op.idempotencyKey()).addValue("params", op.parametersJson());
            int inserted = jdbc.update("""
                INSERT INTO worker_national_exchange_operation_request
                    (request_id, operation_type, exchange_id, tenant_id, agency_code, reason, requested_by,
                     requested_by_name, status, idempotency_key, parameters_json)
                VALUES (:id, :type, :exchange, :tenant, :agency, :reason, :by, :byName, 'PENDING', :key,
                        CAST(:params AS jsonb))
                ON CONFLICT (idempotency_key) DO NOTHING
                """, p);
            if (inserted == 1) {
                appendEvent(op.exchangeId(), "ADMIN_OPERATION_REQUESTED", "PENDING", op.tenantId(), op.requestedBy(),
                    op.requestedByName(), op.operationType() + ": " + op.reason(), "op:" + op.requestId() + ":requested");
            }
            return findByIdempotencyKey(op.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Không đọc lại được lệnh vừa tạo"));
        });
    }

    @Override
    public Optional<NationalExchangeDetail.Operation> find(String requestId) {
        return jdbc.query(PostgresNationalExchangeQueryRepository.OPERATION_SELECT + " WHERE request_id = :id",
                new MapSqlParameterSource("id", requestId),
                (rs, i) -> PostgresNationalExchangeQueryRepository.mapOperation(rs))
            .stream().findFirst();
    }

    @Override
    public boolean cancel(String requestId, String exchangeId, String cancelledBy, String cancelledByName) {
        Boolean done = tx.execute(status -> {
            int updated = jdbc.update("""
                UPDATE worker_national_exchange_operation_request
                   SET status = 'CANCELLED', completed_at = NOW(), updated_at = NOW(), version_no = version_no + 1,
                       error_detail = 'Huỷ bởi ' || :by
                 WHERE request_id = :id AND status = 'PENDING'
                """, new MapSqlParameterSource("id", requestId).addValue("by", cancelledBy));
            if (updated == 1) {
                appendEvent(exchangeId, "ADMIN_OPERATION_CANCELLED", "CANCELLED", null, cancelledBy, cancelledByName,
                    "Huỷ yêu cầu " + requestId, "op:" + requestId + ":cancelled");
            }
            return updated == 1;
        });
        return Boolean.TRUE.equals(done);
    }

    private void appendEvent(String exchangeId, String eventType, String statusAfter, String tenantId, String actorId,
                             String actorName, String details, String correlationId) {
        jdbc.update("""
            INSERT INTO worker_national_exchange_event
                (exchange_id, event_type, status_after, tenant_id, actor_type, actor_id, actor_name, details, correlation_id)
            VALUES (:e, :type, :after, :tenant, 'ADMIN', :actor, :actorName, :details, :corr)
            ON CONFLICT (correlation_id) WHERE correlation_id IS NOT NULL DO NOTHING
            """, new MapSqlParameterSource("e", exchangeId).addValue("type", eventType).addValue("after", statusAfter)
            .addValue("tenant", tenantId).addValue("actor", actorId).addValue("actorName", actorName)
            .addValue("details", details == null || details.length() <= 4000 ? details : details.substring(0, 4000))
            .addValue("corr", correlationId));
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
