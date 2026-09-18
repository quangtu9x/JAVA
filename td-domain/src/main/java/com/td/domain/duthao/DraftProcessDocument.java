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
 * DT-007 (lịch sử xử lý dự thảo), yêu cầu người dùng 25/08/2026 ("tách draft_process sang bảng
 * riêng, bảng vật lý như {@code duthao_documents}, không lưu chung với duthao_documents"): entity
 * cho bảng vật lý ĐỘC LẬP {@code draft_process_documents} (migration
 * {@code V1.0.30__Create_Draft_Process_Documents_Table.sql}) — TRƯỚC ĐÂY {@code draft_process}
 * lưu CHUNG {@code duthao_documents} với document chính (xem V1.0.28), giờ tách hẳn để 2 bảng
 * độc lập dữ liệu/lifecycle hoàn toàn, đúng tiền lệ đã làm với {@code documents}→
 * {@code duthao_documents}/{@code vbdi_documents}.
 *
 * <p>Cấu trúc CỐ Ý trùng {@link DuThaoDocument} (cùng kiến trúc BL-101 Dynamic Data Engine —
 * field biến thiên trong {@code dynamicFieldsJson} JSONB). Không kế thừa vì JPA single-table
 * inheritance sẽ gộp bảng (ngược mục đích "bảng vật lý riêng") — trùng lặp có chủ ý, xem
 * javadoc {@link DuThaoDocument}.</p>
 *
 * <p><b>Mỗi dòng = 1 vết xử lý</b> (append-only, không sửa/xoá): ghi bởi
 * {@code XuLyDuThaoUseCase#createDraftProcessRecord} qua {@code AddDraftProcessDocumentUseCase}
 * cho MỖI action workflow (chuyenduyet/chuyenkyduyet/kyduyet/kynhay/tralai/thuhoi/
 * chuyennguoikhac/chuyenphoihop/ketthuc) VÀ bởi {@code XinYKienPhoiHopUseCase} cho vết
 * "chuyển/cho ý kiến phối hợp" — đúng Domino {@code DraftProcesses.java} điểm 1 mục 4.2 (tài liệu
 * phân tích 04); database {@code duthao_res} trong Domino chỉ là TÊN DB chứa các document
 * {@code form="draft_process"} nên ở Java KHÔNG có khái niệm loại document {@code duthao_res}
 * riêng (đã sửa 25/08/2026). Field động chính: {@code parentid} (id dự thảo cha), {@code action},
 * {@code status}, {@code oldstatus}, {@code receiver}, {@code receiver_username}, {@code opinion},
 * {@code creator}. Cột tĩnh {@code status} = trạng thái đích của action (khác bảng duthao — ở đây
 * chỉ mang ý nghĩa ghi vết).</p>
 *
 * <p>Nhánh KHÔNG tạo dòng: {@code danhgiaduthao} (Domino early-return nhánh "99") và luồng FE
 * đính kèm file khi Lưu (vẫn gọi Domino — xem javadoc {@code XuLyDuThaoUseCase}).</p>
 */
@Entity
@Table(name = "draft_process_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftProcessDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public DraftProcessDocument(
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

    // Append-only: KHÔNG cung cấp update() như DuThaoDocument - vết xử lý không bao giờ được sửa
    // (sai phải ghi dòng mới, đúng chất audit log của BL-103).

    public DraftProcessDocument replaceDynamicFields(String dynamicFieldsJson) {
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