package com.td.domain.common.contracts;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Base auditable entity that tracks creation and modification information.
 * Equivalent to TD.WebApi.Domain.Common.Contracts.AuditableEntity<T> from .NET
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity<T> extends AbstractEntity<T> implements IAuditableEntity, ISoftDelete {

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "last_modified_by")
    private UUID lastModifiedBy;

    @Column(name = "last_modified_on")
    private LocalDateTime lastModifiedOn;

    @Column(name = "deleted_on")
    private LocalDateTime deletedOn;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected AuditableEntity() {
        super();
        this.createdOn = LocalDateTime.now();
        this.lastModifiedOn = LocalDateTime.now();
    }

    protected AuditableEntity(T id) {
        super(id);
        this.createdOn = LocalDateTime.now();
        this.lastModifiedOn = LocalDateTime.now();
    }

    @Override
    public boolean isDeleted() {
        return deletedOn != null;
    }

    @Override
    public void markAsDeleted(UUID deletedBy) {
        this.deletedOn = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.lastModifiedBy = deletedBy;
        this.lastModifiedOn = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdOn == null) {
            createdOn = LocalDateTime.now();
        }
        if (lastModifiedOn == null) {
            lastModifiedOn = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedOn = LocalDateTime.now();
    }
}