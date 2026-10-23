package com.td.infrastructure.fileregistry;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.UUID;

/**
 * Sổ đăng ký tệp đính kèm DÙNG CHUNG giữa các tenant (yêu cầu người dùng 22/10/2026): văn bản đến nội tỉnh,
 * văn bản đến từ trục quốc gia và bản sao "Chuyển xử lý" có dòng {@code file_metadata} riêng ở từng tenant
 * nhưng cùng trỏ 1 object MinIO. td-woker ghi tham chiếu ({@code shared_file_ref}) khi tạo văn bản và chạy
 * job dọn dẹp xóa object khi không còn tham chiếu; phía td-web chỉ:
 * <ul>
 *   <li>tra {@link #hasActiveRefs} trước khi xóa object của 1 dòng chủ sở hữu ({@code is_shared=FALSE}) —
 *       vd tệp gốc của văn bản đã được "Chuyển xử lý" sang đơn vị khác;</li>
 *   <li>{@link #release} khi văn bản chứa dòng dùng chung bị xóa vĩnh viễn.</li>
 * </ul>
 *
 * <p>Pool KHÔNG đăng ký thành bean {@code DataSource} (cùng lý do {@code NationalExchangeDataSourceConfig}):
 * Actuator gộp mọi bean DataSource vào health — DB sổ đăng ký ngắt không được kéo td-web xuống DOWN, cũng
 * không để Spring autowire nhầm DataSource nghiệp vụ. {@code initializationFailTimeout=-1}: DB chưa sẵn sàng
 * không làm sập khởi động.</p>
 */
@Slf4j
@Component
public class SharedFileRegistryClient implements DisposableBean {

    private final FileRegistryProperties properties;
    private volatile HikariDataSource dataSource;

    public SharedFileRegistryClient(FileRegistryProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled()
            && properties.getDatasource().getUrl() != null
            && !properties.getDatasource().getUrl().isBlank();
    }

    /** Còn dòng {@code file_metadata} dùng chung (ở bất kỳ tenant nào) chưa nhả tham chiếu tới object này. */
    public boolean hasActiveRefs(String bucketName, String objectKey) {
        Boolean exists = jdbc().queryForObject("""
                SELECT EXISTS (SELECT 1 FROM shared_file_ref
                               WHERE bucket_name = :bucket AND object_key = :key AND released_at IS NULL)
                """,
            new MapSqlParameterSource("bucket", bucketName).addValue("key", objectKey), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Nhả tham chiếu của các dòng {@code file_metadata} đã bị xóa. Object nào hết tham chiếu được đánh dấu
     * {@code zero_ref_since} — job dọn dẹp td-woker chỉ xóa object sau thời gian chờ và sau khi kiểm tra lại
     * {@code file_metadata} mọi tenant.
     */
    public void release(Collection<UUID> fileMetadataIds) {
        if (fileMetadataIds == null || fileMetadataIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", fileMetadataIds);
        NamedParameterJdbcTemplate jdbc = jdbc();
        new TransactionTemplate(new DataSourceTransactionManager(dataSource())).executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE shared_file_ref SET released_at = now()
                    WHERE file_metadata_id IN (:ids) AND released_at IS NULL
                    """, params);
            jdbc.update("""
                    UPDATE shared_file_object o SET zero_ref_since = now()
                    WHERE o.zero_ref_since IS NULL AND o.deleted_at IS NULL
                      AND EXISTS (SELECT 1 FROM shared_file_ref r
                                  WHERE r.bucket_name = o.bucket_name AND r.object_key = o.object_key
                                    AND r.file_metadata_id IN (:ids))
                      AND NOT EXISTS (SELECT 1 FROM shared_file_ref r
                                      WHERE r.bucket_name = o.bucket_name AND r.object_key = o.object_key
                                        AND r.released_at IS NULL)
                    """, params);
        });
    }

    /** Dùng cho {@link FileRegistrySchemaMigrator}. */
    DataSource dataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (this) {
                ds = dataSource;
                if (ds == null) {
                    dataSource = ds = buildPool();
                }
            }
        }
        return ds;
    }

    private NamedParameterJdbcTemplate jdbc() {
        return new NamedParameterJdbcTemplate(dataSource());
    }

    private HikariDataSource buildPool() {
        FileRegistryProperties.Datasource ds = properties.getDatasource();
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-file-registry");
        config.setJdbcUrl(ds.getUrl());
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setDriverClassName(ds.getDriverClassName());
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(Math.max(1, ds.getMaximumPoolSize()));
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Override
    public void destroy() {
        HikariDataSource ds = dataSource;
        if (ds != null) {
            ds.close();
        }
    }
}
