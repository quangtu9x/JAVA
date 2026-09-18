package com.td.infrastructure.errortracking;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Đọc/ghi Error Tracking DB bằng {@code JdbcTemplate} thuần — KHÔNG dùng JPA/Hibernate. Xem javadoc
 * {@link ErrorLogEntity} để biết lý do (tạo bean {@code JdbcTemplate} không bao giờ chạm DB, khác
 * với {@code EntityManagerFactory} luôn eager-connect lúc bootstrap).
 */
@Repository
class ErrorLogJdbcRepository {

    private static final String SELECT_COLUMNS = "error_id, log_type, severity, occurred_at, http_status, " +
        "http_method, request_path, trace_id, username, remote_addr, exception_class, message, stack_trace, " +
        "request_body, duration_ms";

    private static final RowMapper<ErrorLogEntity> ROW_MAPPER = (rs, rowNum) -> ErrorLogEntity.builder()
        .errorId(rs.getString("error_id"))
        .logType(LogType.valueOf(rs.getString("log_type")))
        .severity(Severity.valueOf(rs.getString("severity")))
        .occurredAt(rs.getTimestamp("occurred_at").toInstant())
        .httpStatus((Integer) rs.getObject("http_status"))
        .httpMethod(rs.getString("http_method"))
        .requestPath(rs.getString("request_path"))
        .traceId(rs.getString("trace_id"))
        .username(rs.getString("username"))
        .remoteAddr(rs.getString("remote_addr"))
        .exceptionClass(rs.getString("exception_class"))
        .message(rs.getString("message"))
        .stackTrace(rs.getString("stack_trace"))
        .requestBody(rs.getString("request_body"))
        .durationMs((Long) rs.getObject("duration_ms"))
        .build();

    private final JdbcTemplate jdbcTemplate;

    // Khong dung @RequiredArgsConstructor: Lombok KHONG copy @Qualifier tu field sang tham so
    // constructor sinh ra - can thiet o day vi Spring Boot tu dong tao san 1 bean JdbcTemplate khac
    // (goi la "jdbcTemplate", gan voi DataSource nghiep vu @Primary) - khong co @Qualifier, Spring se
    // bao loi NoUniqueBeanDefinitionException do co 2 bean cung type JdbcTemplate trong context.
    ErrorLogJdbcRepository(@Qualifier("errorTrackingJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void save(ErrorLogEntity entity) {
        jdbcTemplate.update(
            "INSERT INTO error_log (" + SELECT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            entity.getErrorId(),
            entity.getLogType().name(),
            entity.getSeverity().name(),
            Timestamp.from(entity.getOccurredAt()),
            entity.getHttpStatus(),
            entity.getHttpMethod(),
            entity.getRequestPath(),
            entity.getTraceId(),
            entity.getUsername(),
            entity.getRemoteAddr(),
            entity.getExceptionClass(),
            entity.getMessage(),
            entity.getStackTrace(),
            entity.getRequestBody(),
            entity.getDurationMs());
    }

    Optional<ErrorLogEntity> findById(String errorId) {
        List<ErrorLogEntity> results = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM error_log WHERE error_id = ?", ROW_MAPPER, errorId);
        return results.stream().findFirst();
    }

    List<ErrorLogEntity> search(SearchErrorLogsRequest request, int pageNumber, int pageSize) {
        WhereClause where = buildWhereClause(request);
        List<Object> params = new ArrayList<>(where.params());
        params.add(pageSize);
        params.add(Math.max(pageNumber, 0) * pageSize);

        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM error_log WHERE 1=1" + where.sql() +
                " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
            ROW_MAPPER, params.toArray());
    }

    long count(SearchErrorLogsRequest request) {
        WhereClause where = buildWhereClause(request);
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM error_log WHERE 1=1" + where.sql(), Long.class, where.params().toArray());
        return total != null ? total : 0L;
    }

    /** Bulk delete — dùng bởi {@link ErrorLogCleanupScheduler}. Trả về số bản ghi đã xóa để log. */
    int deleteByOccurredAtBefore(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM error_log WHERE occurred_at < ?", Timestamp.from(cutoff));
    }

    private WhereClause buildWhereClause(SearchErrorLogsRequest request) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (request.getLogType() != null) {
            sql.append(" AND log_type = ?");
            params.add(request.getLogType().name());
        }
        if (request.getSeverity() != null) {
            sql.append(" AND severity = ?");
            params.add(request.getSeverity().name());
        }
        if (request.getHttpStatus() != null) {
            sql.append(" AND http_status = ?");
            params.add(request.getHttpStatus());
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            sql.append(" AND username = ?");
            params.add(request.getUsername());
        }
        if (request.getFromDate() != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(Timestamp.from(request.getFromDate()));
        }
        if (request.getToDate() != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(Timestamp.from(request.getToDate()));
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String likePattern = "%" + request.getKeyword().toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(message) LIKE ? OR LOWER(exception_class) LIKE ? OR LOWER(request_path) LIKE ?)");
            params.add(likePattern);
            params.add(likePattern);
            params.add(likePattern);
        }

        return new WhereClause(sql.toString(), params);
    }

    private record WhereClause(String sql, List<Object> params) {
    }
}
