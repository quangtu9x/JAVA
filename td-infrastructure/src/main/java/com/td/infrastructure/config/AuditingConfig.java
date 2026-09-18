package com.td.infrastructure.config;

import com.td.domain.common.contracts.AuditCurrentUserHolder;
import com.td.infrastructure.security.CurrentUserContextResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@RequiredArgsConstructor
public class AuditingConfig {

    private final CurrentUserContextResolver currentUserContextResolver;

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return currentUserContextResolver::resolveCurrentUserUuid;
    }

    /**
     * Bugfix 26/08/2026 — lần 2 (người dùng build lại vẫn thấy created_by/last_modified_by NULL):
     * đăng ký nguồn "người dùng hiện tại" cho {@code AuditableEntityListener} thuần JPA bên domain
     * (xem javadoc {@link AuditCurrentUserHolder} — cơ chế Spring Data auditing chuẩn vô hiệu vì 3
     * persistence unit dựng tay không có SpringBeanContainer). Resolver hoạt động cho CẢ luồng
     * Keycloak JWT lẫn Tdtoken; ngoài request HTTP (background/cron) trả rỗng → field bỏ trống,
     * không suy diễn.
     */
    @PostConstruct
    void registerAuditCurrentUserSupplier() {
        AuditCurrentUserHolder.register(currentUserContextResolver::resolveCurrentUserUuid);
    }
}