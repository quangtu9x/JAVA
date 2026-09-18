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
 * Bảng vật lý ĐỘC LẬP {@code hosoluutru_process_documents} — vết xử lý audit-trail NỘI BỘ module
 * {@code hosoluutru}, hiện chỉ dùng cho "Chuyển lưu trữ lịch sử" (LTLS, {@code process_type=chuyenltls},
 * tương đương form Domino {@code quatrinhchuyenltls}), xem
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 8.1.
 *
 * <p><b>Quan trọng</b>: vết "tiếp nhận"/"từ chối tiếp nhận" hồ sơ (form Domino
 * {@code quatrinhchuyenluutru}) KHÔNG ghi vào bảng này — chúng ghi vào
 * {@code hosocv_process_documents} ĐÃ CÓ ({@code process_type=chuyenluutru}), để nối tiếp đúng 1 luồng
 * nhật ký với bản ghi log lúc {@code hosocv} tạo hồ sơ lưu trữ (đúng bằng chứng FE: cả 2 phía
 * {@code hosocv}/{@code hosoluutru} đều ghi log vào cùng {@code hosocv_res}, xem tài liệu 16 mục 8.1).
 * Bảng này chỉ tồn tại vì LTLS là nghiệp vụ THUẦN NỘI BỘ {@code hosoluutru}, không có phía {@code hosocv}
 * đối ứng.
 *
 * <p>Field động chính: {@code hoso_id} (id hồ sơ lưu trữ cha), {@code process_type} (hiện chỉ
 * {@code "chuyenltls"}), {@code action_type} ({@code "0"}=thu hồi, {@code "2"}=chuyển LTLS chờ duyệt),
 * {@code creator}/{@code user_creator}, {@code subject} (ý kiến), {@code create_date}.
 *
 * <p><b>Append-only</b> (giống {@link com.td.domain.hosocv.HoSoCVProcessDocument}): mỗi dòng = 1 vết xử
 * lý, không sửa/xoá.</p>
 */
@Entity
@Table(name = "hosoluutru_process_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoLuuTruProcessDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoLuuTruProcessDocument(
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

    // Append-only: KHONG cung cap update() - vet xu ly khong bao gio duoc sua.

    public HoSoLuuTruProcessDocument replaceDynamicFields(String dynamicFieldsJson) {
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
