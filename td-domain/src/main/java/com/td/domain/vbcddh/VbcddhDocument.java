package com.td.domain.vbcddh;

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
 * BL-250 (VBCDDH Core), bổ sung 08/09/2026 (yêu cầu người dùng "port sang JAVA, tuân thủ kiến trúc,
 * tạo bảng riêng, sử dụng dynamic fields"): bảng {@code documents} dùng chung (BL-101) — cách BL-250
 * bản gốc đã dùng (xem lịch sử {@code docs/migration-backlog/01-migration-backlog-tong-hop.md} mục
 * BL-250: "tái dùng documents/dynamic_fields, không xây entity/Workflow riêng") — KHÔNG còn đúng yêu
 * cầu kiến trúc mới nhất: cần 1 bảng vật lý THẬT SỰ ĐỘC LẬP cho "cụm vbcddh", đúng tiền lệ đã áp dụng
 * cho vbdi (BL-220, xem javadoc {@code com.td.domain.vbdi.VbdiDocument}) và hosocv (BL-240).
 *
 * <p>Entity này CỐ Ý gần như trùng {@code BusinessDocument}/{@code VbdiDocument} (title/documentType/
 * status/content/tagsJson/attributesJson/metadataJson/dynamicFieldsJson/versionNo) — cùng kiến trúc
 * BL-101 (Dynamic Data Engine: field nghiệp vụ biến thiên trong {@code dynamicFieldsJson} JSONB,
 * không entity riêng cho từng field) — CHỈ khác {@code @Table} (trỏ {@code vbcddh_documents}, KHÔNG
 * phải {@code documents}). Không kế thừa {@code BusinessDocument} (JPA single-table inheritance sẽ
 * gộp lại 1 bảng — ngược ý muốn "độc lập") — trùng lặp ~60 dòng CHẤP NHẬN ĐƯỢC để 2 entity/2 bảng
 * hoàn toàn tách biệt, đúng tiền lệ {@code VbdiDocument}.
 *
 * <p>Dùng cho CẢ 2 {@code document_type} thuộc "cụm vbcddh" (đúng {@code databasetype} FE gửi):
 * {@code vbcddh} (văn bản chỉ đạo {@code form=vanban} VÀ nhiệm vụ {@code form=nhiemvu} — phân biệt
 * bằng field động {@code form}, giống hệt Domino thật dùng chung 1 database {@code vbcddh.nsf}) và
 * {@code vbcddh_res} (bản ghi tiến độ/kết quả xử lý {@code form=tiendonhiemvu}/{@code
 * ketquaxulynhiemvu}, tương đương {@code vbcddh_res.nsf}) — cùng 1 bảng vật lý, discriminator
 * {@code document_type}, đúng tiền lệ {@code VbdiDocument} gộp {@code vbdi}/{@code vbdi_res}.
 */
@Entity
@Table(name = "vbcddh_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbcddhDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public VbcddhDocument(String title, String documentType, String status, String content,
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

    public VbcddhDocument update(String title, String documentType, String status, String content,
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

    public VbcddhDocument replaceDynamicFields(String dynamicFieldsJson) {
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
