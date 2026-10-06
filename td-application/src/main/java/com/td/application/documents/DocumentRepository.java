package com.td.application.documents;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.documents.BusinessDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DocumentRepository extends IRepository<BusinessDocument> {

    Page<BusinessDocument> search(SearchDocumentsRequest request, Pageable pageable);

    Page<BusinessDocument> searchDeleted(SearchDocumentsRequest request, Pageable pageable);

    void hardDelete(BusinessDocument entity);

    /**
     * BL-102 (View Query Engine): tương đương view {@code vwDocument_ChoXuLy} — văn bản đang chờ
     * xử lý bởi {@code username} (field động {@code user_process} chứa username, dạng JSON array).
     */
    Page<BusinessDocument> findPendingForUser(String documentType, String username, Pageable pageable);

    /**
     * Slice 7 (tìm kiếm/lọc) — biến thể {@link #findPendingForUser(String, String, Pageable)} có
     * thêm {@code keyword} (tìm trên {@code keywordColumns}, field động scalar) — {@code null}/rỗng
     * ở 1 trong 2 tham số này = không lọc, hành vi giống hệt bản không có keyword.
     */
    Page<BusinessDocument> findPendingForUser(
            String documentType, String username, String keyword, List<String> keywordColumns, Pageable pageable);

    /**
     * BL-210 (VBDen Core): tương đương view "đã xử lý" — văn bản đã được {@code username} xử lý
     * (field động {@code user_processed} chứa username, dạng JSON array).
     */
    Page<BusinessDocument> findProcessedForUser(String documentType, String username, Pageable pageable);

    /** Slice 7 — biến thể có {@code keyword}, xem {@link #findPendingForUser(String, String, String, List, Pageable)}. */
    Page<BusinessDocument> findProcessedForUser(
            String documentType, String username, String keyword, List<String> keywordColumns, Pageable pageable);

    /**
     * BL-210 (VBDen Core): tương đương view {@code QTXL_by_parentid} phía Domino — danh sách
     * document con (vd. bản ghi quá trình xử lý) theo field động {@code parentid} (UNID document
     * cha, dạng chuỗi đơn — khác {@code user_process}/{@code user_processed} vốn là JSON array).
     */
    Page<BusinessDocument> findByParentId(String documentType, String parentId, Pageable pageable);

    /**
     * BL-250 (VBCDDH Core): truy vấn tổng quát theo 1 field động dạng chuỗi đơn (không phải JSON
     * array) — dùng cho các field liên kết/khoá tra cứu tuỳ biến theo module (vd. {@code idvanban}
     * — liên kết nhiệm vụ con về văn bản chỉ đạo gốc, {@code donvithuchienhide} — định danh đơn vị
     * thực hiện). Khác {@link #findByParentId} ở chỗ tên field truyền vào linh hoạt thay vì cố định
     * {@code "parentid"}, tránh phải thêm 1 method riêng cho mỗi field khoá mới của mỗi module.
     */
    Page<BusinessDocument> findByFieldValue(String documentType, String fieldName, String fieldValue, Pageable pageable);

    /**
     * BL-220 (VBDi Core): tương đương view "chờ ban hành"/"đã ban hành" — lọc theo cột tĩnh
     * {@code status} (không phải field động JSONB) của {@code documentType} cho trước.
     */
    Page<BusinessDocument> findByStatus(String documentType, String status, Pageable pageable);

    /**
     * BL-210 (VBDen Core) — N03: tương đương các view "chờ vào sổ theo nguồn" của Domino
     * ({@code captrengiao}/{@code dennoibo}/{@code quamangnoibo}) — {@code value} (identifier đơn
     * vị) xuất hiện trong ÍT NHẤT MỘT trong các field JSON-array cho trước (OR containment), tham
     * số hoá theo tên field thay vì cố định 1 field như {@link #findPendingForUser}/
     * {@link #findProcessedForUser} — tránh phải thêm 1 method riêng cho mỗi tổ hợp field org-scoped
     * mới của module vbden (vd. {@code unit_process}/{@code unit_coordinate}/{@code unit_to_read}/
     * {@code contact_unit_identifier}).
     */
    Page<BusinessDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable);

    /** Slice 7 — biến thể có {@code keyword}, xem {@link #findPendingForUser(String, String, String, List, Pageable)}. */
    Page<BusinessDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            String keyword, List<String> keywordColumns, Pageable pageable);

    /**
     * 01/09/2026 ("tìm kiếm nâng cao" cho view cá nhân — {@code theodoi}/{@code datheodoi}): biến thể
     * có thêm {@code advancedFilter} ({@link VbdenOrgViewFilter}, CHỈ dùng {@code includeFieldValues}/
     * {@code excludeFieldValues}/{@code containsAnyFieldValues}/{@code minReceivedDate}/
     * {@code maxReceivedDate}/{@code keyword}/{@code keywordColumns} — {@code orgFieldNames}/
     * {@code orgValue}/{@code orgListFieldNames} BỊ BỎ QUA, khoá cá nhân chính đã có ở
     * {@code fieldNames}/{@code value} riêng). {@code null} = không lọc thêm, giữ NGUYÊN hành vi (kể
     * cả thứ tự sắp xếp {@code created_on ASC}) như 2 overload trên — KHÔNG đổi bằng
     * {@code findByVbdenOrgView} (mặc định sắp {@code last_modified_on DESC}, sẽ đổi thứ tự hiển thị
     * đã ship của 24 view cá nhân khác đang dùng {@code findByAnyFieldContains} nếu tái dùng nhầm).
     */
    Page<BusinessDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            VbdenOrgViewFilter advancedFilter, Pageable pageable);

    /**
     * N03/N13 (VBDen — hàng đợi/hạn xử lý theo tổ chức): truy vấn tổ hợp theo {@link VbdenOrgViewFilter}
     * — đọc trực tiếp từ {@code docs/views_domino/view_vanbanden.dxl} (export View Domino thật, có
     * $Formula), thay thế cách làm rời rạc ở Slice 2/3 ({@code findByFieldValueAndStatusIn}/
     * {@code findByFieldValueWithDeadlineInRange} — không đủ tổng quát cho tổ hợp field/status/ngày
     * thật của 11 view: {@code captrengiao}, {@code dennoibo}, {@code quamangnoibo}, {@code lienthong},
     * {@code dendientu}, {@code phaixuly_cuatochuc}, {@code quahan_cuatochuc}, {@code saptoihan_cuatochuc},
     * {@code denhan_cuatochuc}, {@code daxuly_quahan_cuatochuc}, {@code daxuly_dunghan_cuatochuc}).
     */
    Page<BusinessDocument> findByVbdenOrgView(String documentType, VbdenOrgViewFilter filter, Pageable pageable);

    /**
     * 06/10/2026 (bảng document_links - docs/architecture/document-links.md): danh sách văn bản theo
     * TẬP id (id lấy từ DocumentLinkService), phân trang + lọc từ khoá, mới tạo nhất trước. Rỗng nếu
     * {@code ids} rỗng.
     */
    Page<BusinessDocument> findByIds(
        String documentType, java.util.Collection<java.util.UUID> ids, String keyword, List<String> keywordColumns,
        Pageable pageable);
}
