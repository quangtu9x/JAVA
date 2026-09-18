package com.td.application.hosoluutru;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Port module "Hồ sơ lưu trữ" (hosoluutru) —
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 8.1/8.2. Port RIÊNG cho
 * bảng vật lý ĐỘC LẬP {@code hosoluutru_documents} (xem javadoc {@link HoSoLuuTruDocument}).
 *
 * <p>Chỉ khai báo 1 method truy vấn tổ hợp {@link #findByCriteria} thay vì nhiều method chuyên biệt
 * như {@code HoSoCVDocumentRepository} — module {@code hosoluutru} không có OR-group phức tạp
 * (`_chua`/`_da`) như {@code vwUser_Coordinated_Status`}, nên 1 method tổ hợp (match-any-field OR +
 * field-value IN) đủ phủ toàn bộ ~15 view đã chốt port (checklist 17 Phần 1).
 */
public interface HoSoLuuTruDocumentRepository extends IRepository<HoSoLuuTruDocument> {

    /**
     * Tổ hợp 2 điều kiện, cả 2 đều tuỳ chọn (truyền {@code null}/rỗng để bỏ qua):
     * <ul>
     *   <li>{@code matchAnyFieldNames}/{@code matchValue} — OR containment/scalar-equality trên field
     *       động (đúng ngữ nghĩa {@code DocumentRepository#findByAnyFieldContains}), dùng cho khoá cá
     *       nhân ({@code identifier}).</li>
     *   <li>{@code fieldValueIn} — AND, mỗi field phải có giá trị nằm trong danh sách cho phép; key
     *       {@code "status"} so khớp cột tĩnh {@code status} (KHÔNG phải {@code dynamic_fields}, đúng
     *       bài học {@code PostgresHoSoCVDocumentRepository#findByFieldValueDueDate}), key khác so
     *       khớp {@code dynamic_fields->>'key'}. Giá trị rỗng {@code ""} trong danh sách khớp cả field
     *       chưa từng được set (NULL/thiếu key).</li>
     * </ul>
     */
    Page<HoSoLuuTruDocument> findByCriteria(
            String documentType,
            List<String> matchAnyFieldNames, String matchValue,
            Map<String, List<String>> fieldValueIn,
            Pageable pageable);

    /** {@code vwTrash} — hồ sơ lưu trữ đã xoá mềm ({@code deleted_on IS NOT NULL}). */
    Page<HoSoLuuTruDocument> findDeleted(String documentType, Pageable pageable);
}
