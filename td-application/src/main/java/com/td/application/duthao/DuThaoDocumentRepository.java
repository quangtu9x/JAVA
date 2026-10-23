package com.td.application.duthao;

import com.td.application.common.interfaces.IRepository;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.domain.duthao.DuThaoDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026: port RIÊNG cho bảng vật lý ĐỘC LẬP
 * {@code duthao_documents} (xem javadoc {@link DuThaoDocument}) — KHÔNG phải
 * {@code com.td.application.documents.DocumentRepository} (bảng {@code documents} dùng chung cho
 * mọi module khác: vbden/vbdi/hosocv/...).
 *
 * <p>Chỉ khai báo ĐÚNG method thực sự được dùng bởi các use case của cụm dự thảo (Tao/Luu/
 * GetDetail/XuLy/ChuyenThanhVanBanDi/ThuHoi/XinYKienPhoiHop + 3 case trong {@code ViewQueryService})
 * — không copy nguyên văn toàn bộ surface của {@code DocumentRepository} (nhiều method ở đó —
 * {@code findPendingForUser}/{@code findByParentId}/{@code findByFieldValue}/{@code search}/...
 * — KHÔNG có evidence được cụm dự thảo dùng, thêm vào sẽ là suy diễn không cần thiết).
 */
public interface DuThaoDocumentRepository extends IRepository<DuThaoDocument> {

    /**
     * Cùng ngữ nghĩa {@code DocumentRepository#findByAnyFieldContains} — OR containment (JSON-array
     * hoặc scalar-equality, xem {@code PostgresDuThaoDocumentRepository}) trên nhiều field cho 1
     * giá trị (vd. {@code user_creator}).
     */
    Page<DuThaoDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable);

    Page<DuThaoDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            String keyword, List<String> keywordColumns, Pageable pageable);

    /** Cùng ngữ nghĩa {@code DocumentRepository#findByVbdenOrgView}. */
    Page<DuThaoDocument> findByVbdenOrgView(String documentType, VbdenOrgViewFilter filter, Pageable pageable);

    /**
     * DT-002 (26/08/2026) — {@code vwUser_Coordinated}: OR-group KHÔNG biểu diễn được bằng
     * {@link VbdenOrgViewFilter} (1 nhóm AND) — selection thật là {@code (fieldWithoutStatus != "") OR
     * (fieldWithStatus != "" AND status IN doneStatuses)}, tức 2 field khác nhau với 2 điều kiện
     * status khác nhau trên CÙNG 1 request. {@code value} khớp theo containment/scalar-equality
     * (cùng ngữ nghĩa {@code orgFieldNames}, field lưu JSON array — xem {@code XinYKienPhoiHopUseCase}).
     */
    Page<DuThaoDocument> findByFieldOrFieldWithStatus(
            String documentType, String fieldWithoutStatus, String fieldWithStatus,
            List<String> doneStatuses, String value, String keyword, List<String> keywordColumns,
            VbdenOrgViewFilter search, Pageable pageable);

    /**
     * 01/10/2026 — view gộp "Văn thư trả lại" (Java-only, xem {@code ViewQueryService#VIEW_DUTHAO_VANTHU_TRALAI}):
     * dự thảo văn thư trả lại CHO CHÍNH {@code username} ({@code return_document_out_for_user}), còn
     * đúng trạng thái của lần trả lại đó — mỗi cặp ({@code return_document_out_for} → {@code status})
     * trong {@code statusByReturnFor} là 1 nhánh OR (vd "1"→"1", "2"→"2", "3"→"2").
     */
    Page<DuThaoDocument> findReturnedByClerkForUser(
            String documentType, Map<String, String> statusByReturnFor, String username,
            String keyword, List<String> keywordColumns, VbdenOrgViewFilter search, Pageable pageable);

    /**
     * 22/10/2026 — {@code vwChoDuyetKyBanHanh}/{@code vwChoDuyetKyBanHanhMC}: mỗi cặp (field → các status)
     * trong {@code statusesByField} là 1 nhánh OR "{@code value} thuộc field VÀ status thuộc danh sách"
     * (vd {@code user_signer}→{1,3}, {@code user_other_process_handler}→{9}). {@code requireEmptyField}/
     * {@code requireNonEmptyField} (field động, có thể null) áp chung cho mọi nhánh.
     */
    Page<DuThaoDocument> findByFieldStatusBranches(
            String documentType, Map<String, List<String>> statusesByField, String value,
            String requireEmptyField, String requireNonEmptyField,
            String keyword, List<String> keywordColumns, VbdenOrgViewFilter search, Pageable pageable);

    /**
     * 06/10/2026 (bảng document_links - docs/architecture/document-links.md): danh sách văn bản theo
     * TẬP id (id lấy từ DocumentLinkService), phân trang + lọc từ khoá, mới tạo nhất trước. Rỗng nếu
     * {@code ids} rỗng.
     */
    Page<DuThaoDocument> findByIds(
        String documentType, java.util.Collection<java.util.UUID> ids, String keyword, List<String> keywordColumns,
        Pageable pageable);
}
