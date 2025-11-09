package com.td.domain.common.events;

import com.td.domain.common.contracts.DomainEvent;
import com.td.domain.common.contracts.Entity;
import lombok.Getter;

/**
 * Domain event raised when an entity is updated.
 */
@Getter
public class EntityUpdatedEvent<TEntity extends Entity<?>> extends DomainEvent {
    
    private final TEntity entity;
    
    public EntityUpdatedEvent(TEntity entity) {
        super();
        this.entity = entity;
    }
    
    /**
     * Factory method to create an EntityUpdatedEvent
     */
    public static <T extends Entity<?>> EntityUpdatedEvent<T> withEntity(T entity) {
        return new EntityUpdatedEvent<>(entity);
    }
}