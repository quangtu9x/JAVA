package com.td.domain.common.events;

import com.td.domain.common.contracts.DomainEvent;
import com.td.domain.common.contracts.Entity;
import lombok.Getter;

/**
 * Domain event raised when an entity is created.
 * Equivalent to TD.WebApi.Domain.Common.Events.EntityCreatedEvent<T> from .NET
 */
@Getter
public class EntityCreatedEvent<TEntity extends Entity<?>> extends DomainEvent {
    
    private final TEntity entity;
    
    public EntityCreatedEvent(TEntity entity) {
        super();
        this.entity = entity;
    }
    
    /**
     * Factory method to create an EntityCreatedEvent
     */
    public static <T extends Entity<?>> EntityCreatedEvent<T> withEntity(T entity) {
        return new EntityCreatedEvent<>(entity);
    }
}