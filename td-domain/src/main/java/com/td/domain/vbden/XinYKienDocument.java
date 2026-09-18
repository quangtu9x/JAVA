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
 * N08 ("cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU), yêu cầu người dùng 31/08/2026: "văn bản xin ý kiến"
 * (Domino: {@code Form="xinykien"}, 1 document giả tạo trong hộp thư "văn bản đến" của người được
 * hỏi ý kiến) dùng bảng vật lý ĐỘC LẬP {@code vbden_xinykien_documents} — KHÔNG lưu chung
 * {@code documents} (bảng dùng chung của vbden gốc và mọi module khác), đúng tiền lệ
 * {@code duthao_documents}/{@code vbdi_documents} (BL-230/BL-220, yêu cầu người dùng "tạo bảng
 * riêng" các đợt trước).
 *
 * <p>Entity này CỐ Ý gần như trùng {@code BusinessDocument}/{@code DuThaoDocument}/
 * {@code VbdiDocument} (title/documentType/status/content/tagsJson/attributesJson/metadataJson/
 * dynamicFieldsJson/versionNo) — cùng kiến trúc BL-101 (Dynamic Data Engine), CHỈ khác {@code @Table}
 * (trỏ {@code vbden_xinykien_documents}). Không kế thừa {@code BusinessDocument} (JPA single-table
 * inheritance sẽ gộp lại đúng 1 bảng — ngược ý muốn "độc lập") — trùng lặp ~60 dòng CHẤP NHẬN ĐƯỢC,
 * đổi lại bảng hoàn toàn tách biệt.
 *
 * <p>Bảng này chứa CẢ 2 document_type: {@code vbden_xinykien} (document chính — "văn bản đến giả")
 * VÀ {@code vbden_xinykien_res} (lịch sử "cho ý kiến", tương đương {@code vbden_res} của vbden gốc) —
 * đúng mẫu {@code vbdi_documents} (gộp vbdi + vbdi_res), KHÔNG tách 2 bảng như cụm dự thảo (không có
 * nhiều loại lịch sử khác nhau cần tách riêng).
 */
@Entity
@Table(name = "vbden_xinykien_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XinYKienDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public XinYKienDocument(
            String title,
            String documentType,
            String status,
            String content,
            String tagsJson,
            String attributesJson,
            String metadataJson) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.documentType = documentType;
        this.status = status;
        this.content = content;
        this.tagsJson = normalizeArray(tagsJson);
        this.attributesJson = normalizeObject(attributesJson);
        this.metadataJson = normalizeObject(metadataJson);
    }

    public XinYKienDocument update(
            String title,
            String documentType,
            String status,
            String content,
            String tagsJson,
            String attributesJson,
            String metadataJson) {
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

    // Thay toàn bộ field động (JSONB) — tương đương ShowItems/CreateOrUpdateDocument phía Domino.
    public XinYKienDocument replaceDynamicFields(String dynamicFieldsJson) {
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
