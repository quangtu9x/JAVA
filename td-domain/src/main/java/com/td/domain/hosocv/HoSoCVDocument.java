package com.td.domain.hosocv;

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
 * Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới, sử dụng jsonb (dynamic field)
 * như các module đã thực hiện"): bảng {@code documents} dùng chung (BL-101) đúng thiết kế BAN ĐẦU
 * của BL-240, nhưng đúng tiền lệ {@code duthao}/{@code vbdi} (BL-230, yêu cầu người dùng 24/08/2026
 * "tạo bảng riêng, để độc lập dữ liệu") — hồ sơ công việc giờ có bảng vật lý ĐỘC LẬP
 * {@code hosocv_documents}.
 *
 * <p>Entity này CỐ Ý gần như trùng {@code BusinessDocument}/{@code DuThaoDocument}
 * (title/documentType/status/content/tagsJson/attributesJson/metadataJson/dynamicFieldsJson/
 * versionNo) — cùng kiến trúc BL-101 (Dynamic Data Engine: field nghiệp vụ biến thiên trong
 * {@code dynamicFieldsJson} JSONB, không entity riêng cho từng field) — CHỈ khác {@code @Table}
 * (trỏ {@code hosocv_documents}, KHÔNG phải {@code documents}). Không kế thừa
 * {@code BusinessDocument}/{@code DuThaoDocument} (JPA single-table inheritance theo mặc định sẽ
 * gộp lại đúng 1 bảng — ngược ý muốn "độc lập") — trùng lặp ~60 dòng này CHẤP NHẬN ĐƯỢC, đổi lại 2
 * entity/2 bảng hoàn toàn tách biệt, không ai ảnh hưởng ai (đúng khuôn đã dùng cho
 * {@link com.td.domain.duthao.DuThaoDocument}).
 *
 * <p>Bảng này CHỈ chứa document chính {@code document_type='hosocv'} (form Domino
 * {@code document_profiles}). Vết xử lý/lịch sử (trình duyệt, duyệt, chuyển lưu trữ, ý kiến phối
 * hợp/góp ý — tương đương {@code quatrinhduyet}/{@code storag_log}/
 * {@code document_coordinated_opinion}/{@code document_feedback} phía Domino, database
 * {@code hosocv_res}) vào bảng vật lý RIÊNG {@code hosocv_process_documents} (migration V1.0.44,
 * entity {@link HoSoCVProcessDocument}/{@link HoSoCVProcessDocumentRepository}), theo đúng tiền lệ
 * tách {@code draft_process_documents} khỏi {@code duthao_documents}.
 */
@Entity
@Table(name = "hosocv_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoCVDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoCVDocument(
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

    public HoSoCVDocument update(
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
    public HoSoCVDocument replaceDynamicFields(String dynamicFieldsJson) {
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
