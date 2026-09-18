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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "keycloak_subject", nullable = false, length = 200)
    private String keycloakSubject;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(length = 200)
    private String email;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // BL-108: field hồ sơ người dùng bổ sung (xem docs/field-mapping/03-user-field-mapping.md).
    // Cố tình KHÔNG có agency/unit/department/parentids... như Domino gốc — suy ra được từ
    // organizationId bằng cách đi lên Organization.parentId, tránh lưu trùng dữ liệu tổ chức.
    @Column
    private Integer level;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(length = 20)
    private String gender;

    @Column(name = "birthday_date")
    private LocalDate birthdayDate;

    @Column(length = 500)
    private String address;

    @Column(name = "user_id_no", length = 30)
    private String userIdNo;

    @Column(name = "cap_lanh_dao", length = 30)
    private String capLanhDao;

    @Column(length = 100)
    private String identifier;

    @Column(name = "is_sim", nullable = false)
    private boolean isSim = false;

    // "Kiêm nhiệm": trỏ về document user chính nếu bản ghi này là 1 chức vụ kiêm nhiệm. Chỉ có cột,
    // UseCase quản lý kiêm nhiệm để lại cho đợt sau (xem plan BL-108).
    @Column(name = "parent_user_id")
    private UUID parentUserId;

    // Field động (JSONB) tự định nghĩa từ FE — cùng cơ chế Dynamic Data Engine (BL-101) đã nối cho
    // Organization (BL-109), cho hồ sơ người dùng field kiểu Domino cũ chưa có cột tường minh mà
    // không cần thêm migration mỗi lần.
    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    public AppUser(String keycloakSubject, String username, String fullName, String email,
                   UUID organizationId, UUID positionId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.keycloakSubject = keycloakSubject;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.organizationId = organizationId;
        this.positionId = positionId;
        this.isActive = isActive;
    }

    // Overload đầy đủ field cho CreateAppUserUseCase (BL-108) — giữ nguyên constructor 7-tham số ở
    // trên vì đang được unit test khác dùng trực tiếp.
    public AppUser(String keycloakSubject, String username, String fullName, String email,
                   UUID organizationId, UUID positionId, boolean isActive,
                   Integer level, int sortOrder, String phoneNumber, String gender,
                   LocalDate birthdayDate, String address, String userIdNo, String capLanhDao,
                   String identifier, boolean isSim, UUID parentUserId) {
        this(keycloakSubject, username, fullName, email, organizationId, positionId, isActive);
        this.level = level;
        this.sortOrder = sortOrder;
        this.phoneNumber = phoneNumber;
        this.gender = gender;
        this.birthdayDate = birthdayDate;
        this.address = address;
        this.userIdNo = userIdNo;
        this.capLanhDao = capLanhDao;
        this.identifier = identifier;
        this.isSim = isSim;
        this.parentUserId = parentUserId;
    }

    public AppUser update(String username, String fullName, String email,
                          UUID organizationId, UUID positionId, Boolean isActive) {
        if (username != null) {
            this.username = username;
        }
        if (fullName != null) {
            this.fullName = fullName;
        }
        if (email != null) {
            this.email = email;
        }
        if (organizationId != null) {
            this.organizationId = organizationId;
        }
        if (positionId != null) {
            this.positionId = positionId;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }

    // Overload cập nhật thêm field hồ sơ (BL-108) — null-safe merge, cùng convention với update()
    // gốc ở trên. Gọi update() gốc trước để dùng chung logic 6 field cũ.
    public AppUser updateProfile(String username, String fullName, String email,
                                  UUID organizationId, UUID positionId, Boolean isActive,
                                  Integer level, Integer sortOrder, String phoneNumber, String gender,
                                  LocalDate birthdayDate, String address, String userIdNo,
                                  String capLanhDao, String identifier, Boolean isSim) {
        update(username, fullName, email, organizationId, positionId, isActive);
        if (level != null) {
            this.level = level;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (birthdayDate != null) {
            this.birthdayDate = birthdayDate;
        }
        if (address != null) {
            this.address = address;
        }
        if (userIdNo != null) {
            this.userIdNo = userIdNo;
        }
        if (capLanhDao != null) {
            this.capLanhDao = capLanhDao;
        }
        if (identifier != null) {
            this.identifier = identifier;
        }
        if (isSim != null) {
            this.isSim = isSim;
        }
        return this;
    }

    // Thay toàn bộ field động (JSONB) — gọi với kết quả đã merge (xem UpdateAppUserUseCase), cùng
    // convention với Organization.replaceDynamicFields (BL-109).
    public AppUser replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank())
            ? "{}"
            : dynamicFieldsJson;
        return this;
    }
}
