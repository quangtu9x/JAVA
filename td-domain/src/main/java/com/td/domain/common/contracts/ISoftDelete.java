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

    /**
     * 11/09/2026 (yêu cầu người dùng "port vbdi/thungrac/thungrac" — nút "Khôi phục"): đảo ngược
     * {@link #markAsDeleted}, đưa entity về trạng thái CHƯA xoá ({@code deletedOn}/{@code deletedBy}
     * = null). Cùng LUẬT "delete luôn soft-delete" (xem MEMORY.md) — mọi module port chức năng
     * "Khôi phục"/"Restore" từ Domino sang Java nên tái dùng method này thay vì tự set field thô.
     */
    void restore();
}