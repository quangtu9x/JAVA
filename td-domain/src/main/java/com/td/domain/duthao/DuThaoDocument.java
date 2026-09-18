package com.td.domain.duthao;

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
 * BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026 ("chuyển dự thảo sang bảng mới, không lưu
 * chung documents"): bảng {@code documents} dùng chung (BL-101) là partition RÕ RÀNG KHÔNG đủ —
 * người dùng xác nhận lại (24/08/2026): muốn 1 bảng vật lý THẬT SỰ ĐỘC LẬP (không phải bảng con
 * của "documents"), không lệ thuộc lifecycle/schema của module khác qua bất kỳ hình thức nào.
 *
 * <p>Entity này CỐ Ý gần như trùng {@code BusinessDocument} (title/documentType/status/content/
 * tagsJson/attributesJson/metadataJson/dynamicFieldsJson/versionNo) — cùng kiến trúc BL-101
 * (Dynamic Data Engine: field nghiệp vụ biến thiên trong {@code dynamicFieldsJson} JSONB, không
 * entity riêng cho từng field) — CHỈ khác {@code @Table} (trỏ {@code duthao_documents}, KHÔNG
 * phải {@code documents}). Không kế thừa {@code BusinessDocument} (JPA single-table inheritance
 * theo mặc định sẽ gộp lại đúng 1 bảng — ngược ý muốn "độc lập"; {@code TABLE_PER_CLASS} thay đổi
 * hành vi entity gốc đang chạy ổn định, rủi ro không cần thiết) — trùng lặp ~60 dòng này CHẤP NHẬN
 * ĐƯỢC, đổi lại 2 entity/2 bảng hoàn toàn tách biệt, không ai ảnh hưởng ai.
 *
 * <p><b>Cập nhật 25/08/2026</b>: bảng này CHỈ chứa document chính {@code document_type='duthao'}
 * — MỌI bản ghi lịch sử liên quan đều là {@code draft_process}: vết xử lý workflow (DT-007) lẫn
 * vết "chuyển/cho ý kiến phối hợp" (trước đây SAI khi coi là loại {@code duthao_res} riêng — đối
 * chiếu Domino: database {@code duthao_res} chứa các document {@code form="draft_process"}, xem
 * {@code ChuyenPhoiHopDuThao.java}/{@code ChoYKienPhoiHop.java}). Các vết này đều vào bảng vật lý
 * RIÊNG {@code draft_process_documents} (migration V1.0.30, entity
 * {@link DraftProcessDocument}/{@link DraftProcessDocumentRepository}), theo yêu cầu người dùng
 * "không lưu chung với duthao_documents".
 */
@Entity
@Table(name = "duthao_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DuThaoDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public DuThaoDocument(
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

    public DuThaoDocument update(
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
    public DuThaoDocument replaceDynamicFields(String dynamicFieldsJson) {
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
