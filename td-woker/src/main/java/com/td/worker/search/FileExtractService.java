package com.td.worker.search;

import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.search.FileExtractStore.FileRow;
import com.td.worker.search.FileExtractStore.LeasedFile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 17/10/2026 — làn "trích nội dung tệp" của 1 tenant (docs/architecture/14-trich-noi-dung-tep.md): thuê tệp trong
 * hàng đợi ({@code entity_type='file'}), tải từ MinIO, trích chữ bằng Tika ({@link FileContentExtractor}, không OCR), ghi
 * {@code file_content_extracts}, rồi đưa văn bản cha vào hàng đợi để nạp lại Elasticsearch kèm nội dung tệp.
 * Chạy trên coordinator RIÊNG (luồng riêng) — trích tệp lớn không làm chậm đồng bộ văn bản.
 */
@Slf4j
public class FileExtractService {

    private static final Duration MIN_WAKE = Duration.ofSeconds(1);

    private final SearchSyncProperties props;
    private final FileExtractStore store;
    private final SearchIndexQueueStore queueStore;
    private final FileContentExtractor extractor;
    private final WorkerMinioClient minio;
    private final TenantDataSourceManager dataSources;
    private final Counter extractedCounter;
    private final Counter failedCounter;

    public FileExtractService(SearchSyncProperties props, FileExtractStore store, SearchIndexQueueStore queueStore,
                              FileContentExtractor extractor, WorkerMinioClient minio, TenantDataSourceManager dataSources,
                              MeterRegistry meterRegistry) {
        this.props = props;
        this.store = store;
        this.queueStore = queueStore;
        this.extractor = extractor;
        this.minio = minio;
        this.dataSources = dataSources;
        this.extractedCounter = Counter.builder("worker_file_extract_done_total").register(meterRegistry);
        this.failedCounter = Counter.builder("worker_file_extract_failed_total").register(meterRegistry);
    }

    /** Cùng hợp đồng {@link SearchIndexSyncService#syncTenant(String, boolean)}: trả lúc cần chạy lại (null = rỗng). */
    public Duration extractTenant(String tenantId, boolean sweep) throws SQLException {
        DataSource dataSource = dataSources.getDataSource(tenantId).orElse(null);
        if (dataSource == null) {
            return null;
        }
        return extractTenant(dataSource, sweep);
    }

    Duration extractTenant(DataSource dataSource, boolean sweep) throws SQLException {
        SearchSyncProperties.FileExtract config = props.getFileExtract();
        if (sweep) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                int queued = store.enqueueMissing(connection);
                if (queued > 0) {
                    log.info("Trích nội dung tệp: đưa {} tệp chưa trích vào hàng đợi", queued);
                }
            }
        }
        List<LeasedFile> leased;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            leased = store.lease(connection, config.getBatchSize(), config.getLease());
        }
        for (LeasedFile file : leased) {
            process(dataSource, file);
        }
        if (leased.size() >= config.getBatchSize()) {
            return Duration.ZERO;
        }
        try (Connection connection = dataSource.getConnection()) {
            Instant next = store.nextDueAt(connection);
            if (next == null) {
                return null;
            }
            Duration wait = Duration.between(Instant.now(), next);
            return wait.compareTo(MIN_WAKE) < 0 ? MIN_WAKE : wait;
        }
    }

    void process(DataSource dataSource, LeasedFile leased) throws SQLException {
        Map<String, String> entities = props.getEntities();
        Optional<FileRow> fileRow;
        Optional<UUID> previousDocument;
        try (Connection connection = dataSource.getConnection()) {
            fileRow = store.loadFile(connection, leased.fileId());
            previousDocument = store.extractDocumentId(connection, leased.fileId());
        }

        if (fileRow.isEmpty()) { // tệp đã bị xoá → bỏ nội dung khỏi văn bản cha
            inTransaction(dataSource, connection -> {
                store.deleteExtract(connection, leased.fileId())
                    .ifPresent(doc -> enqueueParentQuietly(connection, doc, entities));
                queueStore.delete(connection, List.of(leased.queueId()));
            });
            return;
        }
        FileRow file = fileRow.get();
        FileContentExtractor.Result result;
        if (file.superseded() || file.documentId() == null) {
            result = FileContentExtractor.Result.skipped(file.contentType(),
                file.superseded() ? "Tệp đã được thay thế bằng tệp mới" : "Tệp chưa gắn văn bản");
        } else {
            try (InputStream input = minio.openObject(file.bucket(), file.filePath())) {
                result = extractor.extract(input, file.filename(), file.contentType(), file.size());
            } catch (Exception ex) {
                handleFailure(dataSource, leased, file, previousDocument, ex);
                return;
            }
        }
        FileContentExtractor.Result finalResult = result;
        inTransaction(dataSource, connection -> {
            store.upsert(connection, file.id(), file.documentId(), finalResult);
            enqueueParentQuietly(connection, file.documentId(), entities);
            previousDocument.filter(old -> !old.equals(file.documentId()))
                .ifPresent(old -> enqueueParentQuietly(connection, old, entities));
            queueStore.delete(connection, List.of(leased.queueId()));
        });
        if (FileContentExtractor.STATUS_DONE.equals(result.status())) {
            extractedCounter.increment();
        }
    }

    private void handleFailure(DataSource dataSource, LeasedFile leased, FileRow file, Optional<UUID> previousDocument,
                               Exception ex) throws SQLException {
        failedCounter.increment();
        String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        int maxAttempts = props.getFileExtract().getMaxAttempts();
        if (leased.attempts() + 1 >= maxAttempts) {
            log.warn("Trích nội dung tệp {} ({}) lỗi {} lần, ghi FAILED: {}", file.id(), file.filename(), maxAttempts, error);
            inTransaction(dataSource, connection -> {
                store.upsert(connection, file.id(), file.documentId(),
                    new FileContentExtractor.Result(FileContentExtractor.STATUS_FAILED, null, false, file.contentType(), error));
                queueStore.delete(connection, List.of(leased.queueId()));
            });
            return;
        }
        log.info("Trích nội dung tệp {} ({}) lỗi, thử lại sau: {}", file.id(), file.filename(), error);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            queueStore.markRetry(connection, List.of(leased.queueId()), error,
                props.getRetryInitialBackoff(), props.getRetryMaxBackoff());
        }
    }

    private void enqueueParentQuietly(Connection connection, UUID documentId, Map<String, String> entities) {
        try {
            store.enqueueParent(connection, documentId, entities);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    private static void inTransaction(DataSource dataSource, SqlWork work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}
