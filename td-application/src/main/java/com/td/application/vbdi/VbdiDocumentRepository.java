package com.td.application.vbdi;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.vbdi.VbdiDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung): port RIÊNG cho bảng
 * vật lý ĐỘC LẬP {@code vbdi_documents} (xem javadoc {@link VbdiDocument}) — KHÔNG phải
 * {@code com.td.application.documents.DocumentRepository} (bảng {@code documents} dùng chung cho mọi
 * module khác: vbden/hosocv/...).
 *
 * <p>Chỉ khai báo ĐÚNG method thực sự được dùng bởi các use case của cụm vbdi (SoanThao/TrinhKy/
 * BanHanh/KySo/GuiEmail + 2 case trong {@code ViewQueryService}) — không copy nguyên văn toàn bộ
 * surface của {@code DocumentRepository}: chỉ cần {@code findByStatus} (lọc theo cột tĩnh cho
 * {@code vwvandi_chobanhanh}/{@code vwvandi_dabanhanh}), còn lại dùng {@code IRepository} (findById/
 * save/delete).
 */
public interface VbdiDocumentRepository extends IRepository<VbdiDocument> {

    /**
     * Cùng ngữ nghĩa {@code DocumentRepository#findByStatus} — lọc theo cột tĩnh {@code status}
     * (không phải field động JSONB) của {@code documentType} cho trước — dùng cho 2 view
     * {@code vwvandi_chobanhanh}/{@code vwvandi_dabanhanh} (BL-220 DoD 4).
     */
    // 01/10/2026: + filter (keyword/keywordColumns) - "tim trong danh sach" truoc day bi bo qua.
    Page<VbdiDocument> findByStatus(String documentType, String status, VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 27/08/2026 (port route FE "vbdi/xuly/chocapkyso", view Domino thật {@code vwCreator_Drafting}
     * — "Văn bản đi chờ vào sổ", dxl {@code view_vanbandi.dxl} dòng ~276-306): lọc theo {@code status}
     * (cột tĩnh) VÀ field động {@code mc_madv} rỗng/vắng mặt VÀ khoá theo {@code identifier} khớp
     * field động {@code unit_publish_identifier} HOẶC {@code unit_publish_identifier_other} — đúng
     * key formula Domino ({@code unit_publish_identifier + unit_publish_identifier_other}, exploded).
     * Method riêng (không tái dùng {@link #findByStatus}) vì cần thêm điều kiện field động, khác hẳn
     * predicate chỉ-cột-tĩnh của method đó.
     */
    // 01/10/2026: + filter (keyword/keywordColumns) - route chocapkyso/chobanhanh truoc day bo qua tu khoa.
    Page<VbdiDocument> findWaitingForRegistrationByIdentifier(
        String documentType, String status, String identifier, VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 28/08/2026 (port route FE "vbdi/xuly/cq_toanboso" + 3 route anh em cùng dom_view_name
     * "vwCreator_DocumentOut_TextBook" — "Sổ văn bản đi", dxl {@code view_vanbandi.dxl} dòng
     * ~1732-1778): lọc theo {@code status} (cột tĩnh, ISSUED = Domino {@code status="2"} — đã ban
     * hành) VÀ khoá theo {@code identifier} khớp ĐÚNG key formula categorized 2 thành phần của
     * Domino ({@code unit_publish_identifier + @If(unit_publish_identifier_other != "";
     * ";"+unit_publish_identifier_other; ";"+unit_creator_identifier)}, exploded) — KHÁC
     * {@link #findWaitingForRegistrationByIdentifier} (chỉ OR phẳng 2 field, vì formula gốc của
     * view đó không có nhánh fallback unit_creator_identifier). Không tái dùng method đó vì @If ở
     * đây rẽ nhánh vào field thứ 3 (unit_creator_identifier) CHỈ KHI unit_publish_identifier_other
     * rỗng — OR phẳng cả 3 field sẽ sai (match nhầm theo unit_creator_identifier ngay cả khi
     * unit_publish_identifier_other đã có giá trị khác identifier đang lọc).
     */
    Page<VbdiDocument> findIssuedByPublishOrCreatorIdentifier(
        String documentType, String status, String identifier, VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 29/08/2026 (port 4 tab theo ngày {@code vwCreator_DocumentOut_ToDay}/{@code homqua}/
     * {@code trongtuan}/{@code trongthang} + 4 tab riêng cơ quan H05.13 {@code vbhanhchinh_snv}/
     * {@code vbncc_snv}/{@code vbbhtn_snv}/{@code vbgpld_snv}, dxl {@code view_vanbandi.dxl} — cả 8
     * view CÙNG 1 công thức khoá {@code user_creator + ";" + unit_creator_identifier + ";" +
     * unit_publish_identifier} (@Explode, OR PHẲNG 3 field) — KHÁC hẳn công thức @If-fallback của
     * {@link #findIssuedByPublishOrCreatorIdentifier} (view {@code vwCreator_DocumentOut_TextBook}
     * "Toàn bộ" dùng công thức RẼ NHÁNH khác, xem javadoc ở đó) — 2 method TÁCH RIÊNG vì đúng 2 công
     * thức Domino khác nhau, không phải cùng 1 thuật toán viết lại 2 lần.
     */
    Page<VbdiDocument> findIssuedByCreatorOrUnitIdentifier(
        String documentType, String status, String identifier, VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 28/08/2026 (yêu cầu người dùng "khi lưu có kiểm trùng gọi tới view vwDocumentOut_kiemtrungvb,
     * port sang JAVA" — {@code ControllModal.jsx#kiemtrungvanban}, gọi trước khi lưu "Thêm mới"):
     * lọc {@code status} ISSUED (Domino {@code status="2"}) VÀ {@code dulieucu != "1"} (loại dữ liệu
     * cũ import, khớp Domino) VÀ khoá theo ĐÚNG key formula categorized của view gốc (dxl
     * {@code view_vanbandi.dxl} dòng ~2395-2402): {@code document_register_id + "|" + document_number
     * [+ "|" + number_other nếu khác rỗng] + "|" + năm(promulgation_date)}, lowercase+trim. Trả về
     * TOÀN BỘ văn bản khớp key (không tự loại trừ theo id) — FE tự lọc loại chính văn bản đang sửa
     * (đúng hành vi client-side hiện có, {@code kiemtrungvanban}), giữ nguyên logic đó không đổi.
     */
    Page<VbdiDocument> findByKiemTrungKey(
        String documentType, String status, String compositeKey, Pageable pageable);

    /**
     * 29/08/2026 (port phần còn lại "vbdi/xuly/*" — yêu cầu người dùng "port toàn bộ chức năng ngoài
     * những phần đã làm ở trên"): TỔNG QUÁT hoá cho N view Domino CHỈ khác nhau ở SỐ LƯỢNG/TÊN field
     * OR phẳng (2-4 field) khoá theo {@code identifier} tổ chức/người tạo — mirror
     * {@link com.td.application.documents.VbdenOrgViewFilter#orgFieldNames} (đã dùng cho 11 view
     * vbden) thay vì viết N method gần-trùng-nhau như {@link #findIssuedByCreatorOrUnitIdentifier}
     * (giữ nguyên method đó vì đã có test/đã dùng — method NÀY chỉ cho các view MỚI, field-shape
     * không trùng bất kỳ method có sẵn nào). So khớp EQUALS (không phải list-membership — dùng
     * {@link #findByAnyFieldContainsMember} cho field kiểu danh sách username nối {@code ";"}) —
     * ĐÚNG bản chất field tổ chức/định danh vốn scalar 1-giá-trị trong các view này (xem javadoc từng
     * case gọi trong {@code ViewQueryService}).
     */
    Page<VbdiDocument> findByAnyFieldEqualsIdentifier(
        String documentType, String status, List<String> fieldNames, String identifier,
        VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 29/08/2026 (cùng đợt port trên): dùng cho nhóm view "hàng đợi cá nhân" Domino khoá theo field
     * động DẠNG DANH SÁCH username nối {@code ";"} ({@code usercoordinater}/{@code usercoordinater_send}/
     * {@code userxemdebiet_send}/{@code coordinator_hide_not_seen_rtext}/{@code userchuyenxuly}...) —
     * tương đương {@code @Explode(field;";")} rồi so khớp — KHÁC hẳn
     * {@link #findByAnyFieldEqualsIdentifier} (so khớp TOÀN CHUỖI, sai nếu field chứa nhiều giá trị).
     * {@code member} thường là username hiện tại — CHÚ Ý: FE các component
     * {@code phoihop}/{@code xemdebiet}/{@code xuly} (khác {@code chobanhanh}) gửi ĐÚNG chuỗi
     * {@code "$username$"} literal (không phải username thật) — Domino gốc coi đây là MACRO được
     * server tự thay bằng username phiên đăng nhập (xem {@code BE_THANHHOA/.../TDDatabaseConnection.java}
     * dòng ~1287/1592/1787: {@code if (key.equals("$username$")) vecKeys.add(user_token.username)}) —
     * {@code ViewQueryService} PHẢI tự resolve token này qua {@code CurrentUserProvider} (server-
     * authoritative, không tin giá trị client gửi dù có vẻ vô hại) TRƯỚC khi gọi method này, KHÔNG
     * truyền thẳng chuỗi "$username$" xuống SQL.
     */
    Page<VbdiDocument> findByAnyFieldContainsMember(
        String documentType, String status, List<String> fieldNames, String member,
        VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 29/08/2026 (đợt 2 "port toàn bộ vbdi/xuly/*" — {@code vwCreator_DocumentOut_SendNoResponse},
     * dxl {@code view_vanbandi.dxl} dòng ~1632-1680, route "chuaden"): điều kiện status THẬT là
     * {@code (status="5" | send_error="1") & send_agent="1"} — OR giữa CỘT TĨNH {@code status} và
     * 1 field ĐỘNG, không khớp shape {@code status} là 1 giá trị AND cố định của MỌI method khác
     * trong lớp này — method này KHÔNG tự thêm điều kiện status (giao hẳn cho
     * {@link VbdiDocumentOutFilter#extraPredicateSql} diễn đạt toàn bộ, xem
     * {@code ViewQueryService#queryVbdiSendNoResponse}), chỉ giữ khoá @If-fallback GIỐNG
     * {@link #findIssuedByPublishOrCreatorIdentifier} (cùng công thức Domino, khác mỗi status).
     */
    Page<VbdiDocument> findByPublishOrCreatorIdentifier(
        String documentType, String identifier, VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 29/08/2026 (đợt 2 — {@code vwPrivateImportant}, dxl dòng ~678-726, route "quantrong"): field
     * {@code MarkImportant} dùng CẢ 2 dấu phân cách "," VÀ ";" thay phiên nhau (Domino
     * {@code @Explode(MarkImportant;",") : @Explode(MarkImportant;";")} rồi {@code @Unique}) — KHÁC
     * {@link #findByAnyFieldContainsMember} (chỉ tách ";"). Chuẩn hoá "," → ";" trước khi bọc dấu
     * phân cách so khớp (coi 2 dấu phân cách tương đương, đủ khớp mọi tổ hợp thực tế đã ghi nhận).
     * {@code status} TUỲ CHỌN (null/rỗng = không lọc) — KHÁC mọi method khác trong interface này —
     * vì formula Domino gốc của {@code vwPrivateImportant} không có điều kiện status (áp dụng mọi
     * trạng thái, không chỉ ISSUED).
     */
    Page<VbdiDocument> findByFieldContainsMemberAnySeparator(
        String documentType, String status, String fieldName, String member,
        VbdiDocumentOutFilter filter, Pageable pageable);

    /**
     * 11/09/2026 (yêu cầu người dùng "port vbdi/thungrac/thungrac sang JAVA" — SỬA LẠI sau khi người
     * dùng báo "FE vẫn gọi qlvb/vanbandi/GetView", xem javadoc {@code ViewQueryService#
     * VIEW_VBDI_TRASH}/{@code queryVbdiTrash}): DUY NHẤT method trong interface này lọc
     * {@code deleted_on IS NOT NULL} (mọi method khác lọc NGƯỢC LẠI, {@code IS NULL}) — danh sách
     * "Thùng rác" (văn bản đi đã xoá mềm). Khoá theo view Domino THẬT
     * {@code vwCreator_DocumentOut_Delete} (dxl {@code view_vanbandi.dxl} dòng 822-852,
     * {@code SELECT Form = "DocumentOut_Deleted"}, categorized {@code user_creator + ";" +
     * unit_creator_identifier + ";" + delete_people}, @Explode) — FE khoá theo USERNAME hiện tại
     * ({@code chobanhanh/ItemsList.jsx} dòng ~200-212), tức văn bản mà user hiện tại là NGƯỜI TẠO
     * ({@code user_creator}, field động) HOẶC NGƯỜI ĐÃ XOÁ ({@code delete_people} Domino ↔
     * {@code deleted_by} cột tĩnh Java — so khớp {@code deletedByUserId}, KHÔNG so chuỗi username vì
     * {@code deleted_by} lưu UUID). {@code unit_creator_identifier} giữ lại trong OR cho khớp field
     * categorized gốc dù so khớp với username hầu như không bao giờ đúng (khác định dạng).
     */
    Page<VbdiDocument> findTrashedByUsernameOrDeleter(
        String documentType, String username, java.util.UUID deletedByUserId,
        String keyword, List<String> keywordColumns, Pageable pageable);
}