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
 * Bảng vật lý ĐỘC LẬP {@code hosoluutru_van_ban_documents} — văn bản gắn vào 1 hồ sơ lưu trữ (form
 * Domino {@code vanban}, {@code documentType="vanban"}), xem
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 6.2/8.1.
 *
 * <p>Được tạo hàng loạt khi {@code TaoHoSoLuuTruTuHoSoCVUseCase} copy văn bản liên quan (vbden/vbdi/văn
 * bản khác) từ hồ sơ {@code hosocv} nguồn, hoặc thêm thủ công qua {@code ThemVanBanLuuTruUseCase}.
 *
 * <p>Field động chính: {@code hoso_id} (liên kết cha, bắt buộc), đúng 1 trong
 * {@code document_in_id}/{@code document_out_id}/{@code other_related_document_id} (tuỳ nguồn),
 * {@code source} ({@code vbden}/{@code vbdi}/{@code hosocv}), {@code code}, {@code identifier},
 * {@code promulgation_date}, {@code received_date}, {@code subject}, {@code unit_in_charge}(
 * {@code _identifier}), {@code signer}, {@code signer_position}, {@code document_type}, {@code sector},
 * {@code creator}/{@code user_creator}, {@code unit_creator}, {@code note}.
 */
@Entity
@Table(name = "hosoluutru_van_ban_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoLuuTruVanBanDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoLuuTruVanBanDocument(
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

    public HoSoLuuTruVanBanDocument update(
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

    public HoSoLuuTruVanBanDocument replaceDynamicFields(String dynamicFieldsJson) {
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
