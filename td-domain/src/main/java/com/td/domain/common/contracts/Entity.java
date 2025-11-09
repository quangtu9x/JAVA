package com.td.domain.common.contracts;

/**
 * Base interface for all domain entities.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.IEntity<T> from .NET
 */
public interface Entity<T> {
    
    /**
     * Gets the unique identifier of the entity
     */
    T getId();
}