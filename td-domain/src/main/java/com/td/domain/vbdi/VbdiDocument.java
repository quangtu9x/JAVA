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
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung): bảng
 * {@code documents} dùng chung (BL-101) không đủ "độc lập" — người dùng yêu cầu 1 bảng vật lý THẬT
 * SỰ ĐỘC LẬP (không phải bảng con của "documents"), không lệ thuộc lifecycle/schema của module khác.
 *
 * <p>Entity này CỐ Ý gần như trùng {@code BusinessDocument} (title/documentType/status/content/
 * tagsJson/attributesJson/metadataJson/dynamicFieldsJson/versionNo) — cùng kiến trúc BL-101
 * (Dynamic Data Engine: field nghiệp vụ biến thiên trong {@code dynamicFieldsJson} JSONB, không
 * entity riêng cho từng field) — CHỈ khác {@code @Table} (trỏ {@code vbdi_documents}, KHÔNG phải
 * {@code documents}). Không kế thừa {@code BusinessDocument} (JPA single-table inheritance sẽ gộp
 * lại 1 bảng — ngược ý muốn "độc lập"; {@code TABLE_PER_CLASS} đổi hành vi entity gốc, rủi ro không
 * cần thiết) — trùng lặp ~60 dòng CHẤP NHẬN ĐƯỢC để 2 entity/2 bảng hoàn toàn tách biệt.
 *
 * <p>Dùng cho CẢ 2 {@code document_type} thuộc "cụm văn bản đi": {@code vbdi} (document chính) VÀ
 * {@code vbdi_res} (bản ghi trình ký, DoD 2) — cùng 1 bảng vật lý, discriminator
 * {@code document_type} giống hệt cách "documents" phân biệt module (chỉ khác: bảng này CHỈ chứa 2
 * document_type của cụm vbdi, không module nào khác chạm vào).
 */
@Entity
@Table(name = "vbdi_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdiDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "status", length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "tags_json", columnDefinition = "TEXT", nullable = false)
    private String tagsJson = "[]";

    @Column(name = "attributes_json", columnDefinition = "TEXT", nullable = false)
    private String attributesJson = "{}";

    @Column(name = "metadata_json", columnDefinition = "TEXT", nullable = false)
    private String metadataJson = "{}";

    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    @Column(name = "version_no", nullable = false)
    private long versionNo = 1L;

    public VbdiDocument(String title, String documentType, String status, String content,
                        String tagsJson, String attributesJson, String metadataJson) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.documentType = documentType;
        this.status = status;
        this.content = content;
        this.tagsJson = normalizeArray(tagsJson);
        this.attributesJson = normalizeObject(attributesJson);
        this.metadataJson = normalizeObject(metadataJson);
    }

    public VbdiDocument update(String title, String documentType, String status, String content,
                               String tagsJson, String attributesJson, String metadataJson) {
        if (title != null) {
            this.title = title;
        }
        if (documentType != null) {
            this.documentType = documentType;
        }
        if (status != null) {
            this.status = status;
        }
        if (content != null) {
            this.content = content;
        }
        if (tagsJson != null) {
            this.tagsJson = normalizeArray(tagsJson);
        }
        if (attributesJson != null) {
            this.attributesJson = normalizeObject(attributesJson);
        }
        if (metadataJson != null) {
            this.metadataJson = normalizeObject(metadataJson);
        }
        this.versionNo += 1L;
        return this;
    }

    public VbdiDocument replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = normalizeObject(dynamicFieldsJson);
        return this;
    }

    private String normalizeObject(String value) {
        return (value == null || value.isBlank()) ? "{}" : value;
    }

    private String normalizeArray(String value) {
        return (value == null || value.isBlank()) ? "[]" : value;
    }
}