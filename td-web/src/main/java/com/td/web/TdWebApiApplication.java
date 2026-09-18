package com.td.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// BL-117: @EntityScan/@EnableJpaRepositories mặc định (trước đây khai báo ngay tại đây) đã chuyển
// sang com.td.infrastructure.tenant.controlplane (PrimaryJpaRepositoriesConfig +
// ControlPlaneJpaRepositoriesConfig + MultiPersistenceUnitJpaConfig) — bắt buộc phải khai báo THỦ
// CÔNG cả 2 EntityManagerFactory (nghiệp vụ theo tenant + control-plane riêng cho Category/Tenant)
// ngay khi có từ 2 EntityManagerFactory trở lên: Spring Boot tự tắt HẲN EntityManagerFactory/
// EnableJpaRepositories mặc định của nó (@ConditionalOnMissingBean(LocalContainerEntityManagerFactoryBean.class)
// - xác nhận qua đọc bytecode JpaBaseConfiguration) ngay khi phát hiện BẤT KỲ bean nào cùng kiểu đó
// đã tồn tại - để @EntityScan ở đây (chỉ ảnh hưởng bean mặc định của Boot) sẽ vô nghĩa/gây hiểu nhầm.
// Xem docs/architecture/06-database-per-tenant-multi-tenant.md.
@SpringBootApplication(scanBasePackages = "com.td")
public class TdWebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TdWebApiApplication.class, args);
    }
}
