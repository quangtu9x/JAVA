package com.td.domain.notifications;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 27/10/2026 - hộp thư thông báo (chuông) của người dùng, lưu ở DB ĐƠN VỊ (tenant). 1 dòng = 1 thông báo
 * cho 1 người nhận: username ({@link #RECIPIENT_USER}) hoặc mã định danh đơn vị ({@link #RECIPIENT_UNIT} -
 * hộp thư chung của văn thư đơn vị, 1 người đọc là cả đơn vị coi như đã đọc). Socket chỉ là "chuông cửa":
 * người offline/mất kết nối kéo lại từ bảng này. Xem {@code docs/architecture/realtime-socket-notifications.md}.
 *
 * <p>Cột tường minh = thứ cần lọc/đếm; nội dung hiển thị (code, creator, position, opinion, deadline,
 * message...) nằm trong {@code dynamic_fields} - đúng quy ước bảng khác. Ẩn thông báo = xoá mềm.</p>
 */
@Entity
@Table(name = "user_notifications")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotification extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String RECIPIENT_USER = "user";
    public static final String RECIPIENT_UNIT = "unit";

    @Column(name = "recipient", length = 150, nullable = false)
    private String recipient;

    @Column(name = "recipient_type", length = 10, nullable = false)
    private String recipientType;

    @Column(name = "database_type", length = 50)
    private String databaseType;

    @Column(name = "action", length = 100, nullable = false)
    private String action;

    @Column(name = "document_id", length = 100)
    private String documentId;

    @Column(name = "read_on")
    private LocalDateTime readOn;

    @Column(name = "read_by", length = 100)
    private String readBy;

    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    public UserNotification(String recipient, boolean unit, String databaseType, String action, String documentId,
                            String dynamicFieldsJson) {
        this.id = UUID.randomUUID();
        this.recipient = recipient;
        this.recipientType = unit ? RECIPIENT_UNIT : RECIPIENT_USER;
        this.databaseType = databaseType;
        this.action = action == null || action.isBlank() ? "thongbao" : action;
        this.documentId = documentId;
        this.dynamicFieldsJson = dynamicFieldsJson == null || dynamicFieldsJson.isBlank() ? "{}" : dynamicFieldsJson;
    }

    public boolean isRead() {
        return readOn != null;
    }
}
