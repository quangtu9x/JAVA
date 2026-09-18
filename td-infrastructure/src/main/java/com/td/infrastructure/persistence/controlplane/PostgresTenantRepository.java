package com.td.infrastructure.persistence.controlplane;

import com.td.application.tenant.TenantRepository;
import com.td.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresTenantRepository implements TenantRepository {

    private final TenantJpaRepository jpa;

    @Override
    public Optional<Tenant> findById(UUID id) {
        return ((JpaRepository<Tenant, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<Tenant> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public Optional<Tenant> findByTenantIdAndDeletedOnIsNull(String tenantId) {
        return jpa.findByTenantIdAndDeletedOnIsNull(tenantId);
    }

    @Override
    public boolean existsByTenantIdAndDeletedOnIsNull(String tenantId) {
        return jpa.existsByTenantIdAndDeletedOnIsNull(tenantId);
    }

    @Override
    public boolean existsByDatabaseNameAndDeletedOnIsNull(String databaseName) {
        return jpa.existsByDatabaseNameAndDeletedOnIsNull(databaseName);
    }

    @Override
    public List<Tenant> findAllByDeletedOnIsNull() {
        return jpa.findAllByDeletedOnIsNull();
    }

    @Override
    public <S extends Tenant> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(Tenant entity) {
        jpa.delete(entity);
    }
}
