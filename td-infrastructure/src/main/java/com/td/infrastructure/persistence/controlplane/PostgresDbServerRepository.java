package com.td.infrastructure.persistence.controlplane;

import com.td.application.dbserver.DbServerRepository;
import com.td.domain.dbserver.DbServer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresDbServerRepository implements DbServerRepository {

    private final DbServerJpaRepository jpa;

    @Override
    public Optional<DbServer> findById(UUID id) {
        return ((JpaRepository<DbServer, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<DbServer> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public boolean existsByHostAndPortAndDeletedOnIsNull(String host, int port) {
        return jpa.existsByHostAndPortAndDeletedOnIsNull(host, port);
    }

    @Override
    public List<DbServer> findAllByDeletedOnIsNullOrderByNameAsc() {
        return jpa.findAllByDeletedOnIsNullOrderByNameAsc();
    }

    @Override
    public <S extends DbServer> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(DbServer entity) {
        jpa.delete(entity);
    }
}
