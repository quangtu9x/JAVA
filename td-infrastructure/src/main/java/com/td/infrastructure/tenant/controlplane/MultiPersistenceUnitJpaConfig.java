package com.td.infrastructure.tenant.controlplane;

import com.td.domain.categories.Category;
import com.td.domain.categories.CategoryFileMetadata;
import com.td.domain.dbserver.DbServer;
import com.td.domain.tenant.Tenant;
import jakarta.persistence.Entity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * BL-117: khai báo THỦ CÔNG cả 2 {@link LocalContainerEntityManagerFactoryBean} của app (nghiệp vụ +
 * control-plane). Trước đây Spring Boot tự autoconfigure DUY NHẤT 1 {@code entityManagerFactory} mặc
 * định — kể từ khi có {@code Category}/{@code Tenant} cần sống ở 1 database VẬT LÝ RIÊNG BIỆT hoàn
 * toàn (không chỉ khác schema như BL-115 cũ), bắt buộc phải có {@code EntityManagerFactory} thứ 2.
 *
 * <p><b>Vì sao PHẢI khai báo lại CẢ HAI, không chỉ thêm 1 cái mới</b> (đã xác nhận bằng cách đọc trực
 * tiếp bytecode {@code JpaBaseConfiguration} — không chỉ suy đoán): bean {@code entityManagerFactory}
 * mặc định của Spring Boot có {@code @ConditionalOnMissingBean({LocalContainerEntityManagerFactoryBean.class,
 * EntityManagerFactory.class})} — điều kiện này xét theo KIỂU, trên TOÀN BỘ ApplicationContext. Chỉ
 * cần xuất hiện THÊM 1 bean kiểu {@code LocalContainerEntityManagerFactoryBean} (bean control-plane
 * mới) là Spring Boot sẽ ÂM THẦM BỎ QUA việc tạo bean {@code entityManagerFactory} mặc định của nó —
 * làm mất luôn EntityManagerFactory nghiệp vụ mà 33/35 repository còn lại đang dùng, sập toàn bộ app.
 * Cách khắc phục CHUẨN của Spring Boot cho tình huống "nhiều EntityManagerFactory" (xem tài liệu
 * chính thức "Configure Two Entity Managers") là tự khai báo LẠI cả 2, dùng chung
 * {@link EntityManagerFactoryBuilder} (bean này KHÔNG bị tắt — điều kiện `@ConditionalOnMissingBean`
 * của nó xét kiểu {@code EntityManagerFactoryBuilder}, khác kiểu, nên vẫn được Boot tự tạo sẵn, đã
 * gộp sẵn {@code spring.jpa.properties.*}/{@code JpaVendorAdapter} từ application.yml — không cần tự
 * dựng lại các bean đó).
 *
 * <p><b>Vì sao {@code Category}/{@code Tenant}/{@code DbServer} phải bị LOẠI HẲN khỏi EntityManagerFactory
 * nghiệp vụ</b> (không chỉ là dọn dẹp cho gọn): Hibernate {@code ddl-auto: validate} xác thực schema lúc
 * khởi động bằng ĐÚNG 1 connection duy nhất cho MỌI entity trong 1 EntityManagerFactory — nếu các entity
 * control-plane còn nằm trong danh sách entity của EntityManagerFactory nghiệp vụ (vốn nối
 * {@code TenantRoutingDataSource}, mặc định lúc khởi động resolve về database của tenant mặc định),
 * Hibernate sẽ cố tìm bảng tương ứng NGAY TRONG database tenant đó — bảng này KHÔNG tồn tại ở đó (đúng
 * chủ đích, chúng chỉ tồn tại ở control-plane) → lặp lại đúng lỗi {@code SchemaManagementException:
 * missing table} đã gặp ở BL-115. Vì vậy dùng {@link #scanPrimaryDomainEntities()} (quét toàn bộ
 * {@code @Entity} dưới {@code com.td.domain} rồi LOẠI TRỪ đúng các lớp control-plane) thay vì liệt kê
 * tay từng package còn lại (fragile — dễ quên khi có domain package mới trong tương lai). THÊM 1 entity
 * control-plane mới (vd {@code DbServer}, BL-117 bổ sung 19/08/2026; {@code CategoryFileMetadata},
 * BL-118 bổ sung 19/08/2026 — tệp đính kèm Banner/Footer/Hướng dẫn sử dụng) PHẢI sửa CẢ 2 chỗ trong file này:
 * {@code .packages(...)} của {@code controlPlaneEntityManagerFactory} VÀ tập {@code excluded} của
 * {@link #scanPrimaryDomainEntities()} — quên 1 trong 2 sẽ tái diễn đúng lỗi trên.
 *
 * <p><b>BL-118 (tiếp, 20/08/2026, bugfix NGHIÊM TRỌNG — persistence unit thứ 3, "identity")</b>: xác
 * nhận qua đối chiếu database thật (tài khoản "skhcn") — {@code AppUser}/{@code Organization}/
 * {@code RoleGroup}/{@code AppRole}/{@code AppPermission}... (toàn bộ {@code com.td.domain.sharedcore})
 * là "danh mục dùng chung" — KHÔNG được nhân bản theo từng tenant, sống CỐ ĐỊNH ở database của tenant
 * MẶC ĐỊNH. Trước đây các entity này nằm chung {@code entityManagerFactory} nghiệp vụ (routing theo
 * {@code TenantContext} của cả request) — khiến việc đổi tenant để ghi văn bản mới (theo
 * {@code Organization.linkedTenantId}) LÀM MẤT LUÔN profile/roles/menu_* (AppUser không hề tồn tại ở
 * tenant "hiệu lực" đó, vì vốn dĩ không được thiết kế để nhân bản). {@link #identityEntityManagerFactory}
 * tách hẳn các entity này ra 1 EntityManagerFactory RIÊNG, nối {@code identityDataSource} (LUÔN trỏ
 * database tenant mặc định, xem {@code TenantDataSourceConfig#identityDataSource} — KHÔNG qua routing)
 * — giải quyết TẬN GỐC, độc lập hoàn toàn với {@code TenantContext} của request đang xử lý.
 */
@Configuration
@RequiredArgsConstructor
public class MultiPersistenceUnitJpaConfig {

    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource) {
        return builder
            .dataSource(dataSource)
            .packages(scanPrimaryDomainEntities())
            .persistenceUnit("default")
            .build();
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(entityManagerFactory.getObject()));
    }

    @Bean(name = "controlPlaneEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean controlPlaneEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource) {
        return builder
            .dataSource(controlPlaneDataSource)
            .packages(Category.class, Tenant.class, DbServer.class, CategoryFileMetadata.class)
            .persistenceUnit("controlPlane")
            .build();
    }

    @Bean(name = "controlPlaneTransactionManager")
    public PlatformTransactionManager controlPlaneTransactionManager(
            @Qualifier("controlPlaneEntityManagerFactory") LocalContainerEntityManagerFactoryBean controlPlaneEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(controlPlaneEntityManagerFactory.getObject()));
    }

    // BL-118 (tiếp, 20/08/2026): "danh mục dùng chung" (AppUser/Organization/RoleGroup/AppRole/
    // AppPermission... — TOÀN BỘ com.td.domain.sharedcore) — LUÔN trỏ tenant mặc định, KHÔNG qua
    // TenantContext/routing (xem javadoc lớp).
    @Bean(name = "identityEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean identityEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("identityDataSource") DataSource identityDataSource) {
        return builder
            .dataSource(identityDataSource)
            .packages(scanEntitiesUnderPackage("com.td.domain.sharedcore"))
            .persistenceUnit("identity")
            .build();
    }

    @Bean(name = "identityTransactionManager")
    public PlatformTransactionManager identityTransactionManager(
            @Qualifier("identityEntityManagerFactory") LocalContainerEntityManagerFactoryBean identityEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(identityEntityManagerFactory.getObject()));
    }

    // Quét toàn bộ @Entity dưới com.td.domain (tương đương @EntityScan("com.td.domain") cũ) rồi loại
    // trừ đúng Category/Tenant/DbServer/CategoryFileMetadata (control-plane) VÀ
    // TOÀN BỘ package com.td.domain.sharedcore (identity — xem javadoc lớp) — đã chuyển hẳn sang 2
    // EntityManagerFactory riêng. Chủ động dùng scan thay vì liệt kê tay từng sub-package còn lại
    // (.documentregister, .numbering, .privatecatalog, .recordsstorage, .documents...) để KHÔNG bị
    // quên khi có package domain mới trong tương lai.
    private static Class<?>[] scanPrimaryDomainEntities() {
        Set<String> excluded = Set.of(Category.class.getName(), Tenant.class.getName(), DbServer.class.getName(),
            CategoryFileMetadata.class.getName());
        String excludedPackagePrefix = "com.td.domain.sharedcore.";

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents("com.td.domain")) {
            String className = beanDefinition.getBeanClassName();
            if (className == null || excluded.contains(className) || className.startsWith(excludedPackagePrefix)) {
                continue;
            }
            classes.add(ClassUtils.resolveClassName(className, MultiPersistenceUnitJpaConfig.class.getClassLoader()));
        }
        // com.td.infrastructure.persistence.entity — package @Entity RIÊNG (vd FileMetadataEntity),
        // nằm ngoài com.td.domain nên phải quét thêm (giữ đúng phạm vi @EntityScan cũ).
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents("com.td.infrastructure.persistence.entity")) {
            String className = beanDefinition.getBeanClassName();
            if (className != null) {
                classes.add(ClassUtils.resolveClassName(className, MultiPersistenceUnitJpaConfig.class.getClassLoader()));
            }
        }

        if (classes.isEmpty()) {
            throw new IllegalStateException(
                "Khong tim thay entity nao duoi com.td.domain (tru control-plane/identity) - kiem tra lai classpath scan");
        }
        return classes.toArray(new Class<?>[0]);
    }

    /** Quét toàn bộ {@code @Entity} nằm TRỰC TIẾP trong 1 package (không đệ quy con) — dùng cho
     * {@link #identityEntityManagerFactory}, khác {@link #scanPrimaryDomainEntities()} (quét toàn bộ
     * {@code com.td.domain} rồi loại trừ) vì ở đây ta muốn CHÍNH XÁC 1 package, không hơn không kém. */
    private static Class<?>[] scanEntitiesUnderPackage(String basePackage) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(basePackage)) {
            String className = beanDefinition.getBeanClassName();
            if (className != null) {
                classes.add(ClassUtils.resolveClassName(className, MultiPersistenceUnitJpaConfig.class.getClassLoader()));
            }
        }
        if (classes.isEmpty()) {
            throw new IllegalStateException("Khong tim thay entity nao duoi " + basePackage + " - kiem tra lai classpath scan");
        }
        return classes.toArray(new Class<?>[0]);
    }
}
