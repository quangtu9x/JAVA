package com.td.domain.common.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Interface for entities that support auditing information.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.IAuditableEntity from .NET
 */
public interface IAuditableEntity {
    
    UUID getCreatedBy();
    void setCreatedBy(UUID createdBy);
    
    LocalDateTime getCreatedOn();
    void setCreatedOn(LocalDateTime createdOn);
    
    UUID getLastModifiedBy();
    void setLastModifiedBy(UUID lastModifiedBy);
    
    LocalDateTime getLastModifiedOn();
    void setLastModifiedOn(LocalDateTime lastModifiedOn);
}