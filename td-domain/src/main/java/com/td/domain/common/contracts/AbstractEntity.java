package com.td.domain.common.contracts;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.AccessLevel;

/**
 * Base entity class that provides common functionality for all domain entities.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.BaseEntity<T> from .NET
 */
@MappedSuperclass
@Getter
public abstract class AbstractEntity<T> implements Entity<T> {

    @Id
    @Column(name = "id")
    protected T id;

    @Transient
    @Getter(AccessLevel.PACKAGE)
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AbstractEntity() {
        // Will be overridden in concrete classes for specific ID generation
    }

    protected AbstractEntity(T id) {
        this.id = id;
    }

    /**
     * Adds a domain event to be published after the entity is saved
     */
    public void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * Removes a specific domain event
     */
    public void removeDomainEvent(DomainEvent event) {
        domainEvents.remove(event);
    }

    /**
     * Gets all domain events (immutable view)
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all domain events - typically called after publishing
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        AbstractEntity<?> that = (AbstractEntity<?>) obj;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return String.format("%s{id=%s}", getClass().getSimpleName(), id);
    }
}