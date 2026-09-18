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
 * Yêu cầu người dùng 28/08/2026 ("ghi log chỉnh sửa thì tạo ra bảng riêng tham khảo thêm như dự
 * thảo đã làm"): bảng vật lý ĐỘC LẬP {@code vbdi_edit_history_documents} — VẾT HIỆU CHỈNH FIELD của
 * văn bản đi, mirror ĐÚNG khuôn {@code DuThaoEditHistoryDocument} (dự thảo, migration
 * {@code V1.0.32}) — port cơ chế Domino: FE {@code LogVariable.getChangedFieldDetails} mỗi lần Lưu
 * phát hiện field đổi thì tự POST 1 document {@code form="log"} ({@code res=<UNID cha>},
 * {@code edit_obj}=JSON {@code {field:{oldvalue,newvalue}}}, {@code creator}, {@code created_date})
 * lên {@code qlvb/add} — bên Java đổi thành 1 dòng table con với {@code parent_id} typed UUID.
 *
 * <p>MỖI dòng = 1 lần Lưu CÓ thay đổi (field text HOẶC thêm/xoá tệp đính kèm — xem
 * {@code VbdiEditHistorySync}); APPEND-ONLY — không cung cấp {@code update()} (sai phải ghi dòng
 * mới, đúng chất audit log BL-103), không có khái niệm trạng thái/số kì.</p>
 */
@Entity
@Table(name = "vbdi_edit_history_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdiEditHistoryDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    /** Id văn bản đi cha ({@code vbdi_documents.id}) — tương đương item {@code res} Domino. */
    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    /** Form của bản ghi gốc lúc ghi vết (vd {@code vbdi}) — tương đương {@code form_edit}. */
    @Column(name = "form_edit", length = 100)
    private String formEdit;

    /** JSON {@code {field:{oldvalue,newvalue}}} — tương đương item {@code edit_obj} Domino. */
    @Column(name = "edit_obj", columnDefinition = "jsonb", nullable = false)
    private String editObjJson;

    /** Tên hiển thị người hiệu chỉnh (item {@code creator} Domino — TUỲ CHỌN, chỉ mang giá trị trình bày). */
    @Column(name = "creator_name", length = 300)
    private String creatorName;

    /** Username người hiệu chỉnh — SERVER-AUTHORITATIVE qua CurrentUserProvider, client không đặt được. */
    @Column(name = "user_creator", length = 100)
    private String userCreator;

    // Constructor nhận ĐẦY ĐỦ giá trị ghi vết (append-only — không có setter công khai nào thay
    // đổi nội dung sau khi tạo, kể cả qua update(); khác VbdiDocument vì đây là audit trail).
    public VbdiEditHistoryDocument(UUID parentId, String formEdit, String editObjJson,
                                   String creatorName, String userCreator) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.formEdit = formEdit;
        this.editObjJson = normalizeObject(editObjJson);
        this.creatorName = creatorName;
        this.userCreator = userCreator;
    }

    // Append-only: KHÔNG cung cấp update()/replaceEditObj - vết hiệu chỉnh không bao giờ được sửa.

    private String normalizeObject(String value) {
        return (value == null || value.isBlank()) ? "{}" : value;
    }
}
