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
 * Yêu cầu người dùng 24/09/2026 ("lưu lại vết ngày giờ cập nhật, có liên kết tới văn bản đã cập nhật,
 * thể hiện được những tệp cũ đã cập nhật nhưng không cho tải về"): bảng vật lý ĐỘC LẬP
 * {@code vbden_update_history_documents} — LỊCH SỬ CẬP NHẬT văn bản đến đã vào sổ từ bản gửi lại/thay
 * thế (nút "Cập nhật VB", {@code CapNhatVanBanThayTheThuHoiUseCase}). Mirror khuôn
 * {@link VbdenEditHistoryDocument} (vết hiệu chỉnh field) nhưng KHÁC nghiệp vụ: vết hiệu chỉnh ghi
 * field người dùng sửa tay trên form; bảng này ghi lần hệ thống GỘP nội dung + tệp từ văn bản nguồn
 * ({@code sourceDocumentId}) vào văn bản đích ({@code parentId}).
 *
 * <p>{@code updateObjJson} = snapshot tại thời điểm cập nhật (xem COMMENT cột trong migration
 * {@code V1.0.64}) — tên tệp cũ đã thay thế CHỈ còn ở đây (tệp thật đã bị ẩn khỏi mọi truy vấn qua
 * {@code file_metadata.superseded_at}). APPEND-ONLY, không cung cấp {@code update()}.</p>
 */
@Entity
@Table(name = "vbden_update_history_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdenUpdateHistoryDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String ACTION_CAP_NHAT = "capnhat";
    public static final String ACTION_THAY_THE_THU_HOI = "thaythe_thuhoi";

    /** Id văn bản đến ĐÍCH (đã vào sổ, được cập nhật) — {@code documents.id}. */
    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    /** Id văn bản đến NGUỒN (bản gửi lại/thay thế, thường đã tự huỷ sau khi cập nhật). */
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    /** {@link #ACTION_CAP_NHAT} | {@link #ACTION_THAY_THE_THU_HOI}. */
    @Column(name = "action", length = 50, nullable = false)
    private String action;

    @Column(name = "update_obj", columnDefinition = "jsonb", nullable = false)
    private String updateObjJson;

    /** Tên hiển thị người cập nhật (chỉ để trình bày; danh tính thật là {@code userCreator}). */
    @Column(name = "creator_name", length = 300)
    private String creatorName;

    /** Username người cập nhật — SERVER-AUTHORITATIVE qua CurrentUserProvider. */
    @Column(name = "user_creator", length = 100)
    private String userCreator;

    public VbdenUpdateHistoryDocument(UUID parentId, UUID sourceDocumentId, String action,
                                      String updateObjJson, String creatorName, String userCreator) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.sourceDocumentId = sourceDocumentId;
        this.action = action;
        this.updateObjJson = (updateObjJson == null || updateObjJson.isBlank()) ? "{}" : updateObjJson;
        this.creatorName = creatorName;
        this.userCreator = userCreator;
    }

    // Append-only: KHÔNG cung cấp update() - lịch sử cập nhật không bao giờ được sửa.
}
