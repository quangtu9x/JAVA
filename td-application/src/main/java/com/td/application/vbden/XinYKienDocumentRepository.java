package com.td.application.vbden;

import com.td.application.common.interfaces.IRepository;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.domain.vbden.XinYKienDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * N08 ("cho ý kiến"), yêu cầu người dùng 31/08/2026 ("tạo bảng riêng"): port RIÊNG cho bảng vật lý
 * ĐỘC LẬP {@code vbden_xinykien_documents} (xem javadoc {@link XinYKienDocument}) — KHÔNG phải
 * {@code com.td.application.documents.DocumentRepository} (bảng {@code documents} dùng chung, nơi
 * vbden GỐC vẫn đang lưu).
 *
 * <p>Chỉ khai báo ĐÚNG method thực sự cần cho cụm xin ý kiến (14 view {@code ViewQueryService} +
 * {@code GetVbdenDetailUseCase} nhánh xinykien + {@code ChoYKienXinYKienUseCase} +
 * {@code GuiXinYKienUseCase}) — đúng tinh thần {@code DuThaoDocumentRepository} (không copy nguyên
 * văn toàn bộ surface {@code DocumentRepository}).</p>
 */
public interface XinYKienDocumentRepository extends IRepository<XinYKienDocument> {

    /**
     * Cùng ngữ nghĩa {@code DocumentRepository#findByAnyFieldContains} — OR containment (JSON-array
     * hoặc scalar-equality) trên nhiều field cho 1 giá trị. Dùng cho việc tìm document xin ý kiến đã
     * có sẵn theo nguồn ({@code id_documentout} — xem {@code GuiXinYKienUseCase}), field scalar-text.
     */
    Page<XinYKienDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable);

    Page<XinYKienDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            String keyword, List<String> keywordColumns, Pageable pageable);

    /** Cùng ngữ nghĩa {@code DocumentRepository#findByVbdenOrgView} — dùng cho 14 view N08. */
    Page<XinYKienDocument> findByVbdenOrgView(String documentType, VbdenOrgViewFilter filter, Pageable pageable);
}
