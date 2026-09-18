package com.td.infrastructure.tenant.controlplane;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// BL-117: repository của 2 entity sống ở database control-plane (Category, Tenant) — dùng
// EntityManagerFactory/TransactionManager RIÊNG (khai báo ở MultiPersistenceUnitJpaConfig).
//
// LƯU Ý QUAN TRỌNG (sự cố thật đã gặp): @EnableJpaRepositories(basePackageClasses=...) quét NGUYÊN
// CẢ PACKAGE chứa các class chỉ định, KHÔNG PHẢI chỉ đúng những class đó — vì vậy 2 repository này
// PHẢI nằm ở 1 package RIÊNG BIỆT hoàn toàn (com.td.infrastructure.persistence.controlplane), tách
// khỏi com.td.infrastructure.persistence.repository (nơi 33+ repository nghiệp vụ khác đang ở) —
// nếu để chung package, config này sẽ quét trùng toàn bộ repository nghiệp vụ, đăng ký bean 2 lần
// (1 lần ở đây, 1 lần ở PrimaryJpaRepositoriesConfig) và Spring ném lỗi
// "bean ... could not be registered ... A bean with that name has already been defined".
@Configuration
@EnableJpaRepositories(
    basePackages = "com.td.infrastructure.persistence.controlplane",
    entityManagerFactoryRef = "controlPlaneEntityManagerFactory",
    transactionManagerRef = "controlPlaneTransactionManager"
)
public class ControlPlaneJpaRepositoriesConfig {
}
