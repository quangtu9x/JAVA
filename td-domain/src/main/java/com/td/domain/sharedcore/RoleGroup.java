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

// "Nhóm vai trò" (Domino form group_roles) — đơn vị THỰC SỰ được gán cho 1 người dùng
// (AppUser.roleGroupId), khác với "Vai trò" (AppRole) chỉ là 1 thành phần con của nhóm. Chứa 2
// danh sách độc lập (xem RoleGroupRole/RoleGroupPermission): danh sách "vai trò" con và danh sách
// "quyền" cấp trực tiếp — đúng 2 cơ chế song song của Domino gốc (list_role_obj và roles).
@Entity
@Table(name = "role_groups")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleGroup extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    // Domino field "limit": "1"=Toàn bộ (mặc định) / "0"=Quản trị tổng.
    @Column(name = "full_access", nullable = false)
    private boolean fullAccess = true;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public RoleGroup(String name, int sortOrder, boolean fullAccess, boolean isActive) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.sortOrder = sortOrder;
        this.fullAccess = fullAccess;
        this.isActive = isActive;
    }

    public RoleGroup update(String name, Integer sortOrder, Boolean fullAccess, Boolean isActive) {
        if (name != null) {
            this.name = name;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (fullAccess != null) {
            this.fullAccess = fullAccess;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
