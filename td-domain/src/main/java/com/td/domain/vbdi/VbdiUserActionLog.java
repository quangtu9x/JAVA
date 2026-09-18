package com.td.domain.vbdi;

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
 * 30/08/2026 (yêu cầu người dùng "port qlvb/vbdi?databasetype=vbdi&action=getloguseraction sang
 * java"): entity cho bảng vật lý ĐỘC LẬP {@code vbdi_user_action_logs} (migration
 * {@code V1.0.36__Create_Vbdi_User_Action_Logs_Table.sql}) — port cặp action Domino
 * {@code loguseraction} (ghi)/{@code getloguseraction} (đọc), cả 2 cùng dùng 1 class Domino
 * {@code vn.com.tandan.xsp.services.vbdi.LogUserAction.java}, lưu ở database phụ {@code vbdi_res}
 * (KHÁC database chính {@code vbdi}), {@code Form="user_action"} — hiển thị ở khối "Lịch sử thao
 * tác văn bản" ({@code LogUserAction.jsx}, nhúng trong Chi tiết văn bản đi).
 *
 * <p><b>Khác biệt CHỦ ĐÍCH với mô hình lưu trữ Domino gốc</b>: Domino tạo 1 document DUY NHẤT cho
 * mỗi cặp {@code (document_id, username)} (upsert theo view key ghép {@code document_id+"|"+
 * username}), rồi PIVOT mỗi loại thao tác ({@code action_type}, vd {@code "guivanban"}/
 * {@code "thuhoivanban"}/{@code "sendOnSave"}/...) thành 1 FIELD RIÊNG trên chính document đó, giá
 * trị là danh sách timestamp nối {@code ";"} (Domino {@code Item#appendToTextList}, dedupe theo
 * {@code containsValue}) — mô hình "wide table" nhân tạo: thêm 1 loại action mới = thêm 1 cột, không
 * có giới hạn rõ ràng. Ở đây theo ĐÚNG khuôn "audit trail append-only, mỗi dòng = 1 sự kiện" đã dùng
 * cho {@code VbdiSendLogDocument} (V1.0.33)/{@code VbdiEditHistoryDocument} (V1.0.35): mỗi lần gọi
 * ghi log ({@code AddVbdiUserActionLogUseCase}) tạo 1 dòng MỚI, {@code actionType} là GIÁ TRỊ của 1
 * cột (không phải TÊN cột) — không giới hạn/không cần thêm cột khi phát sinh loại thao tác nghiệp vụ
 * mới. Khi đọc lại để khớp shape cũ FE đang render (1 dòng/1 user, mỗi loại action là 1 field chứa
 * danh sách timestamp), {@code ListVbdiUserActionLogUseCase} tự NHÓM các dòng phẳng theo
 * {@code (parentId, username)} — xem javadoc use case đó.</p>
 *
 * <p><b>Append-only</b> (không sửa/xoá) — mỗi dòng là 1 lần bấm 1 hành động cụ thể trên văn bản.
 * {@code username} SERVER-AUTHORITATIVE qua {@code CurrentUserProvider} (client không đặt được,
 * tránh giả mạo "ai đã thao tác") — {@code fullname}/{@code unit}/{@code identifier} CHẤP NHẬN từ
 * client (field TRÌNH BÀY thuần tuý, không dùng cho phân quyền/logic nghiệp vụ nào, đúng mức độ tin
 * cậy Domino gốc vốn cũng lấy thẳng từ {@code currentUser} phía client).</p>
 *
 * <p><b>09/09/2026 (2, KHÔNG dùng cho log xem/tải tệp đính kèm nữa)</b>: bản đầu (cùng ngày) từng
 * thêm {@code fileId}/{@code fileName} vào chính bảng này — ĐÃ BỎ theo yêu cầu người dùng: log
 * xem/tải tệp cần DÙNG CHUNG cho mọi module (không chỉ vbdi) và cần model chống phình bảng
 * (upsert theo lần xem đầu/cuối, không phải 1 dòng/1 sự kiện) — khác hẳn model append-only của
 * bảng này. Xem {@code com.td.domain.documents.FileAccessLog}/{@code
 * com.td.application.documents.RecordFileAccessUseCase} (bảng riêng {@code file_access_logs},
 * generic theo mọi {@code documentType}).</p>
 */
@Entity
@Table(name = "vbdi_user_action_logs")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdiUserActionLog extends AuditableEntity<UUID> implements IAggregateRoot {

    /** Id văn bản đi cha ({@code vbdi_documents.id}). */
    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    /** Username người thực hiện — SERVER-AUTHORITATIVE qua CurrentUserProvider, client không đặt được. */
    @Column(name = "username", length = 100)
    private String username;

    /** Tên hiển thị người thực hiện — field trình bày, chấp nhận từ client (đúng Domino gốc). */
    @Column(name = "fullname", length = 300)
    private String fullname;

    /** Tên đơn vị hiển thị — field trình bày, chấp nhận từ client. */
    @Column(name = "unit", length = 300)
    private String unit;

    /** Mã định danh đơn vị — field trình bày, chấp nhận từ client. */
    @Column(name = "identifier", length = 100)
    private String identifier;

    /**
     * Loại thao tác tự do (vd {@code guivanban}/{@code guilaivanban}/{@code thuhoivanban}/
     * {@code laylaivanban}/{@code sendOnSave}/{@code guibosung}/{@code ganvanbanden}/
     * {@code new_ganvanbanden}/{@code vaosonhieuvanbandi}/...) — GIÁ TRỊ của cột này, không giới hạn
     * danh sách cố định (khác Domino coi mỗi loại là 1 TÊN field/cột riêng).
     */
    @Column(name = "action_type", length = 100)
    private String actionType;

    // Constructor nhận ĐẦY ĐỦ giá trị ghi vết (append-only - không có update()/setter công khai nào
    // thay đổi nội dung sau khi tạo, đúng chất audit log BL-103, giống VbdiSendLogDocument).
    public VbdiUserActionLog(UUID parentId, String username, String fullname, String unit,
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
