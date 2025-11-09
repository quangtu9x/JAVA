package com.td.domain.common.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Interface for entities that support soft deletion.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.ISoftDelete from .NET
 */
public interface ISoftDelete {
    
    LocalDateTime getDeletedOn();
    void setDeletedOn(LocalDateTime deletedOn);
    
    UUID getDeletedBy();
    void setDeletedBy(UUID deletedBy);
    
    /**
     * Checks if the entity is soft deleted
     */
    boolean isDeleted();
    
    /**
     * Marks the entity as soft deleted
     */
    void markAsDeleted(UUID deletedBy);
}