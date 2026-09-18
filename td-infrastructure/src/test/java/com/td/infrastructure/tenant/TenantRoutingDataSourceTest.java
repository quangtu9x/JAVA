package com.td.infrastructure.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantRoutingDataSourceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void determineCurrentLookupKey_shouldReturnTenantContextValue() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        TenantContext.setCurrentTenant("K05");
        assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo("K05");

        TenantContext.setCurrentTenant("H05");
        assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo("H05");
    }

    @Test
    void determineCurrentLookupKey_shouldBeNull_whenTenantContextNotSet() {
        // Mô phỏng lúc Flyway chạy ở startup — ngoài vòng đời request, TenantContext chưa được
        // set. AbstractRoutingDataSource sẽ tự dùng defaultTargetDataSource khi lookup key null.
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        assertThat(routingDataSource.determineCurrentLookupKey()).isNull();
    }

    @Test
    void routing_shouldResolveDifferentDataSourcesPerTenant_endToEnd() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        DataSource h05DataSource = mock(DataSource.class);
        DataSource k05DataSource = mock(DataSource.class);
        routingDataSource.setTargetDataSources(Map.of("H05", h05DataSource, "K05", k05DataSource));
        routingDataSource.setDefaultTargetDataSource(h05DataSource);
        routingDataSource.afterPropertiesSet();

        TenantContext.setCurrentTenant("K05");
        assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo("K05");

        TenantContext.setCurrentTenant("H05");
        assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo("H05");
    }

    // BL-117: PlatformTenantsController "khởi tạo đơn vị" phải hot-add pool NGAY lúc app đang chạy,
    // không cần restart.
    @Test
    void initialize_shouldSetUpDefaultAndAllTenants() throws Exception {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        DataSource h05DataSource = mock(DataSource.class);
        Connection h05Connection = mock(Connection.class);
        when(h05DataSource.getConnection()).thenReturn(h05Connection);

        routingDataSource.initialize(Map.of("H05", h05DataSource), h05DataSource);

        TenantContext.setCurrentTenant("H05");
        assertThat(routingDataSource.getConnection()).isSameAs(h05Connection);
        assertThat(routingDataSource.hasTenant("H05")).isTrue();
        assertThat(routingDataSource.hasTenant("K05")).isFalse();
    }

    @Test
    void registerOrReplace_shouldHotAddNewTenant_withoutLosingExistingOnes() throws Exception {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        DataSource h05DataSource = mock(DataSource.class);
        Connection h05Connection = mock(Connection.class);
        when(h05DataSource.getConnection()).thenReturn(h05Connection);
        routingDataSource.initialize(Map.of("H05", h05DataSource), h05DataSource);

        DataSource k05DataSource = mock(DataSource.class);
        Connection k05Connection = mock(Connection.class);
        when(k05DataSource.getConnection()).thenReturn(k05Connection);
        routingDataSource.registerOrReplace("K05", k05DataSource);

        assertThat(routingDataSource.hasTenant("H05"))
            .as("Hot-add 1 tenant moi khong duoc lam mat tenant da co san")
            .isTrue();
        assertThat(routingDataSource.hasTenant("K05")).isTrue();

        TenantContext.setCurrentTenant("K05");
        assertThat(routingDataSource.getConnection()).isSameAs(k05Connection);

        TenantContext.setCurrentTenant("H05");
        assertThat(routingDataSource.getConnection()).isSameAs(h05Connection);
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng: đăng nhập lại tenant "skhcn" — kể cả tab ẩn danh —
    // tạm thời không thấy văn bản của chính mình, tự khỏi "1 lúc sau"; đã loại trừ FE vì tái hiện
    // được ở tab ẩn danh): AbstractRoutingDataSource mặc định lenientFallback=true — lookup key
    // KHÔNG null nhưng KHÔNG khớp knownDataSources trước đây bị ÂM THẦM route sang default (nguy cơ
    // phục vụ nhầm dữ liệu tenant khác nếu TenantActiveRegistry/knownDataSources lệch nhau) — nay
    // phải TỪ CHỐI RÕ RÀNG.
    @Test
    void determineTargetDataSource_shouldThrow_whenLookupKeyKnownButNoMatchingPool() throws Exception {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        DataSource h05DataSource = mock(DataSource.class);
        routingDataSource.initialize(Map.of("H05", h05DataSource), h05DataSource);

        // "K05" khong nam trong knownDataSources (chua initialize/registerOrReplace) - mo phong tinh
        // huong TenantActiveRegistry noi "biet" tenant nay nhung pool that su chua/khong con dang ky.
        TenantContext.setCurrentTenant("K05");

        assertThatThrownBy(routingDataSource::getConnection)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("K05");
    }

    @Test
    void determineTargetDataSource_shouldStillFallbackToDefault_whenLookupKeyIsNull() throws Exception {
        // Truong hop null (Flyway luc khoi dong, ngoai vong doi request) VAN duoc phep fallback -
        // KHONG bi guard moi chan (chi chan non-null-nhung-khong-khop).
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        DataSource h05DataSource = mock(DataSource.class);
        Connection h05Connection = mock(Connection.class);
        when(h05DataSource.getConnection()).thenReturn(h05Connection);
        routingDataSource.initialize(Map.of("H05", h05DataSource), h05DataSource);

        TenantContext.clear();

        assertThat(routingDataSource.getConnection()).isSameAs(h05Connection);
    }

    /**
     * Yêu cầu người dùng 04/09/2026 (outbox dispatcher cần lặp qua MỌI tenant đã biết — xem
     * {@code OutboxDispatcherScheduler}).
     */
    @Test
    void knownTenantIds_shouldReflectInitializeAndHotAdd() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        DataSource h05DataSource = mock(DataSource.class);
        routingDataSource.initialize(Map.of("H05", h05DataSource), h05DataSource);

        assertThat(routingDataSource.knownTenantIds()).containsExactly("H05");

        DataSource k05DataSource = mock(DataSource.class);
        routingDataSource.registerOrReplace("K05", k05DataSource);

        assertThat(routingDataSource.knownTenantIds()).containsExactlyInAnyOrder("H05", "K05");
    }

    /**
     * Bugfix 04/09/2026 (Tomcat WARN "hikari-tenant-... housekeeper... failed to stop" lúc
     * shutdown/reload — connection pool tenant KHÔNG phải bean riêng nên Spring không tự đóng).
     */
    @Test
    void closeAll_shouldCloseEveryKnownDataSourceThatIsCloseable() throws Exception {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        CloseableDataSource h05DataSource = mock(CloseableDataSource.class);
        CloseableDataSource k05DataSource = mock(CloseableDataSource.class);
        routingDataSource.initialize(Map.of("H05", h05DataSource, "K05", k05DataSource), h05DataSource);

        routingDataSource.closeAll();

        org.mockito.Mockito.verify(h05DataSource).close();
        org.mockito.Mockito.verify(k05DataSource).close();
    }

    @Test
    void closeAll_shouldNotThrow_whenADataSourceCloseFails() throws Exception {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        CloseableDataSource faultyDataSource = mock(CloseableDataSource.class);
        CloseableDataSource healthyDataSource = mock(CloseableDataSource.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(faultyDataSource).close();
        routingDataSource.initialize(Map.of("H05", faultyDataSource, "K05", healthyDataSource), healthyDataSource);

        routingDataSource.closeAll();

        org.mockito.Mockito.verify(healthyDataSource).close();
    }

    /** DataSource + Closeable, dùng để mock trong test (Mockito không mock interface hợp nhất được trực tiếp). */
    private interface CloseableDataSource extends DataSource, java.io.Closeable {
    }
}
