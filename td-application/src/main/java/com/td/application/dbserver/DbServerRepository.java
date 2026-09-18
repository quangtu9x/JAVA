package com.td.application.dbserver;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.dbserver.DbServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DbServerRepository extends IRepository<DbServer> {

    Optional<DbServer> findByIdAndDeletedOnIsNull(UUID id);

    boolean existsByHostAndPortAndDeletedOnIsNull(String host, int port);

    List<DbServer> findAllByDeletedOnIsNullOrderByNameAsc();
}
