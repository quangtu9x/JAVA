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
 * Port module "Hồ sơ lưu trữ" (hosoluutru) — {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md}
 * mục 8.1, {@code docs/analysis/17-checklist-port-hosoluutru-domino-sang-java.md}. Bảng vật lý ĐỘC LẬP
 * {@code hosoluutru_documents}, đúng khuôn {@link com.td.domain.hosocv.HoSoCVDocument} (BL-101 Dynamic
 * Data Engine — field nghiệp vụ biến thiên trong {@code dynamicFieldsJson} JSONB, không entity riêng
 * cho từng field).
 *
 * <p>Bảng này CHỈ chứa hồ sơ lưu trữ chính (form Domino {@code hoso}/{@code hoso_chuaduyet},
 * {@code documentType="hosoluutru"}). Cột {@code status} lưu trực tiếp giá trị {@code form} rút gọn
 * ({@code "hoso_chuaduyet"} = chờ tiếp nhận, {@code "hoso"} = đã tiếp nhận) — giữ nguyên literal Domino
 * để khớp nghĩa "Chờ tiếp nhận"/"Đã tiếp nhận" trong {@code tableconfig.jsx} thay vì bịa mã trạng thái
 * mới. Văn bản con ({@code vanban}), phiếu yêu cầu mượn/trả ({@code phieuyeucau}), danh mục dùng chung
 * ({@code kyhieuhoso}/{@code chatluong}/{@code loaihinhtailieu}) và vết xử lý LTLS nằm ở 4 bảng riêng
 * ({@link HoSoLuuTruVanBanDocument}, {@link HoSoLuuTruPhieuYeuCauDocument},
 * {@link HoSoLuuTruDanhMucDocument}, {@link HoSoLuuTruProcessDocument}) — không kế thừa
 * {@code HoSoCVDocument} (JPA single-table inheritance sẽ gộp bảng, ngược mục đích "độc lập dữ liệu").
 *
 * <p>Field động chính (trong {@code dynamicFieldsJson}, xem tài liệu 16 mục 6.1): {@code document_profiles_id}
 * (liên kết ngược hồ sơ {@code hosocv} gốc), {@code identifier} (khoá phân quyền theo cấp đơn vị),
 * {@code category}/{@code category_id}, {@code chuyenluutru} ({@code "0"}/{@code "1"} — đã lưu trữ chính
 * thức nội bộ), {@code chuyenluutrutinh} ({@code ""}/{@code "0"}/{@code "2"} — trạng thái LTLS, xem
 * {@link com.td.application.hosoluutru.HoSoLuuTruLifecycleStatus}), {@code user_quyenxem}/
 * {@code user_dangkymuon} (danh sách username, phân tách {@code ;}).
 */
@Entity
@Table(name = "hosoluutru_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoSoLuuTruDocument extends AuditableEntity<UUID> implements IAggregateRoot {

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

    public HoSoLuuTruDocument(
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

    public HoSoLuuTruDocument update(
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

    public HoSoLuuTruDocument replaceDynamicFields(String dynamicFieldsJson) {
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
