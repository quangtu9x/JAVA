package com.td.infrastructure.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Route mỗi connection tới đúng {@link javax.sql.DataSource} của tenant hiện tại
 * ({@link TenantContext}) — kể từ BL-117, mỗi tenant có 1 database Postgres VẬT LÝ RIÊNG BIỆT
 * (trước đây BL-303 chỉ khác schema cùng 1 database — xem lịch sử
 * {@code docs/architecture/05-shared-schema-multi-tenant.md}).
 *
 * <p>Trả về {@code null} khi chưa xác định tenant (vd. Flyway chạy lúc khởi động, ngoài vòng đời
 * request/{@code TenantFilter}) — {@link AbstractRoutingDataSource} sẽ dùng
 * {@code defaultTargetDataSource} trong trường hợp này.
 *
 * <p>BL-117: thêm khả năng "hot-add" — {@code PlatformTenantsController} tạo tenant mới lúc app
 * ĐANG CHẠY (không cần restart) nên cần thêm 1 {@link DataSource} vào routing NGAY LẬP TỨC. Giữ
 * riêng {@link #knownDataSources} (KHÁC field private {@code resolvedDataSources} bên trong
 * {@link AbstractRoutingDataSource}, Spring không expose API đọc lại) làm "nguồn sự thật" đầy đủ —
 * mỗi lần hot-add đều gọi lại {@code setTargetDataSources}+{@code afterPropertiesSet()} với TOÀN BỘ
 * map này (không chỉ entry mới) để không làm mất các tenant đã đăng ký trước đó.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> knownDataSources = new LinkedHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }

    /**
     * Bugfix 03/09/2026 (báo lỗi người dùng: đăng nhập lại tenant "skhcn" (kể cả tab ẩn danh) tạm
     * thời không thấy văn bản của chính mình, "1 lúc sau lại đúng" — đã loại trừ nguyên nhân FE vì
     * tái hiện được ở tab ẩn danh, tức lỗi ở server): {@link AbstractRoutingDataSource} có
     * {@code lenientFallback=true} MẶC ĐỊNH (chưa từng set {@code false} ở đây) — nghĩa là BẤT KỲ
     * lookup key (tenant_id) nào KHÔNG khớp entry nào trong {@link #knownDataSources} (không riêng
     * gì {@code null}, xem javadoc lớp) sẽ ÂM THẦM dùng {@code defaultTargetDataSource} thay vì báo
     * lỗi — nếu {@code TenantActiveRegistry} (dùng bởi {@code TenantResolver} để quyết định tenant
     * "biết") và {@link #knownDataSources} (dùng ở ĐÂY để thực sự lấy connection) có bất kỳ khoảnh
     * khắc nào lệch nhau (2 nguồn đăng ký riêng biệt — xem {@code TenantDataSourceConfig}/hot-add
     * qua {@code registerOrReplace}), request của tenant đó sẽ bị route NHẦM sang database tenant
     * MẶC ĐỊNH — user thấy "mất" đúng dữ liệu của mình (đang truy vấn nhầm database) mà KHÔNG có lỗi
     * nào hiện ra, đúng triệu chứng báo cáo. Đúng triết lý đã áp dụng cho tenant bị khoá
     * ({@code TenantNotActiveException} — TỪ CHỐI hẳn thay vì âm thầm fallback, xem javadoc
     * {@code TenantResolver}): CHỈ cho phép fallback về default khi lookup key THỰC SỰ null (đúng
     * ngữ cảnh nằm ngoài vòng đời request — Flyway lúc khởi động) — mọi lookup key KHÔNG null nhưng
     * KHÔNG khớp {@link #knownDataSources} phải TỪ CHỐI RÕ RÀNG (throw), không được lặng lẽ phục vụ
     * dữ liệu tenant khác.
     */
    @Override
    protected DataSource determineTargetDataSource() {
        Object lookupKey = determineCurrentLookupKey();
        if (lookupKey != null && !knownDataSources.containsKey(lookupKey)) {
            throw new IllegalStateException(
                "Tenant '" + lookupKey + "' da duoc xac dinh tu request (TenantResolver) nhung KHONG "
                    + "co connection pool tuong ung trong TenantRoutingDataSource - co the do "
                    + "TenantActiveRegistry va danh sach DataSource that su bi lech nhau. TU CHOI request "
                    + "nay thay vi am tham dung database cua tenant mac dinh (tranh phuc vu nham du lieu "
                    + "tenant khac).");
        }
        return super.determineTargetDataSource();
    }

    /**
     * Nạp TOÀN BỘ tenant lúc khởi động ({@code TenantDataSourceConfig.dataSource()}) — GHI ĐÈ
     * {@link #knownDataSources} thay vì cộng dồn (chỉ gọi đúng 1 lần lúc bean {@code DataSource}
     * được tạo).
     */
    public synchronized void initialize(Map<String, DataSource> initialDataSources, DataSource defaultDataSource) {
        knownDataSources.clear();
        knownDataSources.putAll(initialDataSources);
        setTargetDataSources(new LinkedHashMap<>(knownDataSources));
        setDefaultTargetDataSource(defaultDataSource);
        afterPropertiesSet();
    }

    /**
     * Thêm mới HOẶC thay thế (xoay vòng credential) connection pool của 1 tenant, có hiệu lực NGAY
     * (không cần restart app) — CỘNG DỒN vào {@link #knownDataSources} đã có từ {@link #initialize}
     * (khác {@link #initialize}, method này KHÔNG xoá các tenant đã đăng ký trước đó). An toàn gọi
     * nhiều lần — {@code afterPropertiesSet()} chỉ build lại {@code resolvedDataSources} nội bộ từ
     * {@link #knownDataSources}, không đóng pool cũ còn đang dùng (Hikari tự đóng driver cũ nếu bị
     * GC — chấp nhận rò rỉ nhỏ trong trường hợp hiếm xoay vòng credential, đơn giản hơn hẳn so với tự
     * quản lý vòng đời đóng pool cũ giữa lúc có thể đang có request khác dùng).
     */
    public synchronized void registerOrReplace(String tenantId, DataSource dataSource) {
        knownDataSources.put(tenantId, dataSource);
        setTargetDataSources(new LinkedHashMap<>(knownDataSources));
        afterPropertiesSet();
    }

    public synchronized boolean hasTenant(String tenantId) {
        return knownDataSources.containsKey(tenantId);
    }

    /**
     * Yêu cầu người dùng 04/09/2026 (outbox dispatcher cần lặp qua MỌI tenant đã biết) — bản sao
     * bất biến (snapshot) danh sách tenant id đang có connection pool, KHÔNG lộ
     * {@link #knownDataSources} gốc ra ngoài (tránh sửa nhầm từ nơi khác).
     */
    public synchronized java.util.Set<String> knownTenantIds() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (Object key : knownDataSources.keySet()) {
            if (key instanceof String s) {
                ids.add(s);
            }
        }
        return ids;
    }

    /**
     * Bugfix 04/09/2026 (người dùng báo cảnh báo Tomcat "appears to have started a thread named
     * [hikari-tenant-H05_02 housekeeper] but has failed to stop it" — thread leak lúc webapp
     * reload/shutdown): mỗi tenant có 1 {@code HikariDataSource} RIÊNG được tạo thủ công
     * ({@code TenantPoolFactory}) và chỉ tồn tại bên trong {@link #knownDataSources} — Spring KHÔNG
     * biết tới các object này (không phải bean riêng lẻ) nên KHÔNG tự đóng khi context shutdown.
     * Chỉ tenant MẶC ĐỊNH "vô tình" được đóng đúng vì {@code identityDataSource} (bean khác, cùng
     * {@code TenantDataSourceConfig}) trả về THẲNG {@code HikariDataSource} đó — Spring tự suy ra
     * destroy method {@code close()} cho bean trả về kiểu có method này. Mọi tenant KHÁC bị bỏ sót.
     *
     * <p>Gọi ở {@code @Bean(destroyMethod = "closeAll")} của {@code TenantDataSourceConfig.dataSource()}
     * — đóng TẤT CẢ pool (kể cả pool của tenant mặc định, gọi {@code close()} 2 lần vô hại vì
     * {@code HikariDataSource.close()} tự kiểm tra {@code isClosed()}).</p>
     */
    public synchronized void closeAll() {
        for (Object ds : knownDataSources.values()) {
            if (ds instanceof java.io.Closeable closeable) {
                try {
                    closeable.close();
                } catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(TenantRoutingDataSource.class)
                        .warn("Loi khi dong connection pool tenant luc shutdown (bo qua, tiep tuc dong cac pool con lai)", ex);
                }
            }
        }
    }
}
