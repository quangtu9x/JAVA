package com.td.domain.common.contracts;

import java.util.UUID;

/**
 * Default AuditableEntity with UUID as the ID type
 */
public abstract class AuditableEntity extends AuditableEntity<UUID> {
    
    protected AuditableEntity() {
        super();
    }
    
    protected AuditableEntity(UUID id) {
        super(id);
    }
}