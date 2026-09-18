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
 * BL-210 (VBDen Core) — DoD (4), yêu cầu người dùng 05/09/2026 ("trong văn bản đến các chức năng
 * liên quan tới vbden_res (quá trình xử lý...) tách thành bảng riêng cho tôi... không lưu cùng bảng
 * chính như hiện tại"): entity cho bảng vật lý ĐỘC LẬP {@code vbden_res_documents} (migration
 * {@code V1.0.42__Create_Vbden_Res_Documents_Table.sql}) — TRƯỚC ĐÂY {@code vbden_res} lưu CHUNG
 * {@code documents} (bảng dùng chung của văn bản đến gốc {@code document_type='vbden'} VÀ mọi
 * module khác), giờ tách hẳn để 2 bảng độc lập dữ liệu/lifecycle hoàn toàn — đúng tiền lệ
 * {@code duthao_documents}→{@code draft_process_documents} (V1.0.28/V1.0.30). Văn bản đến CHÍNH
 * ({@code document_type='vbden'}) KHÔNG bị đụng tới, vẫn ở lại {@code documents}.
 *
 * <p>Cấu trúc CỐ Ý trùng {@link com.td.domain.documents.BusinessDocument}/
 * {@link com.td.domain.duthao.DraftProcessDocument} (cùng kiến trúc BL-101 Dynamic Data Engine —
 * field biến thiên trong {@code dynamicFieldsJson} JSONB). Không kế thừa vì JPA single-table
 * inheritance sẽ gộp bảng (ngược mục đích "bảng vật lý riêng") — trùng lặp có chủ ý, cùng lý do đã
 * ghi ở javadoc {@code DraftProcessDocument}/{@code XinYKienDocument}.</p>
 *
 * <p><b>Mỗi dòng = 1 vết xử lý</b> (APPEND-ONLY THEO MẶC ĐỊNH): ghi bởi {@code AddVbdenResDocumentUseCase}
 * cho MỖI hành động {@code ChuyenXuLyVbdenUseCase}/{@code TaoDuThaoVBdenUseCase}/
 * {@code KetThucXuLyUseCase}/{@code CapNhatKetQuaXuLyUseCase}/{@code XuLyLaiUseCase}/
 * {@code ThuHoiVanBanUseCase}/{@code LayLaiVanBanUseCase}. <b>NGOẠI LỆ (15/09/2026)</b>:
 * {@code SuaYKienVbdenUseCase} ("Sửa ý kiến", {@code SuaYkien.jsx}) đọc lại 1 bản ghi hiện có (qua
 * {@code VbdenResDocumentRepository#findById} MỚI) và SỬA TRỰC TIẾP field {@code opinion} — đúng
 * nghiệp vụ Domino gốc (đối chiếu trực tiếp {@code CapNhatVanBanDen.java}, action
 * {@code suanoidung}). Đây là use case DUY NHẤT được phép sửa 1 bản ghi đã tồn tại — mọi use case
 * khác vẫn PHẢI tạo bản ghi mới, không tái sử dụng pattern này để sửa field khác. Field động chính:
 * {@code parentid} (id văn bản đến
 * cha, VẪN ở bảng {@code documents}), {@code action}, {@code opinion}, {@code creator},
 * {@code position}, {@code identifier}, {@code nguoi_thuc_hien} (tên field Java THẬT — khác
 * {@code user_creator} của Domino gốc, xem {@code LichSuXuLyUseCase}), {@code user_process_obj}/
 * {@code user_coordinate_obj}/{@code user_to_read_obj}/{@code user_assigner_obj} (snapshot hiển thị),
 * {@code res_obj} (tham chiếu văn bản dự thảo/trả lời).</p>
 */
@Entity
@Table(name = "vbden_res_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbdenResDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public VbdenResDocument(
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

    // Append-only: KHONG cung cap update() nhu BusinessDocument - vet xu ly khong bao gio duoc sua
    // (sai phai ghi dong moi), dung khuon DraftProcessDocument.

    public VbdenResDocument replaceDynamicFields(String dynamicFieldsJson) {
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
