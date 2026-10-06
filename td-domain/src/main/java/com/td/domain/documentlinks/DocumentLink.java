package com.td.domain.documentlinks;

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
 * 06/10/2026 (yêu cầu người dùng "thay đổi cách quản lý liên kết, thay vì ghi trực tiếp vào từng văn
 * bản"): 1 dòng = 1 liên kết có hướng giữa 2 văn bản (vd hồ sơ công việc → dự thảo), thay cho các field
 * id nối ";" ghi chéo trong {@code dynamic_fields} của CẢ 2 phía ({@code document_profiles_id},
 * {@code *_attached_id}...). Xem {@code docs/architecture/document-links.md}.
 *
 * <p>Gỡ liên kết = xoá mềm ({@link #markAsDeleted}); gắn lại sau khi gỡ tạo dòng mới (unique index
 * chỉ áp cho dòng chưa xoá). Không có update — liên kết chỉ có tạo/gỡ.</p>
 */
@Entity
@Table(name = "document_links")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentLink extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "source_type", length = 50, nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "target_type", length = 50, nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "link_type", length = 50, nullable = false)
    private String linkType;

    /**
     * Bản chụp phần tử hiển thị của liên kết (V1.0.70) - vd {id, code, subject, unit_publish,
     * promulgation_date, files, dbpath, database_type} của "Văn bản kèm theo"/"Trả lời cho văn bản đến";
     * API dựng lại documentin_obj/vanban_lienquan_obj từ cột này. "{}" nếu loại liên kết không cần.
     */
    @Column(name = "link_data", columnDefinition = "jsonb", nullable = false)
    private String linkDataJson = "{}";

    public DocumentLink(DocumentLinkType linkType, UUID sourceId, UUID targetId) {
        this.id = UUID.randomUUID();
        this.linkType = linkType.code();
        this.sourceType = linkType.sourceType();
        this.targetType = linkType.targetType();
        this.sourceId = sourceId;
        this.targetId = targetId;
    }

    public DocumentLink(DocumentLinkType linkType, UUID sourceId, UUID targetId, String linkDataJson) {
        this(linkType, sourceId, targetId);
        this.linkDataJson = (linkDataJson == null || linkDataJson.isBlank()) ? "{}" : linkDataJson;
    }

    /** Cập nhật bản chụp (gắn lại liên kết đang có với thông tin mới hơn). */
    public void replaceLinkData(String linkDataJson) {
        this.linkDataJson = (linkDataJson == null || linkDataJson.isBlank()) ? "{}" : linkDataJson;
    }
}
