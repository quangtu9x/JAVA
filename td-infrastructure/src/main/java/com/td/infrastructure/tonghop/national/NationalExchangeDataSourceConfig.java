package com.td.infrastructure.tonghop.national;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;

/**
 * Kết nối CHỈ ĐỌC tới database riêng của TD Gateway (project {@code tdgateway}, mặc định DB {@code tdgateway})
 * để màn "Trục quốc gia" ở /tonghop đọc read model quản trị (V8/V9/V15 của gateway).
 *
 * <ul>
 *   <li>Chỉ tạo khi {@code app.national-exchange.admin.enabled=true} (mặc định TẮT — môi trường chưa có gateway
 *       vẫn chạy bình thường, API trả thông báo "chưa cấu hình").</li>
 *   <li>Pool KHÔNG đăng ký thành bean {@code DataSource}: Actuator gộp mọi bean DataSource vào
 *       {@code /actuator/health} — gateway DB ngắt sẽ kéo health td-web xuống DOWN. Repository tự giữ + đóng pool.</li>
 *   <li>{@code initializationFailTimeout=-1} (như {@code ErrorTrackingDataSourceConfig}): DB gateway chưa sẵn sàng
 *       không làm sập khởi động td-web.</li>
 *   <li>{@code readOnly}: td-web không bao giờ ghi DB gateway — schema do Flyway gateway sở hữu, thao tác quản trị
 *       phải qua hàng đợi lệnh + executor của gateway (tdgateway/docs/22 §4).</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.national-exchange.admin", name = "enabled", havingValue = "true")
public class NationalExchangeDataSourceConfig {

    @Value("${app.national-exchange.admin.datasource.url}")
    private String url;

    @Value("${app.national-exchange.admin.datasource.username:}")
    private String username;

    @Value("${app.national-exchange.admin.datasource.password:}")
    private String password;

    @Value("${app.national-exchange.admin.datasource.maximum-pool-size:4}")
    private int maximumPoolSize;

    /** Tài khoản GHI lệnh quản trị — bỏ trống thì dùng lại tài khoản đọc (dev); production nên tách (docs/24 §2.7). */
    @Value("${app.national-exchange.admin.command-datasource.username:}")
    private String commandUsername;

    @Value("${app.national-exchange.admin.command-datasource.password:}")
    private String commandPassword;

    /**
     * 22/10/2026: tên cơ quan Trục tra Redis trước (gateway đẩy danh bạ lên HASH {@code <prefix>:names}) — prefix
     * PHẢI khớp {@code GATEWAY_ADMIN_AGENCY_REDIS_KEY_PREFIX} của gateway, cùng Redis + database.
     */
    @Value("${app.national-exchange.admin.agency-redis-enabled:true}")
    private boolean agencyRedisEnabled;

    @Value("${app.national-exchange.admin.agency-redis-key-prefix:tdgateway:national-agency}")
    private String agencyRedisKeyPrefix;

    @Bean(destroyMethod = "close")
    public PostgresNationalExchangeQueryRepository nationalExchangeQueryRepository(
            @Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource, ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redis) {
        return new PostgresNationalExchangeQueryRepository(gatewayPool("hikari-national-exchange", username, password,
            Math.max(1, maximumPoolSize), true), controlPlaneDataSource, objectMapper,
            agencyRedisEnabled ? new RedisNationalAgencyNames(redis, agencyRedisKeyPrefix) : null);
    }

    /**
     * Ghi hàng đợi lệnh quản trị — chỉ khi bật thao tác ({@code NATIONAL_EXCHANGE_OPERATIONS_ENABLED}). Pool riêng
     * (KHÔNG readOnly), tối đa 2 kết nối, cùng không chặn khởi động.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.national-exchange.admin", name = "operations-enabled", havingValue = "true")
    public PostgresNationalExchangeCommandRepository nationalExchangeCommandRepository() {
        boolean own = commandUsername != null && !commandUsername.isBlank();
        return new PostgresNationalExchangeCommandRepository(gatewayPool("hikari-national-exchange-cmd",
            own ? commandUsername : username, own ? commandPassword : password, 2, false));
    }

    /** Proxy tải gói eDXML — chỉ khi cấu hình {@code NATIONAL_EXCHANGE_GATEWAY_URL}. */
    @Bean
    @ConditionalOnExpression("'${app.national-exchange.admin.gateway.url:}'.trim().length() > 0")
    public HttpNationalExchangePackageGateway nationalExchangePackageGateway(
            @Value("${app.national-exchange.admin.gateway.url}") String gatewayUrl,
            @Value("${app.national-exchange.admin.gateway.token:}") String gatewayToken) {
        return new HttpNationalExchangePackageGateway(gatewayUrl, gatewayToken);
    }

    private HikariDataSource gatewayPool(String poolName, String user, String pass, int maxSize, boolean readOnly) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(maxSize);
        config.setConnectionTimeout(5000);
        config.setReadOnly(readOnly);
        config.setAutoCommit(true);
        // Màn quản trị không được giữ truy vấn lâu trên read model gateway (trigger runtime ghi liên tục).
        config.addDataSourceProperty("options", "-c statement_timeout=15000");
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }
}
