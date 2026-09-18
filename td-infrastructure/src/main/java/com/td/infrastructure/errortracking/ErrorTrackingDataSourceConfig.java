package com.td.infrastructure.errortracking;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * DataSource RIÊNG cho Error Tracking DB — hoàn toàn độc lập với {@code DataSource} {@code @Primary}
 * của DB nghiệp vụ (khai báo tại {@code TenantDataSourceConfig}, xem package
 * {@code com.td.infrastructure.tenant}). Không dùng {@code @Primary} ở đây để tránh xung đột —
 * Spring tiếp tục autowire {@code DataSource} nghiệp vụ mặc định ở mọi nơi khác trong app.
 *
 * <p><b>KHÔNG được chặn khởi động app khi DB log tạm thời không kết nối được</b> — đây là bài học từ
 * 1 sự cố thật: ban đầu dùng JPA/Hibernate ({@code LocalContainerEntityManagerFactoryBean}), việc
 * bootstrap Hibernate SessionFactory LUÔN cố mở 1 connection đọc JDBC metadata NGAY LÚC BEAN ĐƯỢC
 * TẠO — kể cả đánh dấu {@code @Lazy}/{@code bootstrapMode=LAZY} (đã kiểm chứng bằng test thực tế
 * vẫn KHÔNG ngăn được, xem lịch sử file này). Nếu DB Error Tracking chưa sẵn sàng, bước này ném
 * exception làm SẬP TOÀN BỘ context Spring — bất kể có consumer nào đang dùng {@code ObjectProvider}
 * để hoãn resolve repository hay không.
 *
 * <p>Fix: bỏ hoàn toàn JPA/Hibernate cho Error Tracking, dùng {@code JdbcTemplate} thuần (xem
 * {@link ErrorLogJdbcRepository}). Tạo bean {@code JdbcTemplate} KHÔNG BAO GIỜ chạm DB — nó chỉ giữ
 * tham chiếu {@code DataSource}; connection thật sự chỉ được mở khi code GỌI QUERY, đúng thời điểm
 * nằm trong khối try-catch của {@link ErrorTrackingService}. Kết hợp {@code initializationFailTimeout(-1)}
 * của Hikari (cho phép bean {@code DataSource} tạo thành công dù DB chưa kết nối được), toàn bộ chuỗi
 * bean trong file này giờ CHẮC CHẮN không thể làm sập app lúc khởi động — xem
 * {@code ErrorTrackingStartupResilienceTest} (test dựng context thật với DB không tồn tại để xác nhận).</p>
 */
@Configuration
@RequiredArgsConstructor
public class ErrorTrackingDataSourceConfig {

    private final ErrorTrackingProperties errorTrackingProperties;

    @Bean(name = "errorTrackingDataSource")
    public DataSource errorTrackingDataSource() {
        ErrorTrackingProperties.Datasource ds = errorTrackingProperties.getDatasource();

        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-error-tracking");
        config.setJdbcUrl(ds.getUrl());
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setDriverClassName(ds.getDriverClassName());
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(5000);
        // Cho phep bean khoi tao thanh cong ngay ca khi DB log dang khong ket noi duoc - xem javadoc lop.
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean(name = "errorTrackingJdbcTemplate")
    public JdbcTemplate errorTrackingJdbcTemplate(@Qualifier("errorTrackingDataSource") DataSource errorTrackingDataSource) {
        return new JdbcTemplate(errorTrackingDataSource);
    }
}
