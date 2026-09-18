package com.td.infrastructure.errortracking;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Chạy Flyway RIÊNG cho Error Tracking DB khi khởi động (migration location riêng
 * {@code classpath:db/migration-errortracking}), độc lập hoàn toàn với
 * {@code TenantSchemaMigrator} của DB nghiệp vụ.
 *
 * <p><b>Khác biệt có chủ đích so với {@code TenantSchemaMigrator}</b>: migrator đó để lỗi ném thẳng
 * ra ngoài (làm app dừng khởi động nếu schema nghiệp vụ sai — đúng, vì bắt buộc đúng mới phục vụ
 * được request). Ở đây, lỗi migrate Error Tracking DB (vd. DB log chưa được provision, sai
 * credential) chỉ được LOG lại và KHÔNG làm app dừng khởi động — đúng yêu cầu tính năng phụ trợ này
 * không được ảnh hưởng tới API nghiệp vụ chính, kể cả khi bản thân nó chưa sẵn sàng.</p>
 */
@Component
@Slf4j
public class ErrorTrackingSchemaMigrator implements ApplicationRunner {

    private final ErrorTrackingProperties errorTrackingProperties;
    private final DataSource errorTrackingDataSource;

    // Khong dung @RequiredArgsConstructor: Lombok KHONG copy @Qualifier tu field sang tham so
    // constructor sinh ra (mac dinh khong nam trong danh sach "copyable annotations") - neu de
    // Lombok tu sinh constructor, Spring se autowire theo type va lang le chon nham DataSource
    // @Primary (DB nghiep vu) thay vi errorTrackingDataSource, vi hien co 2 bean cung type DataSource.
    public ErrorTrackingSchemaMigrator(
            ErrorTrackingProperties errorTrackingProperties,
            @Qualifier("errorTrackingDataSource") DataSource errorTrackingDataSource) {
        this.errorTrackingProperties = errorTrackingProperties;
        this.errorTrackingDataSource = errorTrackingDataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!errorTrackingProperties.isEnabled()) {
            log.info("Error Tracking dang bi tat (app.error-tracking.enabled=false) - bo qua migrate schema");
            return;
        }

        try {
            Flyway.configure()
                .dataSource(errorTrackingDataSource)
                .locations("classpath:db/migration-errortracking")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
            log.info("Migrate schema Error Tracking DB thanh cong");
        } catch (Exception ex) {
            log.error("Khong the migrate schema Error Tracking DB - tinh nang ghi log loi tap trung se " +
                "khong hoat dong cho den khi khac phuc (API nghiep vu khong bi anh huong)", ex);
        }
    }
}
