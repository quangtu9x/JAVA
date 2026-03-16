package com.td.domain.common.contracts;

import java.util.UUID;

/**
 * Default AuditableEntity with UUID as the ID type
 */
public abstract class DefaultAuditableEntity extends AuditableEntity<UUID> {
    
    protected DefaultAuditableEntity() {
        super();
    }
    
    protected DefaultAuditableEntity(UUID id) {
        super(id);
    }
}