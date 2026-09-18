package com.td.infrastructure.tenant.controlplane;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// BL-117: thay cho @EnableJpaRepositories("com.td.infrastructure.persistence.repository") trước đây
// khai báo trực tiếp trên TdWebApiApplication — chuyển vào đây để chỉ định rõ
// entityManagerFactoryRef/transactionManagerRef (bean "entityManagerFactory"/"transactionManager"
// khai báo thủ công ở MultiPersistenceUnitJpaConfig). Không cần excludeFilters gì thêm —
// CategoryJpaRepository/TenantJpaRepository (2 repository control-plane, xem
// ControlPlaneJpaRepositoriesConfig) đã ở HẲN 1 package riêng
// (com.td.infrastructure.persistence.controlplane), nằm ngoài phạm vi quét của basePackages dưới
// đây, nên không có nguy cơ trùng lặp.
@Configuration
@EnableJpaRepositories(
    basePackages = "com.td.infrastructure.persistence.repository",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
public class PrimaryJpaRepositoriesConfig {
}
