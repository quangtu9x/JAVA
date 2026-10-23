package com.td.worker.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.infrastructure.tenant.TenantRecord;
import com.td.worker.infrastructure.tenant.TenantRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bật đồng bộ Elasticsearch khi {@code worker.search-sync.enabled=true} VÀ có {@code uris}. Thiếu cấu hình thì
 * chỉ log cảnh báo — hàng đợi vẫn được trigger ghi đủ, cấu hình sau rồi khởi động lại worker sẽ tự bù.
 *
 * <p>Luồng chính theo sự kiện: {@link SearchNotificationListener} (LISTEN/NOTIFY từng tenant) →
 * {@link SearchSyncCoordinator} (song song theo tenant). Quét định kỳ CHỈ còn là dự phòng
 * ({@code fallbackSweepInterval}): bù thông báo lỡ, đối soát, số liệu, cập nhật danh sách tenant đang nghe.</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SearchSyncProperties.class)
public class SearchSyncConfig {

    @Bean
    public SearchSyncRunner searchSyncRunner(SearchSyncProperties props, ObjectMapper objectMapper,
                                             TenantRegistry tenantRegistry, TenantDataSourceManager dataSources,
                                             MeterRegistry meterRegistry, WorkerMinioClient minio) {
        if (!props.isConfigured()) {
            log.warn("Đồng bộ Elasticsearch KHÔNG chạy: worker.search-sync.enabled={} và chưa khai ELASTICSEARCH_URIS. "
                + "Hàng đợi search_index_queue vẫn được ghi; cấu hình rồi khởi động lại worker sẽ tự bù.", props.isEnabled());
            return new SearchSyncRunner(null, null, props, null);
        }
        ElasticsearchHttpClient client = new ElasticsearchHttpClient(props, objectMapper);
        SearchIndexSyncService service = new SearchIndexSyncService(props, client, new SearchIndexQueueStore(),
            new SearchDocumentMapper(objectMapper, props), tenantRegistry, dataSources, objectMapper, meterRegistry);
        FileExtractService fileExtractService = null;
        if (props.getFileExtract().isEnabled()) {
            if (minio.isEnabled()) {
                fileExtractService = new FileExtractService(props, new FileExtractStore(), new SearchIndexQueueStore(),
                    new FileContentExtractor(props.getFileExtract()), minio, dataSources, meterRegistry);
            } else {
                log.warn("Trích nội dung tệp KHÔNG chạy: WORKER_FILE_EXTRACT_ENABLED=true nhưng MinIO tắt (MINIO_ENABLED=false). "
                    + "Hàng đợi tệp vẫn được ghi, bật MinIO rồi khởi động lại worker sẽ tự bù.");
            }
        }
        return new SearchSyncRunner(service, fileExtractService, props, tenantConnectionFactory(tenantRegistry));
    }

    /** Kết nối RIÊNG cho LISTEN (ngoài pool) — cùng thông tin/giải mã mật khẩu như TenantDataSourceManager. */
    static SearchNotificationListener.TenantConnectionFactory tenantConnectionFactory(TenantRegistry registry) {
        return tenantId -> {
            TenantRecord record = registry.find(tenantId)
                .orElseThrow(() -> new SQLException("Tenant '" + tenantId + "' không còn active"));
            String password = registry.cipher().map(c -> c.decrypt(record.encryptedPassword()))
                .orElseThrow(() -> new SQLException("Không có cipher giải mã mật khẩu tenant '" + tenantId + "'"));
            Properties info = new Properties();
            info.setProperty("user", record.username());
            info.setProperty("password", password);
            info.setProperty("tcpKeepAlive", "true");
            info.setProperty("ApplicationName", "td-worker-search-listen");
            return DriverManager.getConnection(record.jdbcUrl(), info);
        };
    }

    @Bean
    public HealthIndicator searchSyncHealthIndicator(SearchSyncRunner runner) {
        return () -> {
            SearchIndexSyncService service = runner.service();
            if (service == null) {
                return Health.unknown().withDetail("message", "Chưa cấu hình (ELASTICSEARCH_URIS trống hoặc tắt)").build();
            }
            // Không trả DOWN: ES lỗi không được làm worker bị coi là chết (xử lý văn bản vẫn chạy bình thường).
            Health.Builder builder = service.lastError() == null ? Health.up() : Health.status("DEGRADED");
            return builder
                .withDetail("lastOkAt", String.valueOf(service.lastCycleOkAt()))
                .withDetail("lastError", String.valueOf(service.lastError()))
                .withDetail("queueSize", service.queueTotal())
                .withDetail("queueFailing", service.queueFailing())
                .withDetail("notifyEnabled", runner.props.isNotifyEnabled())
                .withDetail("listeners", runner.listener == null ? 0 : runner.listener.listenerCount())
                .withDetail("listenersConnected", runner.listener == null ? 0 : runner.listener.connectedCount())
                .withDetail("fileExtractEnabled", runner.fileExtractService != null)
                .build();
        };
    }

    /** Khởi động/dừng các thành phần đồng bộ theo vòng đời Spring. */
    public static class SearchSyncRunner implements SmartLifecycle {

        private final SearchIndexSyncService service;
        /** null = trích nội dung tệp tắt. */
        private final FileExtractService fileExtractService;
        private final SearchSyncProperties props;
        private final SearchNotificationListener.TenantConnectionFactory connectionFactory;
        private SearchSyncCoordinator coordinator;
        private SearchSyncCoordinator fileCoordinator;
        private SearchNotificationListener listener;
        private ScheduledExecutorService sweeper;

        SearchSyncRunner(SearchIndexSyncService service, FileExtractService fileExtractService, SearchSyncProperties props,
                         SearchNotificationListener.TenantConnectionFactory connectionFactory) {
            this.service = service;
            this.fileExtractService = fileExtractService;
            this.props = props;
            this.connectionFactory = connectionFactory;
        }

        SearchIndexSyncService service() {
            return service;
        }

        @Override
        public synchronized void start() {
            if (service == null || sweeper != null) {
                return;
            }
            coordinator = new SearchSyncCoordinator(service::syncTenant, props.getMaxParallelTenants(),
                props.getUnavailableRetryDelay());
            if (fileExtractService != null) {
                // Làn RIÊNG (luồng riêng): trích tệp lớn không làm chậm đồng bộ văn bản.
                fileCoordinator = new SearchSyncCoordinator(fileExtractService::extractTenant,
                    props.getFileExtract().getMaxParallelTenants(), props.getUnavailableRetryDelay());
            }
            if (props.isNotifyEnabled()) {
                listener = new SearchNotificationListener(connectionFactory, this::wake, props.getNotifyChannel(),
                    props.getListenerPingInterval(), props.getListenerReconnectDelay());
            }
            sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "search-sync-sweep");
                thread.setDaemon(true);
                return thread;
            });
            // Lượt đầu chạy ngay: bù mọi thay đổi trong lúc worker tắt + tạo index/nạp lần đầu.
            long interval = props.getFallbackSweepInterval().toMillis();
            sweeper.scheduleWithFixedDelay(this::sweep, 0, interval, TimeUnit.MILLISECONDS);
            log.info("Đồng bộ Elasticsearch đã chạy: uris={}, prefix={}, mappingVersion={}, notify={}, quét dự phòng mỗi {}, "
                    + "song song {} tenant, entities={}", props.getUris(), props.getIndexPrefix(), props.getMappingVersion(),
                props.isNotifyEnabled(), props.getFallbackSweepInterval(), props.getMaxParallelTenants(),
                props.getEntities().keySet());
        }

        private void sweep() {
            try {
                List<String> tenants = service.tenantIds();
                if (listener != null) {
                    listener.listenTo(tenants);
                }
                for (String tenantId : tenants) {
                    coordinator.request(tenantId, true);
                    if (fileCoordinator != null) {
                        fileCoordinator.request(tenantId, true);
                    }
                }
            } catch (Throwable ex) {
                log.error("Quét dự phòng Elasticsearch lỗi: {}", ex.getMessage(), ex);
            }
        }

        /** Thông báo thay đổi của 1 tenant → đánh thức cả 2 làn. */
        private void wake(String tenantId) {
            coordinator.request(tenantId, false);
            if (fileCoordinator != null) {
                fileCoordinator.request(tenantId, false);
            }
        }

        @Override
        public synchronized void stop() {
            if (sweeper != null) {
                sweeper.shutdownNow();
                sweeper = null;
            }
            if (listener != null) {
                listener.close();
                listener = null;
            }
            if (coordinator != null) {
                coordinator.close();
                coordinator = null;
            }
            if (fileCoordinator != null) {
                fileCoordinator.close();
                fileCoordinator = null;
            }
        }

        @Override
        public synchronized boolean isRunning() {
            return sweeper != null;
        }
    }
}
