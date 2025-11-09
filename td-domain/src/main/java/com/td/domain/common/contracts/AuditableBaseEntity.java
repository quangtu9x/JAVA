package com.td.domain.common.contracts;

import java.util.UUID;

/**
 * Base auditable entity with UUID identifier.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.AuditableEntity from .NET
 */
@MappedSuperclass
public abstract class AuditableBaseEntity extends AuditableEntity<UUID> {

    protected AuditableBaseEntity() {
        super(UUID.randomUUID());
    }

    protected AuditableBaseEntity(UUID id) {
        super(id);
    }
}