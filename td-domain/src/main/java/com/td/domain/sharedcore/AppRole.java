package com.td.domain.sharedcore;

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

@Entity
@Table(name = "app_roles")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppRole extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system_role", nullable = false)
    private boolean isSystemRole = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    // Field động (JSONB) tự định nghĩa — "Vai trò" Domino gốc có thêm field cấu hình menu
    // (menu_top, menu_vbden, menu_duthao, menu_hosocv, menu_phieutrinh, menu_vbdi, menu_vbcddh,
    // menu_lichct, menu_chiasetep, menu_qlvb, menu_tlch, menu_hosoluutru, access_level — mỗi field
    // là 1 chuỗi nối ";" các mã menu/module được phép) chưa có cột tường minh nào — tái dùng cơ chế
    // Dynamic Data Engine (BL-101/BL-109/BL-110) làm nơi lưu nguyên trạng, FE tự quản lý cấu trúc
    // bên trong (ConfigMenu.jsx), backend chỉ lưu/trả nguyên văn.
    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    public AppRole(String code, String name, String description, boolean isSystemRole, boolean isActive,
                   int sortOrder) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.description = description;
        this.isSystemRole = isSystemRole;
        this.isActive = isActive;
        this.sortOrder = sortOrder;
    }

    public AppRole update(String name, String description, Boolean isSystemRole, Boolean isActive,
                          Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (isSystemRole != null) {
            this.isSystemRole = isSystemRole;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        return this;
    }

    // Thay toàn bộ field động (JSONB) — gọi với kết quả đã merge (xem UpdateAppRoleUseCase), cùng
    // convention với Organization/AppUser/Category.replaceDynamicFields.
    public AppRole replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank())
            ? "{}"
            : dynamicFieldsJson;
        return this;
    }
}
