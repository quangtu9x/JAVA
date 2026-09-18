package com.td.infrastructure.tenant.controlplane;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// BL-118 (tiếp, 20/08/2026): repository của "danh mục dùng chung" (AppUser/Organization/RoleGroup/
// AppRole/AppPermission... - toàn bộ com.td.domain.sharedcore) - dùng EntityManagerFactory/
// TransactionManager RIÊNG (khai báo ở MultiPersistenceUnitJpaConfig), LUÔN trỏ database tenant mặc
// định - KHÔNG qua TenantContext/routing (xem javadoc MultiPersistenceUnitJpaConfig).
//
// LƯU Ý QUAN TRỌNG (giống ControlPlaneJpaRepositoriesConfig): @EnableJpaRepositories(basePackages=...)
// quét NGUYÊN CẢ PACKAGE - vì vậy các repository này PHẢI nằm ở 1 package RIÊNG BIỆT hoàn toàn
// (com.td.infrastructure.persistence.identity), tách khỏi com.td.infrastructure.persistence.repository
// (nơi các repository nghiệp vụ/văn bản còn lại đang ở) - nếu để chung package, config này sẽ quét
// trùng repository nghiệp vụ, đăng ký bean 2 lần và Spring ném lỗi "bean ... could not be registered".
@Configuration
@EnableJpaRepositories(
    basePackages = "com.td.infrastructure.persistence.identity",
    entityManagerFactoryRef = "identityEntityManagerFactory",
    transactionManagerRef = "identityTransactionManager"
)
public class IdentityJpaRepositoriesConfig {
}
