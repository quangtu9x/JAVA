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
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác như làm với
 * draft_process"): entity cho bảng vật lý ĐỘC LẬP {@code vbdi_send_log_documents} (migration
 * {@code V1.0.33__Create_Vbdi_Send_Log_Documents_Table.sql}) — port dữ liệu "Nhật ký gửi nhận" thật
 * của Domino ({@code coordinator_obj_log_rtext}/{@code internal_unit_obj_log_rtext}/
 * {@code contact_unit_obj_log_rtext}/{@code external_unit_obj_log_rtext}, xem
 * {@code docs/views_domino/data_vbdi_mau.txt} + {@code docs/analysis/07-phan-tich-port-senddocument-
 * vbdi-qlvb.md}) — tách khỏi {@link VbdiDocument#getDynamicFieldsJson()} thay vì lưu chung 1 JSON
 * lồng nhau ngày càng phình to trên bản thân document.
 *
 * <p><b>Vì sao KHÔNG dùng khuôn Dynamic Data Engine (như {@code draft_process_documents})</b>: dữ
 * liệu ở đây có cấu trúc CỐ ĐỊNH, đã biết trước đầy đủ (nhóm nơi nhận/định danh/trạng thái sự
 * kiện...), không phải field biến thiên tự do theo từng loại document — nên dùng khuôn TYPED cột
 * riêng, đúng tiền lệ MỚI NHẤT {@code duthao_edit_history_documents} (V1.0.32, xem javadoc
 * {@code DuThaoEditHistoryDocument}: {@code parent_id} typed UUID thay vì containment JSONB) thay vì
 * khuôn generic {@code title/documentType/status/tags/attributes/metadata/dynamicFieldsJson} của
 * {@link VbdiDocument}/{@code DraftProcessDocument} — tránh phải native SQL containment JSONB cho 1
 * dữ liệu vốn dĩ đã typed sẵn.</p>
 *
 * <p><b>Mỗi dòng = 1 sự kiện gửi/nhận cho 1 nơi nhận</b> (append-only, không sửa/xoá) — tương đương
 * 1 phần tử trong mảng {@code detail[]} của {@code *_obj_log_rtext} Domino gốc (vd {@code "Lê Thị
 * Xuân gửi văn bản cho ... vào lúc: ..."} → {@code "Đã đến"} → {@code "Đã tiếp nhận"} → {@code "Phân
 * công xử lý (...)"}). Ghi bởi {@code AddVbdiSendLogUseCase}, dùng bởi {@code SendVbdiDocumentUseCase}
 * (DoD "gửi văn bản đi" — xem tài liệu phân tích 07) cho mỗi nơi nhận trong 4 nhóm.</p>
 */
@Entity
@Table(name = "vbdi_send_log_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdiSendLogDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    /** Id văn bản đi cha ({@code vbdi_documents.id}). */
    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    /** {@code senddocument}/{@code resenddocument}/{@code guibosung}/{@code guilaitoanbo}. */
    @Column(name = "send_type", length = 50)
    private String sendType;

    /** {@code coordinator}/{@code internal_unit}/{@code contact_unit}/{@code external_unit}. */
    @Column(name = "recipient_group", length = 50)
    private String recipientGroup;

    /** Mã định danh nơi nhận (đơn vị), hoặc username nếu {@code recipientGroup=coordinator}. */
    @Column(name = "recipient_identifier", length = 100)
    private String recipientIdentifier;

    /** Tên hiển thị nơi nhận. */
    @Column(name = "recipient_name", length = 300)
    private String recipientName;

    /** Mã mail-in nếu gửi qua liên thông/mail-in (rỗng nếu gửi nội bộ). */
    @Column(name = "mailin", length = 100)
    private String mailin;

    /** Nhãn trạng thái sự kiện dạng tự do (Gửi văn bản/Đã đến/Đã tiếp nhận/Phân công xử lý/...). */
    @Column(name = "status", length = 100)
    private String status;

    /** Nội dung mô tả sự kiện (tương đương {@code content} trong {@code detail[]} Domino gốc). */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Username người thực hiện — SERVER-AUTHORITATIVE qua CurrentUserProvider, client không đặt được. */
    @Column(name = "user_creator", length = 100)
    private String userCreator;

    // Constructor nhận ĐẦY ĐỦ giá trị ghi vết (append-only - không có update()/setter công khai nào
    // thay đổi nội dung sau khi tạo, đúng chất audit log BL-103, giống DuThaoEditHistoryDocument).
    public VbdiSendLogDocument(UUID parentId, String sendType, String recipientGroup,
            String recipientIdentifier, String recipientName, String mailin, String status,
            String content, String userCreator) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.sendType = sendType;
        this.recipientGroup = recipientGroup;
        this.recipientIdentifier = recipientIdentifier;
        this.recipientName = recipientName;
        this.mailin = mailin;
        this.status = status;
        this.content = content;
        this.userCreator = userCreator;
    }

    // Append-only: KHONG cung cap update() - vet gui/nhan khong bao gio duoc sua.
}
