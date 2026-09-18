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
 * Bảng vật lý ĐỘC LẬP {@code hosoluutru_phieu_yeu_cau_documents} — phiếu yêu cầu khai thác (mượn/trả)
 * tài liệu lưu trữ (form Domino {@code phieuyeucau}, {@code documentType="phieuyeucau"}), xem
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 6.3.
 *
 * <p>Field động chính: {@code hoso_id} (liên kết hồ sơ muốn mượn), {@code identifier}, {@code code}/
 * {@code title} (copy từ hồ sơ), {@code creator}/{@code user_creator} (người mượn), {@code return_date}
 * (hạn hẹn trả, bắt buộc), {@code subject} (mục đích mượn, bắt buộc), {@code status}
 * ({@code "0"}=chờ giải quyết, {@code "1"}=chấp nhận, {@code "2"}=người mượn tự thu hồi,
 * {@code "3"}=không chấp nhận), {@code status1} (chỉ có ý nghĩa khi {@code status="1"}:
 * {@code ""}/{@code "0"}=đang mượn, {@code "1"}=đã trả, {@code "2"}=kho thu hồi hồ sơ).
 */
@Entity
@Table(name = "hosoluutru_phieu_yeu_cau_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoLuuTruPhieuYeuCauDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoLuuTruPhieuYeuCauDocument(
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

    public HoSoLuuTruPhieuYeuCauDocument update(
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

    public HoSoLuuTruPhieuYeuCauDocument replaceDynamicFields(String dynamicFieldsJson) {
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
