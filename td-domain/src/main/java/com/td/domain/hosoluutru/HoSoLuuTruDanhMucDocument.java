package com.td.domain.hosoluutru;

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
 * Bảng vật lý ĐỘC LẬP {@code hosoluutru_danh_muc_documents} — 3 danh mục dùng chung của module
 * {@code hosoluutru}: {@code kyhieuhoso} (ký hiệu hồ sơ), {@code chatluong} (chất lượng tài liệu),
 * {@code loaihinhtailieu} (loại hình tài liệu) — cùng 1 UI Domino (khác nhau bởi {@code nameform}), xem
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 6.5.
 *
 * <p>Gộp cả 3 vào 1 bảng, phân biệt bằng cột tĩnh {@code documentType} (= giá trị {@code nameform}:
 * {@code "kyhieuhoso"}/{@code "chatluong"}/{@code "loaihinhtailieu"}) — đây là 3 "loại tài liệu danh
 * mục" độc lập (không phải audit trail như {@link HoSoLuuTruProcessDocument}), nên KHÔNG dùng field
 * động {@code process_type} mà dùng thẳng cột {@code document_type} có sẵn của entity, đúng cách
 * {@code documents}/{@code hosocv_documents} đã phân biệt nhiều loại document trong 1 bảng.
 *
 * <p>Field động chính: {@code sort_order}, {@code name}, {@code identifier}, {@code unit_used}(
 * {@code _id}), {@code creator}/{@code user_creator}; riêng {@code kyhieuhoso}: thêm
 * {@code code_start_number}, {@code internal_unit_obj} (JSON); riêng {@code chatluong}/
 * {@code loaihinhtailieu}: thêm {@code short_name}.
 */
@Entity
@Table(name = "hosoluutru_danh_muc_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoLuuTruDanhMucDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoLuuTruDanhMucDocument(
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

    public HoSoLuuTruDanhMucDocument update(
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

    public HoSoLuuTruDanhMucDocument replaceDynamicFields(String dynamicFieldsJson) {
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
