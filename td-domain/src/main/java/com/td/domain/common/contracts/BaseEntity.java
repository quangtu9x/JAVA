package com.td.domain.common.contracts;

import java.util.UUID;

/**
 * Base entity with UUID identifier.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.BaseEntity from .NET
 */
@MappedSuperclass
public abstract class BaseEntity extends AbstractEntity<UUID> {

    protected BaseEntity() {
        super(UUID.randomUUID());
    }

    protected BaseEntity(UUID id) {
        super(id);
    }
}