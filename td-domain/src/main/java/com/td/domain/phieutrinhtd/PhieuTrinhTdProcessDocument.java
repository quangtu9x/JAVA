package com.td.domain.phieutrinhtd;

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
 * Nhật ký "quatrinh" của phiếu trình thẩm định (append-only) — mỗi lần trình/chuyển trạng thái tạo 1
 * bản ghi mới ở đây, đúng cơ chế {@code ChiTietPage.jsx#handleTrinh} phía Domino
 * ({@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 2.2, 6.1). Bảng vật lý
 * ĐỘC LẬP {@code phieutrinhtd_process_documents} (migration V1.0.54) — KHÔNG dùng chung
 * {@code hosocv_process_documents}/{@code documents}.
 *
 * <p>Field động ({@link #dynamicFieldsJson}): {@code parentid} (id phiếu trình cha), {@code
 * action_type} (= status mới được chuyển tới), {@code creator}/{@code user_creator}, {@code
 * receiver}/{@code user_receiver}/{@code receiver_obj}, {@code create_date}.
 */
@Entity
@Table(name = "phieutrinhtd_process_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhieuTrinhTdProcessDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String DOCUMENT_TYPE = "phieutrinhtd_res";

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

    public PhieuTrinhTdProcessDocument(String title) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.documentType = DOCUMENT_TYPE;
    }

    public PhieuTrinhTdProcessDocument replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank())
            ? "{}" : dynamicFieldsJson;
        return this;
    }
}
