package com.td.domain.common.events;

import com.td.domain.common.contracts.DomainEvent;
import com.td.domain.common.contracts.Entity;
import lombok.Getter;

/**
 * Domain event raised when an entity is deleted.
 */
@Getter
public class EntityDeletedEvent<TEntity extends Entity<?>> extends DomainEvent {
    
    private final TEntity entity;
    
    public EntityDeletedEvent(TEntity entity) {
        super();
        this.entity = entity;
    }
    
    /**
     * Factory method to create an EntityDeletedEvent
     */
    public static <T extends Entity<?>> EntityDeletedEvent<T> withEntity(T entity) {
        return new EntityDeletedEvent<>(entity);
    }
}