package com.td.domain.common.contracts;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * Base class for all domain events.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.DomainEvent from .NET
 */
@Getter
public abstract class DomainEvent {
    
    private final LocalDateTime occurredOn;
    
    protected DomainEvent() {
        this.occurredOn = LocalDateTime.now();
    }
    
    protected DomainEvent(LocalDateTime occurredOn) {
        this.occurredOn = occurredOn;
    }
}