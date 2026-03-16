package com.td.domain.common.contracts;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Base Entity có Audit Tracking - Entity cơ sở tự động tracking thông tin audit
 * 
 * Kế thừa từ AbstractEntity và implement các interface:
 * - IAuditableEntity: Tracking người tạo, người sửa, thời gian tạo/sửa
 * - ISoftDelete: Hỗ trợ soft delete (xóa mềm) thay vì xóa vật lý
 * 
 * Tương đương với TD.WebApi.Domain.Common.Contracts.AuditableEntity<T> từ .NET
 * 
 * Các field được tracking tự động:
 * - createdBy, createdOn: Người tạo và thời gian tạo
 * - lastModifiedBy, lastModifiedOn: Người sửa cuối và thời gian sửa cuối
 * - deletedBy, deletedOn: Người xóa và thời gian xóa (soft delete)
 * 
 * JPA Callbacks tự động:
 * - @PrePersist: Tự động set createdOn, lastModifiedOn khi insert
 * - @PreUpdate: Tự động cập nhật lastModifiedOn khi update
 * 
 * Sử dụng: Kế thừa class này cho các entity cần audit tracking
 * Example: public class Product extends AuditableEntity<UUID>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity<T> extends AbstractEntity<T> implements IAuditableEntity, ISoftDelete {

    // ID người tạo (User UUID)
    @Column(name = "created_by")
    private UUID createdBy;

    // Thời gian tạo (không thể cập nhật sau khi tạo)
    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDateTime createdOn;

    // ID người sửa cuối cùng (User UUID)
    @Column(name = "last_modified_by")
    private UUID lastModifiedBy;

    // Thời gian sửa cuối cùng
    @Column(name = "last_modified_on")
    private LocalDateTime lastModifiedOn;

    // Thời gian xóa (null = chưa xóa, có giá trị = đã soft delete)
    @Column(name = "deleted_on")
    private LocalDateTime deletedOn;

    // ID người xóa (User UUID)
    @Column(name = "deleted_by")
    private UUID deletedBy;

    /**
     * Constructor mặc định - Tự động set thời gian tạo/sửa = hiện tại
     */
    protected AuditableEntity() {
        super();
        this.createdOn = LocalDateTime.now();
        this.lastModifiedOn = LocalDateTime.now();
    }

    /**
     * Constructor với ID - Tự động set thời gian tạo/sửa = hiện tại
     * 
     * @param id ID của entity
     */
    protected AuditableEntity(T id) {
        super(id);
        this.createdOn = LocalDateTime.now();
        this.lastModifiedOn = LocalDateTime.now();
    }

    /**
     * Kiểm tra entity đã bị soft delete chưa
     * 
     * @return true nếu deletedOn != null (đã xóa), ngược lại false
     */
    @Override
    public boolean isDeleted() {
        return deletedOn != null;
    }

    /**
     * Đánh dấu entity là đã xóa (soft delete)
     * Set deletedOn, deletedBy, và cập nhật lastModified
     * 
     * @param deletedBy ID người xóa (User UUID)
     */
    @Override
    public void markAsDeleted(UUID deletedBy) {
        this.deletedOn = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.lastModifiedBy = deletedBy;
        this.lastModifiedOn = LocalDateTime.now();
    }

    /**
     * JPA Callback - Tự động gọi trước khi insert vào database
     * Đảm bảo createdOn và lastModifiedOn luôn có giá trị
     */
    @PrePersist
    protected void onCreate() {
        if (createdOn == null) {
            createdOn = LocalDateTime.now();
        }
        if (lastModifiedOn == null) {
            lastModifiedOn = LocalDateTime.now();
        }
    }

    /**
     * JPA Callback - Tự động gọi trước khi update vào database
     * Tự động cập nhật lastModifiedOn về thời gian hiện tại
     */
    @PreUpdate
    protected void onUpdate() {
        lastModifiedOn = LocalDateTime.now();
    }
}