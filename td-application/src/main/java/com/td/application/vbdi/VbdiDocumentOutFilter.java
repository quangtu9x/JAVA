package com.td.application.vbdi;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 29/08/2026 (port "các tab trong vbdi/xuly/cq_toanboso, chức năng chọn sổ và tìm kiếm" — yêu cầu
 * người dùng): tổ hợp điều kiện dùng chung cho 8 view "Sổ văn bản đi" đọc trực tiếp từ
 * {@code docs/views_domino/view_vanbandi.dxl} — {@code vwCreator_DocumentOut_TextBook} (Toàn bộ,
 * đã port trước — {@link VbdiDocumentRepository#findIssuedByPublishOrCreatorIdentifier}),
 * {@code vwCreator_DocumentOut_ToDay}/{@code homqua}/{@code trongtuan}/{@code trongthang} (4 tab
 * theo ngày), {@code vbhanhchinh_snv}/{@code vbncc_snv}/{@code vbbhtn_snv}/{@code vbgpld_snv} (4 tab
 * riêng cơ quan H05.13), và {@code vwCreator_DocumentOut_ByTextBook} (kết hợp "chọn sổ" + tìm kiếm).
 * Cùng tinh thần {@link com.td.application.documents.VbdenOrgViewFilter} (đã dùng cho 11 view vbden
 * N03/N13) — 1 filter object thay vì N method repository riêng biệt, vì tất cả 8 view CÙNG 1 predicate
 * gốc (status=ISSUED + khoá identifier), chỉ khác NHAU ở điều kiện phụ.
 *
 * <p>{@code extraPredicateSql} CHỈ được gán từ hằng số Java nội bộ cố định (4 công thức SNV, xem
 * {@code ViewQueryService#SNV_FILTER_*}) — KHÔNG BAO GIỜ được ghép từ input client, tránh SQL
 * injection (khác {@link #keyword}/{@link #keywordColumns} luôn đi qua bind parameter).</p>
 */
@Data
@Builder(toBuilder = true)
public class VbdiDocumentOutFilter {

    /** Khoảng ngày cho {@code promulgation_date} (field động, lưu "DD/MM/YYYY") — null 1 đầu = không
     * giới hạn đầu đó. Dùng cho 3 tab {@code today}/{@code homqua}/{@code trongtuan}/{@code trongthang}
     * — TÍNH ĐỘNG tại thời điểm query (LocalDate.now()) thay vì hard-code như formula Domino tự sinh
     * lại định kỳ (xem javadoc {@code VbdenOrgViewFilter#minCreatedOn} — cùng quyết định đã áp dụng
     * cho vbden, tái dùng nguyên trạng cho vbdi). */
    private LocalDate promulgationDateFrom;
    private LocalDate promulgationDateTo;

    /** Slice 7-style tìm kiếm tự do — OR trên các field động trong {@link #keywordColumns}
     * (contains, không phân biệt hoa/thường). Rỗng/null ở keyword HOẶC keywordColumns = không lọc. */
    private String keyword;
    private List<String> keywordColumns;

    /** Công thức SNV cố định (xem trên) — nối thẳng vào SQL, KHÔNG bind parameter vì luôn là hằng số
     * nội bộ, không bao giờ chứa giá trị client gửi. */
    private String extraPredicateSql;

    /** {@code document_register_id} (field động) phải khớp — dùng cho
     * {@code vwCreator_DocumentOut_ByTextBook} ("chọn sổ"), so khớp lowercase+trim đúng công thức cột
     * $7 Domino ({@code @LowerCase(@Trim(document_register_id))}). Chấp nhận CẢ UNID Domino (32 ký
     * tự hex) LẪN UUID Java (sổ tạo qua BL-116 {@code document_registers}) — chỉ so khớp CHUỖI, không
     * quan tâm định dạng, vì {@code vbdi_documents.dynamic_fields->>'document_register_id'} lưu
     * nguyên giá trị FE gửi lúc "Vào sổ" bất kể nguồn sổ nào. */
    private String documentRegisterId;

    /**
     * 29/08/2026 (port "vbdi/xuly/baocaochuaxem" — dxl {@code vwDocumentOut_BaoCaoChuaXem}, dòng
     * ~1093-1123): field động DẠNG DANH SÁCH username nối {@code ";"} mà {@code member} (tham số
     * truyền cho {@code findByAnyFieldContainsMember}) KHÔNG ĐƯỢC nằm trong (Domino gốc:
     * {@code !@Contains(@LowerCase(log_seen_document); @Text(@V3UserName))} — loại văn bản người
     * đang xem ĐÃ xem rồi). Dùng LẠI CHÍNH tham số {@code member} của method (không cần field riêng
     * trong filter cho giá trị so khớp) — chỉ cần TÊN field cần loại trừ.
     */
    private String excludeMemberFieldName;

    /**
     * 09/09/2026 (yêu cầu người dùng cutover route dashboard đang gọi Domino
     * {@code qlvb/tichhop/TimKiem?databasetype=vbdi} — bỏ qua Elasticsearch, "tìm kiếm nâng cao"
     * {@code CollapseVBdi.jsx}): field → giá trị PHẢI khớp CHÍNH XÁC 1 trong danh sách (qua
     * {@code dynamic_fields->>'field'}, EXACT match) — dùng cho field CHỌN TỪ DANH MỤC/tổ chức
     * (document_type/sector/lien_thong/send_agent/user_signer/draftor/unit_publish_identifier/
     * unit_in_charge_identifier). Nhiều field khác nhau trong map kết hợp AND — cùng ngữ nghĩa
     * {@code VbdenOrgViewFilter#includeFieldValues} (KHÁC vbden: "tìm kiếm nâng cao" vbdi KHÔNG có
     * dropdown trạng thái/status — FE luôn khoá cứng {@code status="2"} qua tham số {@code status}
     * riêng của {@code findByAnyFieldEqualsIdentifier}, không đi qua map này).
     */
    private Map<String, List<String>> includeFieldValues;

    /**
     * 09/09/2026 (cùng đợt): field → giá trị PHẢI khớp ÍT NHẤT MỘT trong danh sách, containment
     * JSON-array-HOẶC-scalar-equality (an toàn cho cả field lưu dạng mảng lẫn scalar) — dùng cho
     * field TỰ DO/text input (code_full/mc_thutuc) — cùng ngữ nghĩa
     * {@code VbdenOrgViewFilter#containsAnyFieldValues}.
     */
    private Map<String, List<String>> containsAnyFieldValues;
}
