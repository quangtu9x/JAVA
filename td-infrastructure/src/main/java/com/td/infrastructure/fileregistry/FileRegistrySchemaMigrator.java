package com.td.infrastructure.fileregistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Chạy Flyway RIÊNG cho DB sổ đăng ký tệp dùng chung ({@code classpath:db/migration-fileregistry}) — cùng
 * cách {@code ErrorTrackingSchemaMigrator}: lỗi chỉ được LOG, không chặn khởi động td-web. Khi DB chưa
 * sẵn sàng, td-woker không ghi được tham chiếu (chỉ log) và job dọn dẹp không xóa gì — an toàn, chỉ chưa
 * thu hồi dung lượng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileRegistrySchemaMigrator implements ApplicationRunner {

    private final SharedFileRegistryClient registry;

    @Override
    public void run(ApplicationArguments args) {
        if (!registry.isEnabled()) {
            log.info("So dang ky tep dung chung dang tat (app.file-registry.enabled=false hoac thieu url) - bo qua migrate");
            return;
        }
        try {
            Flyway.configure()
                .dataSource(registry.dataSource())
                .locations("classpath:db/migration-fileregistry")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
            log.info("Migrate schema DB so dang ky tep dung chung thanh cong");
        } catch (Exception ex) {
            log.error("Khong the migrate DB so dang ky tep dung chung - object MinIO dung chung se khong duoc "
                + "thu hoi cho den khi khac phuc (khong anh huong API nghiep vu)", ex);
        }
    }
}
