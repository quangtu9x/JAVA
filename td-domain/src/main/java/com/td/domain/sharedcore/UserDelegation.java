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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 09/10/2026 — 1 lần ủy quyền xử lý thay (menu người dùng → "Ủy quyền"). Bảng riêng
 * {@code user_delegations} (migration V1.0.71), persistence unit "identity" (cùng {@link AppUser}).
 *
 * <p>Thay cho chuỗi {@code delegation_obj} Domino lưu trên bản ghi người dùng rồi chép sang tổ chức/nhóm/
 * sổ: nay chỉ lưu 1 nơi, các API đọc tự gắn ủy quyền đang hiệu lực (xem {@code UserDelegationResolver}).
 * Hiệu lực = {@link #STATUS_ACTIVE}, chưa xoá, {@code startDate <= ngày <= endDate} (tính cả 2 đầu).</p>
 */
@Entity
@Table(name = "user_delegations")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDelegation extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REVOKED = "revoked";

    @Column(name = "delegator_user_id", nullable = false)
    private UUID delegatorUserId;

    @Column(name = "delegator_username", nullable = false, length = 100)
    private String delegatorUsername;

    @Column(name = "delegate_user_id", nullable = false)
    private UUID delegateUserId;

    @Column(name = "delegate_username", nullable = false, length = 100)
    private String delegateUsername;

    @Column(name = "delegate_name", length = 300)
    private String delegateName;

    @Column(name = "delegate_position", length = 300)
    private String delegatePosition;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Phạm vi nối ";" — {@code vbden}, {@code duthao}. */
    @Column(name = "scopes", nullable = false, length = 200)
    private String scopes;

    @Column(name = "subject", columnDefinition = "TEXT")
    private String subject;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "revoked_on")
    private LocalDateTime revokedOn;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    public UserDelegation(UUID delegatorUserId, String delegatorUsername,
                          UUID delegateUserId, String delegateUsername, String delegateName, String delegatePosition,
                          LocalDate startDate, LocalDate endDate, String scopes, String subject) {
        this.id = UUID.randomUUID();
        this.delegatorUserId = delegatorUserId;
        this.delegatorUsername = delegatorUsername;
        this.delegateUserId = delegateUserId;
        this.delegateUsername = delegateUsername;
        this.delegateName = delegateName;
        this.delegatePosition = delegatePosition;
        this.startDate = startDate;
        this.endDate = endDate;
        this.scopes = scopes;
        this.subject = subject;
        this.status = STATUS_ACTIVE;
    }

    public boolean isActiveOn(LocalDate day) {
        return STATUS_ACTIVE.equals(status) && getDeletedOn() == null
            && day != null && !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    /** Còn hiệu lực hôm nay hoặc sẽ hiệu lực sau này (chưa hết hạn, chưa huỷ). */
    public boolean isCurrentOrUpcoming(LocalDate today) {
        return STATUS_ACTIVE.equals(status) && getDeletedOn() == null && !today.isAfter(endDate);
    }

    public boolean coversScope(String scope) {
        if (scope == null || scopes == null) {
            return false;
        }
        for (String part : scopes.split(";")) {
            if (scope.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    public void revoke(UUID byUserId) {
        this.status = STATUS_REVOKED;
        this.revokedOn = LocalDateTime.now();
        this.revokedBy = byUserId;
    }
}
