package com.td.domain.categories;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// BL-117 (thay thế hẳn cơ chế BL-115 cũ): trước đây bảng này sống trong schema `shared` CÙNG 1
// database vật lý với mọi tenant (search_path 2 tầng + `@Table(schema="shared")` để né lỗi Hibernate
// schema-validation không theo search_path — xem lịch sử ở `docs/architecture/05-shared-schema-
// multi-tenant.md`, ĐÃ RETIRED). Từ BL-117, mô hình multi-tenant đổi hẳn sang database-per-tenant
// thật (mỗi tenant 1 database Postgres vật lý riêng biệt, không chỉ khác schema) — bảng `categories`
// vì vậy được tách sang 1 database "control-plane" RIÊNG BIỆT HOÀN TOÀN (không tenant nào có thể
// đụng tới), có `EntityManagerFactory`/`DataSource` riêng (xem `ControlPlaneDataSourceConfig`) — bản
// thân database đó đã đủ cô lập, không cần khai báo `schema=` tường minh nữa (không còn search_path
// nhiều schema để phải né). Xem `docs/architecture/06-database-per-tenant-multi-tenant.md`.
@Entity
@Table(name = "categories")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String form;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "level", nullable = false)
    private int level = 0;

    @Column(name = "full_path", nullable = false, columnDefinition = "TEXT")
    private String fullPath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // Field động (JSONB) tự định nghĩa từ FE — cùng cơ chế Dynamic Data Engine (BL-101) đã nối cho
    // Organization (BL-109), cho danh mục field kiểu Domino cũ chưa có cột tường minh mà không cần
    // thêm migration mỗi lần.
    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    public Category(String code, String name, String description, String form,
                    UUID parentId, int level, String fullPath, int sortOrder) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.description = description;
        this.form = form;
        this.parentId = parentId;
        this.level = level;
        this.fullPath = fullPath;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    public Category update(String code, String name, String description, String form,
                           UUID parentId, int level, String fullPath,
                           Integer sortOrder, Boolean isActive) {
        if (code != null)        this.code = code;
        if (name != null)        this.name = name;
        if (description != null) this.description = description;
        if (form != null)        this.form = form;
        this.parentId = parentId;
        this.level    = level;
        this.fullPath = fullPath;
        if (sortOrder != null)   this.sortOrder = sortOrder;
        if (isActive  != null)   this.isActive  = isActive;
        return this;
    }

    // Thay toàn bộ field động (JSONB) — gọi với kết quả đã merge (xem UpdateCategoryUseCase), cùng
    // convention với Organization.replaceDynamicFields (BL-109).
    public Category replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank())
            ? "{}"
            : dynamicFieldsJson;
        return this;
    }
}
