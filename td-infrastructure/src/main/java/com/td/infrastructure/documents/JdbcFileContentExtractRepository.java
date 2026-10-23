package com.td.infrastructure.documents;

import com.td.application.documents.FileContentExtractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 17/10/2026 — {@link FileContentExtractRepository} trên DB tenant hiện tại (datasource định tuyến theo tenant). Bảng
 * {@code file_content_extracts} + hàng đợi {@code search_index_queue} (migration V1.0.72-V1.0.74); tenant chưa chạy
 * migration → coi như chưa có dữ liệu (không lỗi).
 */
@Slf4j
@Repository
public class JdbcFileContentExtractRepository implements FileContentExtractRepository {

    /** PHẢI trùng kênh trong hàm trigger search_index_enqueue() (V1.0.73) và WORKER_SEARCH_SYNC_NOTIFY_CHANNEL. */
    static final String NOTIFY_CHANNEL = "search_index";
    private static final String ENTITY_FILE = "file";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcFileContentExtractRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    }

    @Override
    public Optional<FileContentExtract> findByFileId(UUID fileId) {
        try {
            List<FileContentExtract> rows = jdbc.query(
                "SELECT file_id, document_id, status, content, content_chars, truncated, content_type, error, "
                    + "extracted_at FROM file_content_extracts WHERE file_id = ?",
                (rs, i) -> new FileContentExtract((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3),
                    rs.getString(4), rs.getInt(5), rs.getBoolean(6), rs.getString(7), rs.getString(8),
                    rs.getObject(9, OffsetDateTime.class)),
                fileId);
            return rows.stream().findFirst();
        } catch (DataAccessException ex) {
            log.debug("Chưa đọc được file_content_extracts (migration V1.0.74?): {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isQueued(UUID fileId) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM search_index_queue WHERE entity_type = ? AND entity_id = ?)",
                Boolean.class, ENTITY_FILE, fileId));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    @Override
    public void requestReextract(UUID fileId) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO search_index_queue (entity_type, entity_id, operation) VALUES (?, ?, 'R')",
                ENTITY_FILE, fileId);
            // Đánh thức worker ngay (gửi khi COMMIT) — không thì chờ lượt quét dự phòng.
            jdbc.queryForObject("SELECT pg_notify(?, ?)::text", String.class, NOTIFY_CHANNEL, ENTITY_FILE);
        });
    }
}
