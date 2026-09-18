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
 * Phiếu trình thẩm định (form Domino {@code submission_dossier}, database Domino
 * {@code phieutrinhtd.nsf}) — {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md}.
 *
 * <p>Bảng vật lý ĐỘC LẬP {@code phieutrinhtd_documents} (migration V1.0.53), đúng tiền lệ
 * {@code VbcddhDocument} (BL-250): entity CỐ Ý gần như trùng {@code BusinessDocument}/{@code
 * VbcddhDocument} (title/documentType/status/content/tagsJson/attributesJson/metadataJson/
 * dynamicFieldsJson/versionNo) — cùng kiến trúc BL-101 (Dynamic Data Engine). KHÔNG kế thừa
 * {@code BusinessDocument} (JPA single-table inheritance sẽ gộp bảng — ngược ý muốn "độc lập").
 *
 * <p>{@code status} lưu mã trạng thái Domino thật (xem tài liệu 20 mục 2.1): {@code "0"} đang soạn
 * thảo, {@code "1"} trình tham khảo, {@code "2"} trình thông qua, {@code "3"} không thông qua,
 * {@code "4"} đã thông qua, {@code "5"} trình duyệt cấp 1, {@code "6"} không duyệt cấp 1, {@code "7"}
 * đã duyệt cấp 1, {@code "25"/"26"/"27"} tương đương cấp 2 (route/UI FE gốc hiện đang tắt, nhưng field/
 * mã trạng thái vẫn tồn tại trong data model — port đầy đủ, không cutover FE cho nhánh này).
 *
 * <p>Field nghiệp vụ biến thiên (code/title_rtext/description/creator/user_creator/lanhdao1(_obj)/
 * lanhdao2(_obj)/lanhdaovanphong(_obj)/coordinator(_obj)/nguoithamkhaohide/nguoidathamkhaohide/
 * signerhide/documentin_id/document_profiles_id/unit_publish(_id)/storaged/trinh&lt;N&gt;_date/
 * ykientrinh&lt;N&gt;...) nằm trong {@link #dynamicFieldsJson}, KHÔNG có cột riêng — xem tài liệu 20
 * mục 2.2.
 */
@Entity
@Table(name = "phieutrinhtd_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhieuTrinhTdDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String DOCUMENT_TYPE = "phieutrinhtd";

    public static final String STATUS_DANG_SOAN_THAO = "0";
    public static final String STATUS_TRINH_THAM_KHAO = "1";
    public static final String STATUS_TRINH_THONG_QUA = "2";
    public static final String STATUS_KHONG_THONG_QUA = "3";
    public static final String STATUS_DA_THONG_QUA = "4";
    public static final String STATUS_TRINH_DUYET_CAP1 = "5";
    public static final String STATUS_KHONG_DUYET_CAP1 = "6";
    public static final String STATUS_DA_DUYET_CAP1 = "7";
    public static final String STATUS_TRINH_DUYET_CAP2 = "25";
    public static final String STATUS_KHONG_DUYET_CAP2 = "26";
    public static final String STATUS_DA_DUYET_CAP2 = "27";

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

    public PhieuTrinhTdDocument(String title, String status, String content) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.documentType = DOCUMENT_TYPE;
        this.status = status;
        this.content = content;
    }

    public PhieuTrinhTdDocument update(String title, String content) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        this.versionNo += 1L;
        return this;
    }

    public PhieuTrinhTdDocument changeStatus(String newStatus) {
        this.status = newStatus;
        this.versionNo += 1L;
        return this;
    }

    public PhieuTrinhTdDocument replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = normalizeObject(dynamicFieldsJson);
        return this;
    }

    private String normalizeObject(String value) {
        return (value == null || value.isBlank()) ? "{}" : value;
    }
}
