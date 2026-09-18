package com.td.infrastructure.errortracking;

import com.td.infrastructure.security.CurrentUserContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test cho 1 sự cố THẬT đã xảy ra: trước fix, app SẬP HOÀN TOÀN LÚC KHỞI ĐỘNG nếu DB
 * Error Tracking không kết nối được — vì {@code errorTrackingEntityManagerFactory} (thiết kế JPA/
 * Hibernate ban đầu) bootstrap SessionFactory (cố mở connection đọc JDBC metadata) NGAY LÚC BEAN
 * ĐƯỢC TẠO, và việc này KHÔNG bị ngăn được kể cả khi đánh dấu {@code @Lazy}/{@code bootstrapMode=LAZY}
 * (đã tự kiểm chứng bằng chính test này khi còn dùng JPA — vẫn fail). Lỗi đó lan qua
 * {@code ApiResponseAuthenticationEntryPoint}/{@code TdTokenAuthenticationFilter} (hạ tầng bảo mật
 * cốt lõi) làm sập TOÀN BỘ context.
 *
 * <p>Fix: bỏ hẳn JPA/Hibernate, dùng {@code JdbcTemplate} thuần ({@link ErrorLogJdbcRepository}) —
 * tạo bean không bao giờ chạm DB, chỉ chạm khi query THẬT SỰ được gọi (bên trong try-catch của
 * {@link ErrorTrackingService}). Test dựng 1 context Spring TỐI GIẢN (không cần
 * {@code TdWebApiApplication}/DB nghiệp vụ/Keycloak — không có sẵn trong môi trường CI này) chỉ gồm
 * đúng phần Error Tracking, trỏ datasource tới 1 địa chỉ CHẮC CHẮN không ai lắng nghe, xác nhận:
 * (1) context load thành công, (2) {@code captureError} vẫn hoạt động (degrade êm, trả về Error ID)
 * thay vì ném exception.</p>
 */
@SpringJUnitConfig(classes = ErrorTrackingStartupResilienceTest.TestConfig.class)
@TestPropertySource(properties = {
    "app.error-tracking.datasource.url=jdbc:postgresql://localhost:1/khong-ton-tai",
    "app.error-tracking.datasource.username=bogus",
    "app.error-tracking.datasource.password=bogus"
})
class ErrorTrackingStartupResilienceTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ErrorTrackingService errorTrackingService;

    @Test
    void contextShouldLoadSuccessfully_evenWhenErrorTrackingDbUnreachable() {
        // Neu fix khong dung, @SpringJUnitConfig se nem BeanCreationException tu buoc load context
        // nay - test se fail o day (context loading), khong can toi assertion nao ca.
        assertThat(context.getBean(ErrorTrackingService.class)).isNotNull();
        assertThat(context.getBean(ErrorLogCleanupScheduler.class)).isNotNull();
    }

    @Test
    void captureError_shouldDegradeGracefully_insteadOfThrowing_whenDbUnreachable() {
        String errorId = errorTrackingService.captureError(null, HttpStatus.INTERNAL_SERVER_ERROR, new RuntimeException("boom"));

        assertThat(errorId).isNotBlank().startsWith("ERR-");
    }

    @Configuration
    @EnableConfigurationProperties(ErrorTrackingProperties.class)
    @Import({ErrorTrackingDataSourceConfig.class, ErrorLogJdbcRepository.class, ErrorTrackingService.class, ErrorLogCleanupScheduler.class})
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        CurrentUserContextResolver currentUserContextResolver(CacheManager cacheManager) {
            return new CurrentUserContextResolver(cacheManager);
        }
    }
}
