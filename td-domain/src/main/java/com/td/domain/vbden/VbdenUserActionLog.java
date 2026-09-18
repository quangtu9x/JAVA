package com.td.domain.vbden;

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

/**
 * Yêu cầu người dùng 18/09/2026 ("thêm vết audit của văn bản đến, tham khảo văn bản đi đã làm và có
 * hiển thị khi xem chi tiết"): entity cho bảng vật lý ĐỘC LẬP {@code vbden_user_action_logs} —
 * mirror ĐÚNG {@code com.td.domain.vbdi.VbdiUserActionLog} (văn bản đi) — "Lịch sử thao tác văn
 * bản" hiển thị ở khối {@code LogUserAction.jsx} (nhúng trong Chi tiết văn bản đến), KHÁC hẳn
 * {@link VbdenEditHistoryDocument} ("Vết hiệu chỉnh" — chỉ ghi FIELD nào bị đổi, không ghi HÀNH
 * ĐỘNG).
 *
 * <p>Append-only (không sửa/xoá) — mỗi dòng là 1 lần bấm 1 hành động cụ thể trên văn bản đến
 * (tiếp nhận/chuyển xử lý/thu hồi/trả lại/kết thúc xử lý/...). {@code username}
 * SERVER-AUTHORITATIVE qua {@code CurrentUserProvider} (client không đặt được, tránh giả mạo "ai đã
 * thao tác") — {@code fullname}/{@code unit}/{@code identifier} CHẤP NHẬN từ client (field TRÌNH
 * BÀY thuần tuý, không dùng cho phân quyền/logic nghiệp vụ nào, đúng mức độ tin cậy đã áp dụng ở
 * {@code VbdiUserActionLog}).</p>
 */
@Entity
@Table(name = "vbden_user_action_logs")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdenUserActionLog extends AuditableEntity<UUID> implements IAggregateRoot {

    /** Id văn bản đến cha ({@code documents.id}, {@code document_type='vbden'}). */
    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    /** Username người thực hiện — SERVER-AUTHORITATIVE qua CurrentUserProvider, client không đặt được. */
    @Column(name = "username", length = 100)
    private String username;

    /** Tên hiển thị người thực hiện — field trình bày, chấp nhận từ client. */
    @Column(name = "fullname", length = 300)
    private String fullname;

    /** Tên đơn vị hiển thị — field trình bày, chấp nhận từ client. */
    @Column(name = "unit", length = 300)
    private String unit;

    /** Mã định danh đơn vị — field trình bày, chấp nhận từ client. */
    @Column(name = "identifier", length = 100)
    private String identifier;

    /**
     * Loại thao tác tự do (vd {@code tiepnhan}/{@code vaosovanbanden}/{@code chuyenxuly}/
     * {@code thuhoivanban}/{@code laylaivanban}/{@code xulylai}/{@code tralaivanban}/
     * {@code ketthucxuly}/{@code vanthuketthucxuly}/{@code capnhatphoihop}/{@code capnhatykien}/
     * {@code choykienxinykien}/...) — GIÁ TRỊ của cột này, không giới hạn danh sách cố định.
     */
    @Column(name = "action_type", length = 100)
    private String actionType;

    // Constructor nhận ĐẦY ĐỦ giá trị ghi vết (append-only - không có update()/setter công khai nào
    // thay đổi nội dung sau khi tạo, giống VbdiUserActionLog).
    public VbdenUserActionLog(UUID parentId, String username, String fullname, String unit,
            String identifier, String actionType) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.username = username;
        this.fullname = fullname;
        this.unit = unit;
        this.identifier = identifier;
        this.actionType = actionType;
    }

    // Append-only: KHONG cung cap update() - vet thao tac khong bao gio duoc sua.
}
