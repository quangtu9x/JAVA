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
 * Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới ... như các module đã thực
 * hiện"): entity cho bảng vật lý ĐỘC LẬP {@code hosocv_process_documents} (migration
 * {@code V1.0.44__Create_HoSoCV_Process_Documents_Table.sql}) — vết xử lý/lịch sử của 1 hồ sơ
 * công việc, đúng tiền lệ {@link com.td.domain.duthao.DraftProcessDocument}/
 * {@code draft_process_documents}.
 *
 * <p>Gộp 3 nhu cầu audit-trail thật của Domino ({@code hosocv_res.nsf}) vào 1 bảng, phân biệt bằng
 * field động {@code process_type} (tránh 3 bảng lẻ chỉ khác nhau vài field):
 * <ul>
 *   <li>{@code trinhduyet}/{@code duyet} — tương đương {@code quatrinhduyet} (lịch sử trình/duyệt
 *       lãnh đạo, field {@code trinhlanhdaoduyet}).</li>
 *   <li>{@code chuyenluutru} — tương đương {@code storag_log} (lịch sử chuyển lưu trữ, field
 *       {@code storaged}, view Domino {@code vwQuaTrinhChuyenLuuTru}).</li>
 *   <li>{@code phoihop}/{@code gopy} — tương đương {@code document_coordinated_opinion}/
 *       {@code document_feedback} (ý kiến phối hợp/góp ý).</li>
 * </ul>
 *
 * <p>Field động chính (trong {@code dynamicFieldsJson}): {@code hosocv_id} (id hồ sơ cha),
 * {@code process_type}, {@code action}, {@code old_value}, {@code new_value}, {@code note}/
 * {@code opinion}, {@code creator} (người thực hiện). Cấu trúc CỐ Ý trùng {@code HoSoCVDocument}
 * (cùng kiến trúc BL-101) nhưng KHÔNG kế thừa (JPA single-table inheritance sẽ gộp bảng, ngược mục
 * đích "bảng vật lý riêng").</p>
 *
 * <p><b>Append-only</b> (giống {@code DraftProcessDocument}): mỗi dòng = 1 vết xử lý, không sửa/xoá
 * — sai phải ghi dòng mới, đúng chất audit log BL-103.</p>
 */
@Entity
@Table(name = "hosocv_process_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoCVProcessDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoCVProcessDocument(
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

    // Append-only: KHONG cung cap update() nhu HoSoCVDocument - vet xu ly khong bao gio duoc sua.

    public HoSoCVProcessDocument replaceDynamicFields(String dynamicFieldsJson) {
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
