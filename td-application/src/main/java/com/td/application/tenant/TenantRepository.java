package com.td.application.tenant;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.tenant.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends IRepository<Tenant> {

    Optional<Tenant> findByIdAndDeletedOnIsNull(UUID id);

    Optional<Tenant> findByTenantIdAndDeletedOnIsNull(String tenantId);

    boolean existsByTenantIdAndDeletedOnIsNull(String tenantId);

    boolean existsByDatabaseNameAndDeletedOnIsNull(String databaseName);

    List<Tenant> findAllByDeletedOnIsNull();
}
