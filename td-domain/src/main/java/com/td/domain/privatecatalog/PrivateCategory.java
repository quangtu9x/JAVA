package com.td.domain.privatecatalog;

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

// BL-116: "Danh mục riêng" (dungrieng/*) — cấu trúc giống hệt Category (BL-101x) nhưng KHÔNG sống ở
// schema `shared` (global) — bảng private_categories nằm trong schema RIÊNG của từng tenant, tự
// động tách biệt theo tenant nhờ cơ chế schema-per-tenant gốc (BL-303), không cần cột/logic tenant
// nào ở đây. Xem docs/architecture/05-shared-schema-multi-tenant.md.
//
// `identifier`: hiện chỉ có ĐÚNG 1 tenant thật (cả tỉnh, mọi phòng/ban/đơn vị con dùng chung 1
// schema) — schema-per-tenant KHÔNG tách được dữ liệu GIỮA CÁC ĐƠN VỊ CON trong cùng tenant (vd:
// mỗi phòng/ban có "ký hiệu văn bản" riêng, như Domino cũ dùng identifier + view "..._by_identifier"
// để tách). Field này giữ nguyên vai trò đó — chuỗi tự do, không phải FK (đăng nhập vẫn do Domino
// `qlvb/authen`/`qlvb/profile` cấp identifier, chưa migrate sang Java).
@Entity
@Table(name = "private_categories")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivateCategory extends AuditableEntity<UUID> implements IAggregateRoot {

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

    @Column(name = "identifier", length = 200)
    private String identifier;

    // Field động (JSONB) tự định nghĩa từ FE — cùng cơ chế Dynamic Data Engine (BL-101) đã dùng cho
    // Category/Organization, cho danh mục field kiểu Domino cũ chưa có cột tường minh mà không cần
    // thêm migration mỗi lần.
    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    public PrivateCategory(String code, String name, String description, String form,
                           UUID parentId, int level, String fullPath, int sortOrder, String identifier) {
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
        this.identifier = identifier;
    }

    public PrivateCategory update(String code, String name, String description, String form,
                                  UUID parentId, int level, String fullPath,
                                  Integer sortOrder, Boolean isActive, String identifier) {
        if (code != null)        this.code = code;
        if (name != null)        this.name = name;
        if (description != null) this.description = description;
        if (form != null)        this.form = form;
        this.parentId = parentId;
        this.level    = level;
        this.fullPath = fullPath;
        if (sortOrder != null)   this.sortOrder = sortOrder;
        if (isActive  != null)   this.isActive  = isActive;
        if (identifier != null)  this.identifier = identifier;
        return this;
    }

    // Thay toàn bộ field động (JSONB) — gọi với kết quả đã merge (xem UpdatePrivateCategoryUseCase),
    // cùng convention Category/Organization.replaceDynamicFields.
    public PrivateCategory replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank())
            ? "{}"
            : dynamicFieldsJson;
        return this;
    }
}
