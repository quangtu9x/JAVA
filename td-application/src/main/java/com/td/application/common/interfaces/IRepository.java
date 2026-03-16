package com.td.application.common.interfaces;

import java.util.Optional;
import java.util.UUID;

/**
 * Minimal repository contract used by application use cases.
 */
public interface IRepository<T> {

    Optional<T> findById(UUID id);

    <S extends T> S save(S entity);

    void delete(T entity);
}