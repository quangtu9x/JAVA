package com.td.application.viewquery;

import com.td.application.common.dynamicfields.ItemsFilter;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesByProcessUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.ListFilesByProcessRequest;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.duthao.DraftProcessDocumentRepository;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.application.hosocv.HoSoCVStatus;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.application.hosoluutru.HoSoLuuTruDocumentRepository;
import com.td.application.hosoluutru.HoSoLuuTruLifecycleStatus;
import com.td.application.hosoluutru.HoSoLuuTruPhieuYeuCauDocumentRepository;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument;
import com.td.application.duthao.DuThaoStatus;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.sharedcore.SearchAppUsersRequest;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.vbden.ChoYKienXinYKienUseCase;
import com.td.application.vbden.ChuyenXuLyVbdenUseCase;
import com.td.application.vbden.VbdenResDocumentRepository;
import com.td.application.vbden.VbdenStatusService;
import com.td.application.vbden.XinYKienDocumentRepository;
import com.td.application.vbcddh.CapNhatTienDoUseCase;
import com.td.application.vbcddh.VbcddhDocumentRepository;
import com.td.application.phieutrinhtd.PhieuTrinhTdDocumentRepository;
import com.td.application.vbdi.VbdiDocumentOutFilter;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DraftProcessDocument;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.vbcddh.VbcddhDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.vbden.VbdenResDocument;
import com.td.domain.vbden.XinYKienDocument;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * BL-102 (View Query Engine): tương thích {@code qlvb/view/query} — mapping {@code viewname} sang
 * truy vấn cụ thể. View tối thiểu: {@code vwDocument_ChoXuLy}, {@code vwuserbyusername},
 * {@code vwuserbyparentid}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ViewQueryService {

    static final String VIEW_DOCUMENT_CHO_XU_LY = "vwdocument_choxuly";
    // Slice 7: FE gui viewname="chutri" (khong phai "vwdocument_choxuly") cho tab "Cho xu ly" mac
    // dinh (choxuly.jsx) - dxl chutri (dong 2350-2378, Slice 1) cung cong thuc/khoa user_process voi
    // view nay, nen alias thang, khong tao case rieng (dung tien le "daketthucxuly" alias sang
    // vwdocument_daxuly ben duoi).
    static final String VIEW_VBDEN_CHUTRI = "chutri";
    static final String VIEW_DOCUMENT_DA_XU_LY = "vwdocument_daxuly";
    static final String VIEW_QTXL_BY_PARENT_ID = "qtxl_by_parentid";
    static final String VIEW_USER_BY_USERNAME = "vwuserbyusername";
    static final String VIEW_USER_BY_PARENT_ID = "vwuserbyparentid";
    // "Người ký" (Slice 1 dự thảo, 24/08/2026) — dùng bởi dropdown "Người ký" ở nhiều màn soạn thảo
    // (duthao/vbcddh/vbdi/phieutrinhTD...) khi chọn "Cấp ban hành" xong: chỉ trả về những AppUser
    // thuộc tổ chức {@code key} VÀ có {@code capLanhDao} (là "lãnh đạo") — khác
    // {@code vwuserbyparentid} (BL-102, TẤT CẢ user của 1 tổ chức, không lọc lãnh đạo).
    static final String VIEW_USER_LEADER_BY_PARENT_ID = "vwuserleaderbyparentid";
    private static final String NOT_A_LEADER_VALUE = "khong";
    // BL-220 (VBDi Core) — DoD (4): "chờ ban hành"/"đã ban hành" là trạng thái (status), khác
    // vbden's "chờ xử lý"/"đã xử lý" vốn theo người xử lý (user_process/user_processed).
    static final String VIEW_VANDI_CHO_BAN_HANH = "vwvandi_chobanhanh";
    static final String VIEW_VANDI_DA_BAN_HANH = "vwvandi_dabanhanh";
    // 28/08/2026 (port route FE "vbdi/xuly/cq_toanboso" + 3 route anh em cùng dom_view_name:
    // "toanboso_all"/"dv_toanboso"/"pb_toanboso" — xem javadoc queryVbdiDocumentOutTextBook).
    static final String VIEW_VBDI_DOCUMENTOUT_TEXTBOOK = "vwcreator_documentout_textbook";
    // 28/08/2026 (yêu cầu người dùng "kiểm trùng gọi tới view vwDocumentOut_kiemtrungvb, port sang
    // JAVA" — xem javadoc queryVbdiKiemTrungVanBan).
    static final String VIEW_VBDI_KIEMTRUNGVB = "vwdocumentout_kiemtrungvb";
    // 29/08/2026 (yêu cầu người dùng "port các tab trong vbdi/xuly/cq_toanboso, chức năng chọn sổ và
    // tìm kiếm" — xem javadoc queryVbdiDocumentOutByDateRange/queryVbdiDocumentOutBySnvCategory/
    // queryVbdiDocumentOutByTextBook). Tên viewname/URL giữ NGUYÊN chữ hoa/thường của Domino gốc
    // trong comment cho dễ đối chiếu — biến giữ bản normalizeViewname (lowercase) để so khớp switch.
    static final String VIEW_VBDI_DOCUMENTOUT_TODAY = "vwcreator_documentout_today"; // "Trong ngày"
    static final String VIEW_VBDI_DOCUMENTOUT_YESTERDAY = "homqua"; // "Hôm qua"
    static final String VIEW_VBDI_DOCUMENTOUT_THISWEEK = "trongtuan"; // "Trong tuần"
    static final String VIEW_VBDI_DOCUMENTOUT_THISMONTH = "trongthang"; // "Trong tháng"
    static final String VIEW_VBDI_SNV_NCC = "vbncc_snv"; // "VB Người có công" (chỉ hiện ở H05.13)
    static final String VIEW_VBDI_SNV_BHTN = "vbbhtn_snv"; // "VB Bảo hiểm thất nghiệp"
    static final String VIEW_VBDI_SNV_HANHCHINH = "vbhanhchinh_snv"; // "VB hành chính" (phần còn lại)
    static final String VIEW_VBDI_SNV_GPLD = "vbgpld_snv"; // "VB Giải phóng lao động" (chưa có tab FE
    // riêng — vẫn port vì dxl có thật và cùng họ 3 view kia, phòng khi FE thêm tab)
    static final String VIEW_VBDI_DOCUMENTOUT_BYTEXTBOOK = "vwcreator_documentout_bytextbook"; // "chọn sổ"

    // 16/09/2026 (yêu cầu người dùng "modal Câp nhật kết quả xử lý văn bản -> Gắn văn bản trả lời -
    // port và cutoverfe" — ItemsActiveListVBdiTraLoi.jsx, danh sách chọn văn bản đi để "gắn kèm" khi
    // Cập nhật kết quả xử lý vbden): 2 view CÒN LẠI FE dùng (tab "sovanbandi" đã port từ trước, xem
    // VIEW_VBDI_DOCUMENTOUT_TEXTBOOK). Đối chiếu dxl view_vanbandi.dxl:
    // - vanbandi_by_author (dòng 2152-2178): selection CHỈ Form="DocumentOut" (KHÔNG lọc status nào —
    //   mọi trạng thái, kể cả DRAFT), categorized theo field "author" (multi-value, separatemultiplevalues
    //   =true, KHÔNG có @Explode tường minh nhưng vẫn liệt kê rời từng phần tử) — FE gửi
    //   key=currentUser.identifier (tab "Toàn bộ tổ chức"/"Toàn bộ cá nhân").
    // - vbdi_by_user_draft (dòng 3332-3352): selection Form="DocumentOut" & user_draft!="" (cũng
    //   KHÔNG lọc status), categorized theo "user_draft" — FE gửi key=currentUser.username (tab "Là
    //   người soạn"). Dùng lại findByFieldContainsMemberAnySeparator (status=null, đã có sẵn cho
    //   vwPrivateImportant) — CHỈ method trong VbdiDocumentRepository hỗ trợ "không lọc status".
    static final String VIEW_VBDI_BY_AUTHOR = "vanbandi_by_author";
    static final String VIEW_VBDI_BY_USER_DRAFT = "vbdi_by_user_draft";

    // 29/08/2026 (yêu cầu người dùng "port toàn bộ chức năng ngoài những phần đã làm ở trên trong
    // route vbdi/xuly/" — đợt 1, xem javadoc các case gọi bên dưới + 2 method mới
    // findByAnyFieldEqualsIdentifier/findByAnyFieldContainsMember trong VbdiDocumentRepository).
    static final String VIEW_VBDI_TOANBO_ALL_BO = "vwcreator_documentout_all_bo"; // route "toanbo_all"
    static final String VIEW_VBDI_DOCUMENTOUT_ALL = "vwcreator_documentout_all"; // "cq_toanbo"/"dv_toanbo"/"pb_toanbo"
    static final String VIEW_VBDI_USER_ALL = "vwdocumentout_userall"; // "cn_toanbo"
    static final String VIEW_VBDI_WAITING_TO_SEND = "vwcreator_documentout_waitingtosend"; // "chuagui"
    static final String VIEW_VBDI_SENT = "vwcreator_documentout_sent"; // "dagui"
    // 09/09/2026 (port not SoLuongCanXuLy - xem javadoc GetSoLuongCanXuLyUseCase): dxl 2814-2884 -
    // bien the "Signer" (khoa SCALAR field user_signer, so khop truc tiep, KHAC 2 view "Creator"
    // tren dung khoa to chuc unit_publish_identifier/unit_creator_identifier qua cong thuc @If-fallback).
    // Dieu kien that: Form="DocumentOut" & status="2" & send_agent={"0"|"1"} - da BO QUA
    // "(send_error!="0"|send_error!="1")" (hang dung toan hoc, xem javadoc queryVbdiDocumentOutTextBookLike)
    // va "@IsUnavailable($Conflict)" (khai niem replication conflict cua Domino, khong ton tai o Java/Postgres).
    static final String VIEW_VBDI_SIGNER_DOCUMENTOUT_WAITINGTOSEND = "vwsigner_documentout_waitingtosend";
    static final String VIEW_VBDI_SIGNER_DOCUMENTOUT_SENT = "vwsigner_documentout_sent";
    static final String VIEW_VBDI_REVOKE = "vwcreator_documentout_revoke"; // "vanbandathuhoi"
    static final String VIEW_VBDI_REPLY_REFUSE = "vwcreator_documentout_replyrefuse"; // "tralai-tuchoi"
    static final String VIEW_VBDI_REPLY_DRAFTOR = "vwcreator_documentout_reply_draftor"; // "datralai"
    // 09/09/2026 (port not SoLuongCanXuLy, nhom VBDI_COQUAN - xem javadoc GetSoLuongCanXuLyUseCase):
    // 2 view Domino THAT SU KHAC voi 2 hang so tren (ten gan giong nhung dxl khac, doi chieu
    // docs/views_domino/view_vanbandi.dxl):
    //   vwCreator_DocumentOut_Refuse (dxl ~646-676): Form="DocumentOut" & status="2" &
    //     unit_reply_refuse!="" & is_unit_reply_refuse="1" - HEP hon ReplyRefuse (khong co nhanh
    //     is_document_reply="1").
    //   vwCreator_DocumentOut_Reply (dxl ~435-473): Form="DocumentOut" & status="2" &
    //     is_document_reply="1" & deletelistWaitingToSend!="1" - KHAC Reply_Draftor (status="3").
    // Khoa ca 2 la union identifier (user_creator/unit_creator_identifier[/unit_publish_identifier/
    // user_draft cho Reply], BO qua thanh phan dac biet ";all" - cung quyet dinh da ap dung cho
    // vwUser_Coordinator "all" o duthao, khong co bang chung can ho tro "xem toan bo khong loc").
    static final String VIEW_VBDI_CREATOR_DOCUMENTOUT_REFUSE = "vwcreator_documentout_refuse";
    static final String VIEW_VBDI_CREATOR_DOCUMENTOUT_REPLY = "vwcreator_documentout_reply";
    static final String VIEW_VBDI_DOCUMENT_REPLACE = "vwcreator_document_replace"; // "thaythe"
    // 3 view "hàng đợi cá nhân" ($username$ server-resolve, xem javadoc findByAnyFieldContainsMember)
    static final String VIEW_VBDI_COORDINATER_RECEIVE = "vwdocumentout_coordinater_receive"; // "chophoihop"
    static final String VIEW_VBDI_COORDINATER = "vwdocumentout_coordinater"; // "phoihop"
    static final String VIEW_VBDI_CHO_XEM_DE_BIET = "vwdocumentout_choxemdebiet"; // "choxemdebiet"
    static final String VIEW_VBDI_XEM_DE_BIET = "vwdocumentout_xemdebiet"; // "xemdebiet"
    // Macro Domino thật — xem BE_THANHHOA/.../TDDatabaseConnection.java dòng ~1287/1592/1787:
    // server tự thay "$username$" bằng username phiên đăng nhập, KHÔNG BAO GIỜ tin giá trị này là
    // identifier thật nếu nó khớp y hệt chuỗi này (xem javadoc findByAnyFieldContainsMember).
    private static final String USERNAME_TOKEN = "$username$";

    // 29/08/2026 (đợt 2 "port toàn bộ vbdi/xuly/*"): xem javadoc các case gọi + 2 method mới
    // findByPublishOrCreatorIdentifier/findByFieldContainsMemberAnySeparator trong VbdiDocumentRepository.
    static final String VIEW_VBDI_SEND_NO_RESPONSE = "vwcreator_documentout_sendnoresponse"; // "chuaden"
    // vwDocumentOut_XuLy dùng CHUNG bởi CẢ 2 route "choxuly" VÀ "xuly" trong THỰC TẾ (component
    // xuly/Page.jsx cố định activeTab="choxuly", CHỈ 1 tab UI — nhánh "vwDocumentOut_Nhanxemdebiet"
    // trong ItemsList.jsx#fetchData là DEAD CODE hiện tại, không có UI nào set activeTab khác) — vẫn
    // port CẢ HAI vì view kia có dxl thật, phòng khi FE thêm tab sau (cùng tinh thần vbgpld_snv).
    static final String VIEW_VBDI_XULY = "vwdocumentout_xuly"; // "choxuly"/"xuly" (nhánh thật)
    static final String VIEW_VBDI_NHANXEMDEBIET = "vwdocumentout_nhanxemdebiet"; // "xuly" (nhánh chết hiện tại)
    static final String VIEW_VBDI_DAXEMDEBIET = "vwdocumentout_daxemdebiet"; // tab "Đã xem" của "choxemdebiet"
    static final String VIEW_VBDI_PRIVATE_IMPORTANT = "vwprivateimportant"; // "quantrong"
    static final String VIEW_VBDI_NHANDEBIET_UNSEEN = "vwgroup_reader_by_nhandebiet"; // tab "Chưa xem"
    static final String VIEW_VBDI_NHANDEBIET_SEEN = "vwgroup_reader_by_nhandebiet_seen"; // tab "Đã xem"
    static final String VIEW_VBDI_BAOCAO_CHUA_XEM = "vwdocumentout_baocaochuaxem"; // "baocaochuaxem"
    static final String VIEW_VBDI_DRAFTING_MC = "vwcreator_drafting_mc"; // "chocapkysomc"
    static final String VIEW_VBDI_GROUP_BY_SIGNER = "vwgroup_reader_by_signer"; // "nguoiky"
    static final String VIEW_VBDI_GROUP_BY_DRAFTOR = "vwgroup_by_draftor"; // "nguoisoan"
    static final String VIEW_VBDI_GROUP_UNIT_IN_CHARGE = "vwgroup_is_unit_in_charge_by_identifier"; // "donvisoan"/"toanbodonvisoan"
    // 11/09/2026 (port "vbdi/thungrac/thungrac"): SỬA LẠI sau khi người dùng báo "FE vẫn gọi
    // qlvb/vanbandi/GetView" — điều tra ban đầu nhắm NHẦM route chết
    // (thungrac/components/ItemsList.jsx, KHÔNG được `SystemsPageDi.tsx` mount — route thật
    // (RouteThungRac, dòng ~910-925) dùng CHUNG component `chobanhanh/components/ItemsList.jsx` với
    // `dom_view_name="vwCreator_DocumentOut_Delete"` — view Domino THẬT (dxl view_vanbandi.dxl dòng
    // 822-852, KHÁC HẲN "templateQuery=getVanBanDaXoa" đã điều tra lúc trước — đó là 1 route khác
    // hoàn toàn, `qlvb/search`, không phải route đang dùng `qlvb/view/query`). Tên case khớp ĐÚNG
    // viewname FE gửi (loadViewName() trả nguyên dom_view_name cho actiontype="thungrac") để
    // isVbdiViewPortedToJava (vbdiApi.js) dispatch đúng - xem javadoc queryVbdiTrash.
    static final String VIEW_VBDI_TRASH = "vwcreator_documentout_delete"; // "Thùng rác văn bản đi"

    // 4 công thức SNV cố định (dxl view_vanbandi.dxl dòng ~2244-2341/2609-2639) — hằng số Java NỘI BỘ,
    // KHÔNG BAO GIỜ ghép từ input client (xem javadoc VbdiDocumentOutFilter#extraPredicateSql, tránh
    // SQL injection). document_register/subject/unit_in_charge là field động dạng chuỗi scalar.
    private static final String SNV_REGISTER = "COALESCE(d.dynamic_fields->>'document_register', '')";
    private static final String SNV_SUBJECT = "COALESCE(d.dynamic_fields->>'subject', '')";
    private static final String SNV_UNIT_IN_CHARGE = "LOWER(TRIM(COALESCE(d.dynamic_fields->>'unit_in_charge', '')))";
    // vbncc_snv (dxl ~2262): Contains(UPPER(TRIM(document_register)),"NCC") | UPPER(TRIM(document_register))="PHIẾU BÁO" | LOWER(TRIM(unit_in_charge))="phòng người có công"
    private static final String SNV_FILTER_NCC =
        "UPPER(TRIM(" + SNV_REGISTER + ")) LIKE '%NCC%'"
        + " OR UPPER(TRIM(" + SNV_REGISTER + ")) = 'PHIẾU BÁO'"
        + " OR " + SNV_UNIT_IN_CHARGE + " = 'phòng người có công'";
    // vbbhtn_snv (dxl ~2294): Contains(UPPER(TRIM(document_register)),"BHTN") | Contains(UPPER(TRIM(subject)),"QĐ BHTN") | unit_in_charge IN (2 cách viết "nghiệp"/"nghiêp" — GIỮ NGUYÊN lỗi chính tả gốc)
    private static final String SNV_FILTER_BHTN =
        "UPPER(TRIM(" + SNV_REGISTER + ")) LIKE '%BHTN%'"
        + " OR UPPER(TRIM(" + SNV_SUBJECT + ")) LIKE '%QĐ BHTN%'"
        + " OR " + SNV_UNIT_IN_CHARGE + " = 'phòng bảo hiểm thất nghiệp'"
        + " OR " + SNV_UNIT_IN_CHARGE + " = 'phòng bảo hiểm thất nghiêp'";
    // vbgpld_snv (dxl ~2627): Contains(UPPER(TRIM(subject)),"GPLĐ") | unit_in_charge IN (3 giá trị)
    private static final String SNV_FILTER_GPLD =
        "UPPER(TRIM(" + SNV_SUBJECT + ")) LIKE '%GPLĐ%'"
        + " OR " + SNV_UNIT_IN_CHARGE + " = 'phòng lao động, việc làm'"
        + " OR " + SNV_UNIT_IN_CHARGE + " = 'phòng việc làm'"
        + " OR " + SNV_UNIT_IN_CHARGE + " = 'phòng chính sách lao động'";
    // vbhanhchinh_snv (dxl ~2329): phần CÒN LẠI — PHỦ ĐỊNH của cả 3 công thức trên GỘP (không tách
    // rời NCC/BHTN/GPLD như 3 hằng số trên vì union phủ định phải cùng 1 biểu thức, tránh double-NOT
    // sai logic De Morgan nếu ghép 3 chuỗi NOT() riêng bằng AND).
    private static final String SNV_FILTER_HANHCHINH =
        "NOT (" + SNV_FILTER_NCC + " OR " + SNV_FILTER_BHTN + " OR " + SNV_FILTER_GPLD + ")";
    // BL-240 (HoSoCV Core) — DoD (3): "toàn bộ hồ sơ của cá nhân" (docs/frontend/07-hosocv-hosoluutru.md
    // — view vwUser_All). Đơn giản hoá CÓ CHỦ Ý: chỉ lọc theo field "creator" (người tạo), KHÔNG
    // replicate toàn bộ mô hình sở hữu dữ liệu thật (OR giữa creator/process_handler/approved_by/
    // coordinator) — DoD chỉ yêu cầu "danh sách hồ sơ + phân trang", không yêu cầu đủ 25 view con
    // của module hosocv thật.
    static final String VIEW_HOSOCV_TOAN_BO = "vwuser_all";

    // Mo rong BL-240, port lo view con theo docs/analysis/08-phan-tich-port-hosocv-domino-sang-java.md
    // muc 3 + xac nhan docs/analysis/13-checklist-port-hosocv-domino-sang-java.md muc 1.1/1.2 (loai
    // route mo coi/thuoc module khac - muc 1.3/1.4 tai lieu do). Tat ca doc/ghi qua
    // HoSoCVDocumentRepository (bang rieng hosocv_documents, KHONG con la "documents" dung chung).
    static final String VIEW_HOSOCV_USER_PROCESS_HANDLER = "vwuser_process_handler";
    static final String VIEW_HOSOCV_CHUTRI_DANGXULY = "hoso_chutri_dangxuly";
    static final String VIEW_HOSOCV_CHUTRI_DAXULY = "hoso_chutri_daxuly";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_0 = "vwuser_process_handler_storaged_0";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_1 = "vwuser_process_handler_storaged_1";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_2 = "vwuser_process_handler_storaged_2";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_3 = "vwuser_process_handler_storaged_3";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_TRINH_1 = "vwuser_process_handler_trinh_1";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_TRINH_2 = "vwuser_process_handler_trinh_2";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_TRINH_3 = "vwuser_process_handler_trinh_3";
    static final String VIEW_HOSOCV_PROCESS_HANDLER_DEN_HAN_NOP_LUU_TRU =
        "vwuser_process_handler_den_han_nop_luu_tru";
    static final String VIEW_HOSOCV_APPROVED_BY_TRINH_2 = "vwuser_approved_by_trinh_2";
    static final String VIEW_HOSOCV_LEADER_IN_CHARGE = "vwuser_leader_in_charge";
    // "vwuser_coordinator" TRUNG ten voi VIEW_DUTHAO_USER_COORDINATOR - khong khai bao hang so rieng
    // o day, xu ly gop trong case cua hang so do (nhanh theo dbType).
    // Khoa FE la "<username>_chua"/"<username>_da" (khong phai username thuan) - xem
    // queryHoSoCVCoordinatedStatus.
    static final String VIEW_HOSOCV_COORDINATED_STATUS = "vwuser_coordinated_status";
    static final String VIEW_HOSOCV_TRASH = "vwtrash";
    // 8 view tra cuu/thong ke (tracuu/*) - DON GIAN HOA CO CHU Y (dung tinh than "da don gian hoa CO
    // CHU Y" cua chinh BL-240 goc): tra ve danh sach loc/phan trang theo field khoa tuong ung, KHONG
    // xay dung engine group-by-count tong quat moi (Domino la categorized view, Java chua co tien le
    // ha tang nay - xem docs/analysis/13 Phan 3 "Field-mapping rieng cho hosocv... chua co").
    static final String VIEW_HOSOCV_BY_PROCESS_HANDLER = "vwby_process_handler";
    static final String VIEW_HOSOCV_BY_LEADER_IN_CHARGE = "vwby_leader_in_charge";
    static final String VIEW_HOSOCV_BY_COORDINATOR = "vwby_coordinator";
    static final String VIEW_HOSOCV_BY_DOCUMENT_PROFILES_TYPE = "vwby_document_profiles_type";
    static final String VIEW_HOSOCV_END_DATE = "vwend_date";
    static final String VIEW_HOSOCV_OVERDUE_PROCESSING = "vwoverdue_processing";
    static final String VIEW_HOSOCV_USER_OVERDUE_PROCESSING = "vwuser_overdue_processing";

    private static final String HOSOCV_USER_CREATOR_FIELD = "user_creator";
    private static final String HOSOCV_USER_PROCESS_HANDLER_FIELD = "user_process_handler";
    private static final String HOSOCV_USER_APPROVED_BY_FIELD = "user_approved_by";
    private static final String HOSOCV_USER_LEADER_IN_CHARGE_FIELD = "user_leader_in_charge";
    private static final String HOSOCV_USER_COORDINATOR_FIELD = "user_coordinator";
    private static final String HOSOCV_USER_COORDINATED_FIELD = "user_coordinated";
    private static final String HOSOCV_CATEGORY_FIELD = "category";
    private static final String HOSOCV_TRINHLANHDAODUYET_FIELD = "trinhlanhdaoduyet";
    private static final String HOSOCV_STORAGED_FIELD = "storaged";
    private static final String HOSOCV_STORAGE_DEADLINE_FIELD = "storage_deadline";
    private static final String HOSOCV_DEADLINE_DATE_FIELD = "deadline_date";
    private static final String HOSOCV_STATUS_STATIC_FIELD = "status";
    private static final String HOSOCV_STATUS_DANG_XU_LY = "0";

    // Port module "Ho so luu tru" (hosoluutru) - docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md
    // muc 3, docs/analysis/17-checklist-port-hosoluutru-domino-sang-java.md Phan 1 (bo qua muc ⛔:
    // muonhoso1/2, 6 view "tra cuu theo tieu chi" bi comment o FE goc, duyet/tu choi LTLS - khong co
    // bang chung). DXL goc KHONG day du (chi export duoc 1 view mac dinh SELECT @All moi CSDL) - toan
    // bo selection formula duoi day la SUY LUAN tu ten view + field loc trong code FE/BE that (xem
    // tai lieu 16 muc 1.1), KHONG phai copy formula Domino goc nhu da lam duoc cho hosocv/duthao.
    static final String VIEW_HOSOLUUTRU_TOAN_BO = "hoso_by_identifier";
    static final String VIEW_HOSOLUUTRU_CHO_TIEP_NHAN = "hoso_chuaduyet_by_identifier";
    static final String VIEW_HOSOLUUTRU_CHO_CHUYEN_LUU_TRU = "hoso_chochuyenluutru_by_identifier";
    static final String VIEW_HOSOLUUTRU_DA_CHUYEN_LUU_TRU = "hoso_dachuyenluutru_by_identifier";
    static final String VIEW_HOSOLUUTRU_CHUYEN_LTLS_0 = "hoso_chuyenltls0_by_identifier";
    static final String VIEW_HOSOLUUTRU_CHUYEN_LTLS_2 = "hoso_chuyenltls2_by_identifier";
    // "vwtrash" TRUNG ten voi VIEW_HOSOCV_TRASH - xu ly gop trong case cua hang so do (nhanh theo
    // dbType), dung khuon collision VIEW_DUTHAO_USER_COORDINATOR da dung.
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_0 = "phieuyeucau0_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_1 = "phieuyeucau1_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_2 = "phieuyeucau2_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_3 = "phieuyeucau3_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_13 = "phieuyeucau13_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_DANG_MUON = "phieuyeucau_dangmuon_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_QUA_HAN = "phieuyeucau_quahan_by_identifier";
    static final String VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_DA_TRA = "phieuyeucau_datra_by_identifier";

    private static final String HOSOLUUTRU_IDENTIFIER_FIELD = "identifier";
    private static final String HOSOLUUTRU_USER_CREATOR_FIELD = "user_creator";
    private static final String HOSOLUUTRU_CHUYENLUUTRU_FIELD = "chuyenluutru";
    private static final String HOSOLUUTRU_CHUYENLUUTRUTINH_FIELD = "chuyenluutrutinh";
    private static final String HOSOLUUTRU_STATUS1_FIELD = "status1";
    private static final String HOSOLUUTRU_RETURN_DATE_FIELD = "return_date";

    /**
     * BL-250 (VBCDDH Core), bổ sung 08/09/2026 (2) — người dùng cung cấp export DXL THẬT của
     * {@code vbcddh.nsf} (78 view, {@code docs/views_domino/view_vbcddh.dxl}), thay cho slice đầu chỉ
     * suy luận từ tên view + code FE (DXL lúc đó chỉ có {@code SELECT @All}). Toàn bộ hằng số/case
     * dưới đây port TRỰC TIẾP theo công thức {@code $Formula} + công thức cột khoá categorized đọc
     * được từ DXL — không suy diễn. 2 khoá UNION dùng chung nhiều view:
     * <ul>
     *   <li>{@code VBCDDH_DVTH_FIELDS} = [{@code donvithuchienhide}, {@code chuyenvienthuchienhide}]
     *   — dxl {@code vwNhiem_Vu_By_Donvithuchienhide}: {@code @Unique(@Explode(@Trim(donvithuchienhide)
     *   + ";" + @Trim(chuyenvienthuchienhide); ";"))} (UNION đơn vị THỰC HIỆN và cá nhân thực hiện).</li>
     *   <li>{@code VBCDDH_IDENTITY_UNION_FIELDS} = [{@code user_creator}, {@code nguoitheodoihide},
     *   {@code nguoiphutrachhide}, {@code nguoichidaohide}, {@code user_nguoichuyencddh}, {@code coquanid}]
     *   — dxl {@code vwNhiem_Vu_Ca_Nhan_Co_Quan}/{@code vwVan_Ban_Ca_Nhan_Co_Quan} (UNION người
     *   tạo/theo dõi/phụ trách/chỉ đạo/chuyển CĐĐH(exploded)/cơ quan).</li>
     * </ul>
     * So khớp member trên CẢ 2 khoá đều KHÔNG phân biệt hoa/thường (FE {@code NhiemVuItemsList.jsx}/
     * {@code ItemsList.jsx} LUÔN {@code .toUpperCase()} khoá trước khi gửi — xem javadoc
     * {@link com.td.application.vbcddh.VbcddhDocumentRepository#findByFormAndAnyFieldContainsMember}).
     * An ninh: TRUST {@code request.getKeys()} (field tổ chức/cá nhân do chính FE tự tính từ session
     * hiện tại, cùng tiền lệ các view cá nhân đơn giản khác của vbden — KHÔNG áp dụng cơ chế "tự
     * resolve" của {@code chutri_group}).
     *
     * <p><b>3 nhóm đã port đầy đủ theo DXL</b> (xem case tương ứng bên dưới để biết chi tiết từng
     * view): (1) {@code vwNhiem_Vu_By_Donvithuchienhide*} — 24 view, "thực hiện nhiệm vụ" (người ĐƯỢC
     * GIAO xử lý). (2) {@code vwNhiem_Vu_Ca_Nhan_Co_Quan*} — 23 view, "theo dõi nhiệm vụ"/"phân công
     * nhiệm vụ" (người GIAO theo dõi), khoá 2 CẤP ({@code typeLoaiNhiemVu} ∈
     * {{@code nvtuqlvb},{@code nvtheochidao},{@code all}} + khoá danh tính). (3)
     * {@code vwVan_Ban_Ca_Nhan_Co_Quan*} — 6 view (viewname FE thật khớp {@code ItemsList.jsx}, form
     * {@code vanban}, CÙNG khoá danh tính UNION 6 field, KHÔNG có {@code typeLoaiNhiemVu}). Cộng 5
     * view độc lập: {@code vwNhiem_Vu_Da_Xoa}/{@code vwVan_Ban_Da_Xoa} (thùng rác), {@code
     * vwNhiem_Vu_By_Idvanban} (bảng phân công), {@code vwTien_Do_Nhiem_Vu_By_Idnhiemvu} (lịch sử tiến
     * độ, đóng G4), {@code vwNhiem_Vu_Co_Quan} (sidebar "theo dõi nhiệm vụ", khoá 1 field
     * {@code coquanid}).
     *
     * <p><b>CHƯA port</b> (ghi lại trong tài liệu 14/15 thay vì đoán tiếp, KHÔNG có trong DXL hoặc độ
     * tin cậy FE thấp): 3 view FE gọi tên nhưng KHÔNG tồn tại trong DXL này
     * ({@code vwNhiemVu_Donvithuchienhide_By_kandinhhan}/{@code _kquavbcd}/{@code _Tuvbcd} — có thể
     * route chết hoặc view thuộc thiết kế khác chưa export); 6 view {@code _Theo_DVTH}/{@code _CVTH}/
     * {@code _CQBH}/{@code _VBGiaoViec_SKH} (khoá tổng hợp {@code $12} = ghép chuỗi "identity_type",
     * cần hạ tầng khác); {@code vwVan_Ban_Co_Quan_Theo_Nguoitao}/{@code _LanhDaoGiao} (view nhóm/báo
     * cáo, tiêu thụ qua {@code GroupTable}, chưa rõ hợp đồng response); {@code vwUser_All} (route
     * "toanbo" — viewname TRÙNG {@link #VIEW_HOSOCV_TOAN_BO}, công thức thật của vbcddh chưa xác
     * nhận, FE không override {@code viewkey} mặc định); {@code vwDoc_Sai_Thong_Tin}/
     * {@code vwDoc_Thieu_Thong_Tin*} (view audit dữ liệu, không có bằng chứng FE gọi);
     * {@code vwNhiem_Vu_Dinh_Ky_Den_Han_Phan_Cong_Lai}/{@code vwNhiem_Vu_Chua_Duyet_By_Idvanban}/
     * {@code vwNhiem_Vu_By_Idvanbanchidao}/{@code vwNhiem_Vu_Da_Xoa_By_Idvanban} (không có bằng chứng
     * FE gọi trực tiếp). Bảng phân công/báo cáo/thống kê/{@code DanhSachDonViThucHien}/restore/cutover
     * FE — xem {@code docs/analysis/15} Phần 4.
     */
    private static final String VBCDDH_DOCUMENT_TYPE = "vbcddh";
    private static final List<String> VBCDDH_DVTH_FIELDS =
        List.of("donvithuchienhide", "chuyenvienthuchienhide");
    private static final List<String> VBCDDH_IDENTITY_UNION_FIELDS = List.of(
        "user_creator", "nguoitheodoihide", "nguoiphutrachhide", "nguoichidaohide",
        "user_nguoichuyencddh", "coquanid");
    private static final String VBCDDH_FORM_NHIEMVU = "nhiemvu";
    private static final String VBCDDH_FORM_VANBAN = "vanban";
    private static final String VBCDDH_F_TINHTRANG = "COALESCE(d.dynamic_fields->>'tinhtrang','')";
    private static final String VBCDDH_F_QUAHAN = "COALESCE(d.dynamic_fields->>'quahan','')";
    private static final String VBCDDH_F_TRALAI = "COALESCE(d.dynamic_fields->>'tralai','')";
    private static final String VBCDDH_TT_CHUA_EQ = VBCDDH_F_TINHTRANG + " = 'Chưa thực hiện'";
    private static final String VBCDDH_TT_DANG_EQ = VBCDDH_F_TINHTRANG + " = 'Đang thực hiện'";
    private static final String VBCDDH_TT_DA_EQ = VBCDDH_F_TINHTRANG + " = 'Đã thực hiện'";
    private static final String VBCDDH_TT_NOT_DA = VBCDDH_F_TINHTRANG + " != 'Đã thực hiện'";
    private static final String VBCDDH_TT_CHUA_OR_DANG = "(" + VBCDDH_TT_CHUA_EQ + " OR " + VBCDDH_TT_DANG_EQ + ")";
    private static final String VBCDDH_TT_NOT_DA_LOWER = "LOWER(" + VBCDDH_F_TINHTRANG + ") NOT LIKE '%đã%'";
    private static final String VBCDDH_NOT_TRALAI = VBCDDH_F_TRALAI + " != '1'";
    private static final String VBCDDH_QUAHAN_TRONGHAN = "(" + VBCDDH_F_QUAHAN + " = '0' OR " + VBCDDH_F_QUAHAN + " = '')";
    private static final String VBCDDH_QUAHAN_QUAHAN = VBCDDH_F_QUAHAN + " = '1'";
    // dxl: (';' ghep dvth/cvth) khong dung tralai - chi 3 bien the trang thai + cac view co dieu kien
    // rieng moi dung tralai (xem tung hang so ben duoi, doi chieu dung tung view, KHONG suy dien).
    private static final String VBCDDH_HANXULY_DATE_FORMAT_GUARD =
        "(d.dynamic_fields->>'hanxuly_date') ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$'";
    private static final String VBCDDH_HANXULY_TO_DATE =
        "TO_DATE(d.dynamic_fields->>'hanxuly_date', 'DD/MM/YYYY')";
    // dxl cac view "trong 1/2 ngay"/"trong tuan/thang/quy" export voi ngay LITERAL (@Date(2026;9;8) -
    // bang chung view duoc 1 agent rewrite lai formula MOI NGAY, khong phai @Today/@Adjust dong) - ban
    // Java dung CURRENT_DATE tai thoi diem query (dung y do nghiep vu "tinh tu hom nay", khong copy
    // nguyen van ngay literal cua 1 lan export).
    private static final String VBCDDH_WEEK_START = "DATE_TRUNC('week', CURRENT_DATE)::date";
    private static final String VBCDDH_WEEK_END = "(DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '6 days')::date";
    private static final String VBCDDH_MONTH_START = "DATE_TRUNC('month', CURRENT_DATE)::date";
    private static final String VBCDDH_MONTH_END = "(DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date";
    private static final String VBCDDH_QUARTER_START = "DATE_TRUNC('quarter', CURRENT_DATE)::date";
    private static final String VBCDDH_QUARTER_END = "(DATE_TRUNC('quarter', CURRENT_DATE) + INTERVAL '3 months - 1 day')::date";
    private static final String VBCDDH_HANXULY_1DAY = vbcddhHanXuLyBetween("CURRENT_DATE", "CURRENT_DATE + 1");
    private static final String VBCDDH_HANXULY_2DAY = vbcddhHanXuLyBetween("CURRENT_DATE", "CURRENT_DATE + 2");
    private static final String VBCDDH_HANXULY_TODAY = vbcddhHanXuLyBetween("CURRENT_DATE", "CURRENT_DATE");
    private static final String VBCDDH_HANXULY_TRONGTUAN = vbcddhHanXuLyBetween(VBCDDH_WEEK_START, VBCDDH_WEEK_END);
    private static final String VBCDDH_HANXULY_TRONGTHANG = vbcddhHanXuLyBetween(VBCDDH_MONTH_START, VBCDDH_MONTH_END);
    private static final String VBCDDH_HANXULY_TRONGQUY = vbcddhHanXuLyBetween(VBCDDH_QUARTER_START, VBCDDH_QUARTER_END);
    private static final String VBCDDH_CREATED_2DAY_AGO = vbcddhCreatedOnBetween("CURRENT_DATE - 2", "CURRENT_DATE");
    private static final String VBCDDH_CREATED_TODAY = vbcddhCreatedOnBetween("CURRENT_DATE", "CURRENT_DATE");
    private static final String VBCDDH_CREATED_TRONGTUAN = vbcddhCreatedOnBetween(VBCDDH_WEEK_START, VBCDDH_WEEK_END);
    private static final String VBCDDH_CREATED_TRONGTHANG = vbcddhCreatedOnBetween(VBCDDH_MONTH_START, VBCDDH_MONTH_END);
    private static final String VBCDDH_CREATED_TRONGQUY = vbcddhCreatedOnBetween(VBCDDH_QUARTER_START, VBCDDH_QUARTER_END);

    private static String vbcddhHanXuLyBetween(String fromExpr, String toExpr) {
        return VBCDDH_HANXULY_DATE_FORMAT_GUARD + " AND " + VBCDDH_HANXULY_TO_DATE
            + " BETWEEN " + fromExpr + " AND " + toExpr;
    }

    // @Created that (khong phai field dong "create_date") -> cot created_on cua chinh entity.
    private static String vbcddhCreatedOnBetween(String fromExpr, String toExpr) {
        return "d.created_on::date BETWEEN " + fromExpr + " AND " + toExpr;
    }

    // ===== Nhom 1: vwNhiem_Vu_By_Donvithuchienhide* (24 view, form=nhiemvu, khoa UNION 2 field) =====
    static final String VIEW_VBCDDH_NV_DVTH = "vwnhiem_vu_by_donvithuchienhide";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAXULY = "vwnhiem_vu_by_donvithuchienhide_chuaxuly";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAXULY_TRONGHAN = "vwnhiem_vu_by_donvithuchienhide_chuaxuly_tronghan";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAXULY_QUAHAN = "vwnhiem_vu_by_donvithuchienhide_chuaxuly_quahan";
    static final String VIEW_VBCDDH_NV_DVTH_DANGXULY = "vwnhiem_vu_by_donvithuchienhide_dangxuly";
    static final String VIEW_VBCDDH_NV_DVTH_DANGXULY_TRONGHAN = "vwnhiem_vu_by_donvithuchienhide_dangxuly_tronghan";
    static final String VIEW_VBCDDH_NV_DVTH_DANGXULY_QUAHAN = "vwnhiem_vu_by_donvithuchienhide_dangxuly_quahan";
    static final String VIEW_VBCDDH_NV_DVTH_DAXULY = "vwnhiem_vu_by_donvithuchienhide_daxuly";
    static final String VIEW_VBCDDH_NV_DVTH_DAXULY_TRONGHAN = "vwnhiem_vu_by_donvithuchienhide_daxuly_tronghan";
    static final String VIEW_VBCDDH_NV_DVTH_DAXULY_QUAHAN = "vwnhiem_vu_by_donvithuchienhide_daxuly_quahan";
    static final String VIEW_VBCDDH_NV_DVTH_1DAY = "vwnhiem_vu_by_donvithuchienhide_1day";
    static final String VIEW_VBCDDH_NV_DVTH_2DAY = "vwnhiem_vu_by_donvithuchienhide_2day";
    static final String VIEW_VBCDDH_NV_DVTH_CREATE_2DAY = "vwnhiem_vu_by_donvithuchienhide_create_2day";
    static final String VIEW_VBCDDH_NV_DVTH_CHUA_HOAN_THANH = "vwnhiem_vu_by_donvithuchienhide_chua_hoan_thanh";
    static final String VIEW_VBCDDH_NV_DVTH_CHUA_HOAN_THANH_QUA_HAN =
        "vwnhiem_vu_by_donvithuchienhide_chua_hoan_thanh_qua_han";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAHOANTHANH = "vwnhiem_vu_by_donvithuchienhide_chuahoanthanh";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAHOANTHANH_QUAHAN =
        "vwnhiem_vu_by_donvithuchienhide_chuahoanthanh_quahan";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAHOANTHANH_TRONGHAN =
        "vwnhiem_vu_by_donvithuchienhide_chuahoanthanh_tronghan";
    static final String VIEW_VBCDDH_NV_DVTH_XU_LY_LAI = "vwnhiem_vu_by_donvithuchienhide_xu_ly_lai";
    static final String VIEW_VBCDDH_NV_DVTH_TRALAI = "vwnhiem_vu_by_donvithuchienhide_tralai";
    static final String VIEW_VBCDDH_NV_DVTH_CHUYENXULY = "vwnhiem_vu_by_donvithuchienhide_chuyenxuly";
    static final String VIEW_VBCDDH_NV_DVTH_CHUAPHOIHOP = "vwnhiem_vu_by_donvithuchienhide_chuaphoihop";
    static final String VIEW_VBCDDH_NV_DVTH_DAPHOIHOP = "vwnhiem_vu_by_donvithuchienhide_daphoihop";
    static final String VIEW_VBCDDH_NV_DVTH_DONDOC = "vwnhiem_vu_by_donvithuchienhide_dondoc";
    // dxl: (donviphoihophide!=""|chuyenvienphoihophide!="") & tinhtrang!="Đã thực hiện".
    private static final String VBCDDH_PHOIHOP_CHUA =
        "(COALESCE(d.dynamic_fields->>'donviphoihophide','') != '' OR COALESCE(d.dynamic_fields->>'chuyenvienphoihophide','') != '')";
    // dxl: donvidaphoihophide!=""|chuyenviendaphoihophide!=""|((donviphoihophide!=""|chuyenvienphoihophide!="") & tinhtrang="Đã thực hiện").
    private static final String VBCDDH_PHOIHOP_DA =
        "(COALESCE(d.dynamic_fields->>'donvidaphoihophide','') != '' OR COALESCE(d.dynamic_fields->>'chuyenviendaphoihophide','') != ''"
        + " OR (" + VBCDDH_PHOIHOP_CHUA + " AND " + VBCDDH_TT_DA_EQ + "))";

    // ===== Nhom 2: vwNhiem_Vu_Ca_Nhan_Co_Quan* (23 view, form=nhiemvu, khoa 2 cap) =====
    static final String VIEW_VBCDDH_NV_CNCQ = "vwnhiem_vu_ca_nhan_co_quan";
    static final String VIEW_VBCDDH_NV_CNCQ_CHUAXULY = "vwnhiem_vu_ca_nhan_co_quan_chuaxuly";
    static final String VIEW_VBCDDH_NV_CNCQ_DANGXULY = "vwnhiem_vu_ca_nhan_co_quan_dangxuly";
    static final String VIEW_VBCDDH_NV_CNCQ_DAXULY = "vwnhiem_vu_ca_nhan_co_quan_daxuly";
    static final String VIEW_VBCDDH_NV_CNCQ_DAXULY_TRONGHAN = "vwnhiem_vu_ca_nhan_co_quan_daxuly_tronghan";
    static final String VIEW_VBCDDH_NV_CNCQ_TRALAI = "vwnhiem_vu_ca_nhan_co_quan_tralai";
    static final String VIEW_VBCDDH_NV_CNCQ_QUA_HAN = "vwnhiem_vu_ca_nhan_co_quan_qua_han";
    static final String VIEW_VBCDDH_NV_CNCQ_DAKETTHUC = "vwnhiem_vu_ca_nhan_co_quan_daketthuc";
    static final String VIEW_VBCDDH_NV_CNCQ_CHUA_HOAN_THANH = "vwnhiem_vu_ca_nhan_co_quan_chua_hoan_thanh";
    static final String VIEW_VBCDDH_NV_CNCQ_CHUA_HOAN_THANH_QUA_HAN =
        "vwnhiem_vu_ca_nhan_co_quan_chua_hoan_thanh_qua_han";
    static final String VIEW_VBCDDH_NV_CNCQ_DONDOC = "vwnhiem_vu_ca_nhan_co_quan_dondoc";
    static final String VIEW_VBCDDH_NV_CNCQ_1DAY = "vwnhiem_vu_ca_nhan_co_quan_1day";
    static final String VIEW_VBCDDH_NV_CNCQ_1NGAY = "vwnhiem_vu_ca_nhan_co_quan_1ngay";
    static final String VIEW_VBCDDH_NV_CNCQ_2DAY = "vwnhiem_vu_ca_nhan_co_quan_2day";
    static final String VIEW_VBCDDH_NV_CNCQ_2NGAY = "vwnhiem_vu_ca_nhan_co_quan_2ngay";
    static final String VIEW_VBCDDH_NV_CNCQ_TRONGNGAY = "vwnhiem_vu_ca_nhan_co_quan_trongngay";
    static final String VIEW_VBCDDH_NV_CNCQ_TRONGTUAN = "vwnhiem_vu_ca_nhan_co_quan_trongtuan";
    static final String VIEW_VBCDDH_NV_CNCQ_TRONGTHANG = "vwnhiem_vu_ca_nhan_co_quan_trongthang";
    static final String VIEW_VBCDDH_NV_CNCQ_TRONGQUY = "vwnhiem_vu_ca_nhan_co_quan_trongquy";
    static final String VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGNGAY = "vwnhiem_vu_ca_nhan_co_quan_create_trongngay";
    static final String VIEW_VBCDDH_NV_CNCQ_CREATE_2NGAY = "vwnhiem_vu_ca_nhan_co_quan_create_2ngay";
    static final String VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGTUAN = "vwnhiem_vu_ca_nhan_co_quan_create_trongtuan";
    static final String VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGTHANG = "vwnhiem_vu_ca_nhan_co_quan_create_trongthang";
    static final String VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGQUY = "vwnhiem_vu_ca_nhan_co_quan_create_trongquy";

    // ===== Nhom 3: vwVan_Ban_Ca_Nhan_Co_Quan* (6 view, form=vanban, cung khoa UNION 6 field, KHONG co loai) =====
    static final String VIEW_VBCDDH_VB_CNCQ_1 = "vwvan_ban_ca_nhan_co_quan_1";
    static final String VIEW_VBCDDH_VB_CNCQ_CHUAPHANCONG = "vwvan_ban_ca_nhan_co_quan_chuaphancong";
    static final String VIEW_VBCDDH_VB_CNCQ_DAPHANCONG = "vwvan_ban_ca_nhan_co_quan_daphancong";
    static final String VIEW_VBCDDH_VB_CNCQ_CHUAXULY = "vwvan_ban_ca_nhan_co_quan_chuaxuly";
    static final String VIEW_VBCDDH_VB_CNCQ_DANGXULY = "vwvan_ban_ca_nhan_co_quan_dangxuly";
    static final String VIEW_VBCDDH_VB_CNCQ_DAHOANTHANH = "vwvan_ban_ca_nhan_co_quan_dahoanthanh";
    // dxl vwVan_Ban_Ca_Nhan_Co_Quan_chuaphancong/_daphancong dung field rieng "tinhtrangphancong"
    // (KHAC "tinhtrang" cua nhiemvu) - 2 gia tri that: "Chưa phân công"/"Đã phân công".
    private static final String VBCDDH_F_TINHTRANGPHANCONG = "COALESCE(d.dynamic_fields->>'tinhtrangphancong','')";

    // ===== 5 view doc lap =====
    static final String VIEW_VBCDDH_NV_DA_XOA = "vwnhiem_vu_da_xoa";
    static final String VIEW_VBCDDH_VB_DA_XOA = "vwvan_ban_da_xoa";
    static final String VIEW_VBCDDH_NV_BY_IDVANBAN = "vwnhiem_vu_by_idvanban";
    // Bugfix 16/09/2026 (port tab "Danh sach nhiem vu" o chi tiet vbden): dxl vwNhiem_Vu_By_Idvanban
    // khoa = @Unique(idvanban : idvbdi : idvbden : idduthao) - UNION 4 field, moi field ung voi 1
    // module goc khac nhau tao ra nhiem vu (khong CHI idvanban) - xem javadoc
    // VbcddhDocumentRepository#findByAnyFieldContainsMember.
    private static final java.util.List<String> VBCDDH_NV_BY_IDVANBAN_FIELDS =
        java.util.List.of("idvanban", "idvbdi", "idvbden", "idduthao");
    static final String VIEW_VBCDDH_TIENDO_BY_IDNHIEMVU = "vwtien_do_nhiem_vu_by_idnhiemvu";
    static final String VIEW_VBCDDH_NV_CO_QUAN = "vwnhiem_vu_co_quan";

    // Port module "Phieu trinh tham dinh" (phieutrinhtd) - docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md
    // muc 6.3. Ten view MOI tu thiet ke (khong phai view Domino goc - Domino goc khong co servlet
    // rieng, logic chi ton tai o FE query_by_phieutrinh() voi it nhat 1 loi da xac nhan - xem tai lieu
    // 20 muc 4 #2) - KHONG lap lai loi do, thiet ke DUNG theo nghia ten tab.
    //
    // Cap nhat 09/09/2026 (doi chieu DXL THAT `docs/views_domino/view_phieutrinh.dxl` - path DXL xac
    // nhan day CHINH LA phieutrinhtd.nsf, 26 view): sua field co tien to "user_" dung theo DXL
    // (user_creator/user_lanhdao1/user_lanhdao2/user_lanhdaovanphong - ban truoc dung nham
    // usercreator/lanhdao1/lanhdao2/lanhdaovanphong khong tien to, khong khop du lieu that). Bo
    // "signerhide"/"coordinator" khoi PHIEUTRINHTD_FIELDS_TOANBO (khong co bang chung DXL cho
    // "signerhide"; "coordinator" that su la "user_coordinator" phan tach boi DAU PHAY - can xu ly rieng,
    // xem PostgresPhieuTrinhTdDocumentRepository#findThamKhaoChoYKien - don gian hoa CO CHU Y, khong
    // gop vao TOANBO trong lo sua loi nay).
    static final String VIEW_PHIEUTRINHTD_TOANBO = "phieutrinhtd_toanbo";
    static final String VIEW_PHIEUTRINHTD_DANGSOAN = "phieutrinhtd_dangsoan";
    static final String VIEW_PHIEUTRINHTD_TRINHDUYET = "phieutrinhtd_trinhduyet";
    static final String VIEW_PHIEUTRINHTD_CHODUYET = "phieutrinhtd_choduyet";
    static final String VIEW_PHIEUTRINHTD_DADUYET = "phieutrinhtd_daduyet";
    static final String VIEW_PHIEUTRINHTD_KHONGDUYET = "phieutrinhtd_khongduyet";
    static final String VIEW_PHIEUTRINHTD_TRINHTHONGQUA = "phieutrinhtd_trinhthongqua";
    static final String VIEW_PHIEUTRINHTD_CHOTHONGQUA = "phieutrinhtd_chothongqua";
    static final String VIEW_PHIEUTRINHTD_DATHONGQUA = "phieutrinhtd_dathongqua";
    static final String VIEW_PHIEUTRINHTD_KHONGTHONGQUA = "phieutrinhtd_khongthongqua";
    static final String VIEW_PHIEUTRINHTD_THAMKHAO = "phieutrinhtd_thamkhao";
    static final String VIEW_PHIEUTRINHTD_THUNGRAC = "phieutrinhtd_thungrac";
    private static final List<String> PHIEUTRINHTD_FIELDS_TOANBO =
        List.of("user_creator", "user_lanhdao1", "user_lanhdao2", "user_lanhdaovanphong");
    private static final List<String> PHIEUTRINHTD_FIELDS_USERCREATOR = List.of("user_creator");
    private static final List<String> PHIEUTRINHTD_FIELDS_LANHDAOVANPHONG = List.of("user_lanhdaovanphong");

    /**
     * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU, xem
     * docs/analysis/02-checklist-port-vbden-con-lai.md). Đọc trực tiếp
     * {@code docs/views_domino/view_vanbanden.dxl} dòng 2820-3227 (14 view). Domino: khi phát hành
     * văn bản đi (vbdi) hoặc xử lý văn bản đến (vbden) cần xin ý kiến 1 số lãnh đạo/tổ chức
     * (TVUB/TVNB/BCH/BTV/TTĐU), hệ thống tự tạo 1 "văn bản đến GIẢ" ({@code Form="xinykien"}, cùng
     * database {@code vbden.nsf} nhưng KHÁC {@code Form="DocumentIn"} của văn bản đến thật) trong hộp
     * thư đến người được hỏi ý kiến, để họ xử lý y hệt 1 văn bản đến bình thường (containment
     * {@code user_process}/{@code user_processed}/{@code user_delivered} — CÙNG field, CÙNG cơ chế đã
     * port cho {@code chutri}/{@code daketthucxuly}/{@code dachuyenxuly}).
     *
     * <p><b>Quyết định kiến trúc</b>: Java KHÔNG có khái niệm "Form" tách biệt trong cùng
     * {@code document_type} như Domino ({@code document_type} chính là tương đương {@code Form}) —
     * nếu dùng chung {@code document_type="vbden"} cho các "văn bản đến giả" này, chúng sẽ LEAK vào
     * mọi view vbden khác đang match theo {@code user_process}/{@code user_processed} (vd
     * {@code chutri}, {@code daketthucxuly}) vì Java không có tầng lọc phụ tương đương {@code Form}.
     * Dùng {@code document_type} RIÊNG {@link #XINYKIEN_DOCUMENT_TYPE} — đúng tiền lệ
     * {@code vbden_so}/{@code vbden_res} (đã tách {@code document_type} riêng cho 2 khái niệm phụ
     * trợ khác của vbden) — cách ly hoàn toàn khỏi các view vbden lõi, không cần sửa lại bất kỳ view
     * nào đã port trước đó. {@code /api/v1/vbden/GetView} vẫn nhận request bình thường (không đổi
     * route) — 14 case bên dưới TỰ hardcode {@code document_type} riêng, bỏ qua {@code dbType} tham
     * số truyền vào (đúng tiền lệ {@code QuaTrinhXuLy} đã hardcode {@code vbden_res}).
     *
     * <p>Field cờ đơn vị cần ý kiến ({@code is_tvub_opinion}/{@code is_tvnb_opinion}/
     * {@code is_bch_opinion}/{@code is_btv_opinion}/{@code is_ttdu_opinion}) — DXL gốc có 2 kiểu công
     * thức KHÔNG ĐỒNG NHẤT: 10/14 view kiểm cả {@code ="true"|="1"}, riêng {@code chochoykien}/
     * {@code chochoykien_noi_bo} CHỈ kiểm {@code ="true"} (nhiều khả năng là thiếu sót khi thiết kế
     * view gốc, không phải chủ đích nghiệp vụ) — Java kiểm ĐỒNG NHẤT cả 2 giá trị cho TẤT CẢ 14 view
     * (superset, không bỏ sót dữ liệu nào so với bản gốc, an toàn hơn là chỉ chép y nguyên chỗ thiếu).
     * {@code dachuyenchoykien} (không hậu tố) là NGOẠI LỆ DUY NHẤT không có điều kiện cờ (dxl xác nhận
     * rõ, giữ nguyên, không tự thêm).
     */
    static final String XINYKIEN_DOCUMENT_TYPE = "vbden_xinykien";
    static final String VIEW_VBDEN_CHOCHOYKIEN = "chochoykien";
    static final String VIEW_VBDEN_CHOCHOYKIEN_NOI_BO = "chochoykien_noi_bo";
    static final String VIEW_VBDEN_CHOCHOYKIENBCH = "chochoykienbch";
    static final String VIEW_VBDEN_CHOCHOYKIENBTV = "chochoykienbtv";
    static final String VIEW_VBDEN_CHOCHOYKIENTTDU = "chochoykienttdu";
    static final String VIEW_VBDEN_DACHOYKIEN = "dachoykien";
    static final String VIEW_VBDEN_DACHOYKIEN_NOI_BO = "dachoykien_noi_bo";
    static final String VIEW_VBDEN_DACHOYKIENBCH = "dachoykienbch";
    static final String VIEW_VBDEN_DACHOYKIENBTV = "dachoykienbtv";
    static final String VIEW_VBDEN_DACHOYKIENTTDU = "dachoykienttdu";
    static final String VIEW_VBDEN_DACHUYENCHOYKIEN = "dachuyenchoykien";
    static final String VIEW_VBDEN_DACHUYENCHOYKIENBCH = "dachuyenchoykienbch";
    static final String VIEW_VBDEN_DACHUYENCHOYKIENBTV = "dachuyenchoykienbtv";
    static final String VIEW_VBDEN_DACHUYENCHOYKIENTTDU = "dachuyenchoykienttdu";
    private static final String OPINION_FLAG_TVUB = "is_tvub_opinion";
    private static final String OPINION_FLAG_TVNB = "is_tvnb_opinion";
    private static final String OPINION_FLAG_BCH = "is_bch_opinion";
    private static final String OPINION_FLAG_BTV = "is_btv_opinion";
    private static final String OPINION_FLAG_TTDU = "is_ttdu_opinion";
    private static final List<String> OPINION_FLAG_TRUE_VALUES = List.of("true", "1");
    static final String XINYKIEN_USER_PROCESS_FIELD = "user_process";
    static final String XINYKIEN_USER_PROCESSED_FIELD = "user_processed";
    static final String XINYKIEN_USER_DELIVERED_FIELD = "user_delivered";

    /**
     * DT-002 (tài liệu phân tích 04 mục 12/13, "GAP P0" — queue/view {@code dom_view_name} của
     * module {@code duthao}) — Slice đầu, đọc trực tiếp từ {@code docs/views_domino/view_duthao.dxl}.
     * Đơn giản hoá CÓ CHỦ Ý (đã hỏi xác nhận người dùng qua AskUserQuestion): tài liệu DXL liệt kê
     * ~20 view, nhưng đa số filter theo field vai trò workflow ({@code user_signer},
     * {@code user_process_handler}, {@code user_other_process_handler}, {@code user_pre_signer}...)
     * — các field này CHỈ được ghi bởi {@code DraftProcesses.DraftProcessing()} (Domino), tương
     * đương {@code xulyduthao} (DT-001), CHƯA port sang Java (đã grep toàn bộ {@code JAVA/}: không
     * use case nào ghi 5 field vai trò trên cho {@code document_type="duthao"} — chỉ
     * {@link com.td.application.duthao.TaoDuThaoUseCase}/{@link com.td.application.duthao.LuuDuThaoUseCase}
     * tồn tại, cả 2 chỉ merge field FE tự gửi, không tự tính lại vai trò theo hành động workflow).
     * Cài các view đó bây giờ sẽ ĐÚNG code nhưng LUÔN rỗng với dữ liệu tạo qua Java (không kiểm
     * chứng được) — hoãn tới khi DT-001 xong, ghi lại trong tài liệu phân tích 04 mục 12 thay vì
     * đoán trước cách DT-001 sẽ ghi field (scalar hay JSON-array).
     *
     * <p>Slice này CHỈ port 3 view có dữ liệu thật ngay từ {@code TaoDuThaoUseCase} (field
     * {@code user_creator}, trạng thái khởi tạo {@code DRAFTING} — status Domino {@code "0"}):
     * <ul>
     *   <li>{@code vwUser_Creator} (dxl 196-229): {@code SELECT Form="draft_document"} — TOÀN BỘ
     *   dự thảo, khoá {@code user_creator} (field đơn, không {@code @Explode}/{@code @Unique}).
     *   Đây cũng là fallback JS mặc định khi FE không truyền {@code dom_view_name} (vd.
     *   {@code DuThaoButtonActionPage.jsx}).</li>
     *   <li>{@code vwCreator_Drafting} (dxl 844-872, dùng bởi route {@code xuly/dangduthao} —
     *   "Đang dự thảo"): {@code SELECT Form="draft_document" & status="0"}, khoá
     *   {@code user_creator}.</li>
     *   <li>{@code vwUser_Invoked} (dxl 1480-1513, dùng bởi route {@code xuly/toanbo} — "Toàn bộ"):
     *   {@code SELECT Form="draft_document"} (không điều kiện), khoá {@code @LowerCase(author)} —
     *   field {@code author} là field BUILT-IN của Domino (người tạo document thật), KHÁC
     *   {@code user_creator} (field nghiệp vụ FE tự set). Đơn giản hoá: coi 2 khái niệm là MỘT
     *   ({@code user_creator}) vì {@code duthao} không có luồng nào documents được tạo hộ bởi người
     *   khác (khác {@code author} tách biệt khỏi field nghiệp vụ ở 1 số module có uỷ quyền tạo hộ)
     *   — kết quả: {@code vwUser_Creator} và {@code vwUser_Invoked} hiện là 2 case CÙNG hành vi ở
     *   Java (khác biệt Domino chỉ là siêu dữ liệu nội bộ, không phải nghiệp vụ thấy được ở FE).</li>
     * </ul>
     *
     * <p><b>CHƯA port trong slice này</b> (ghi trong tài liệu phân tích 04 mục 12, DT-002):
     * {@code vwCreator_Process_Handler_Submit}, {@code vwUser_Process_Handler_Submit},
     * {@code vwCreator_Pre_Signer_Submit}, {@code vwUser_Return_Draft},
     * {@code vwSigner_DocumentOut_Submited}, {@code vwUser_DocumentOut_Published}, và toàn bộ view
     * còn lại trong DXL (chặn bởi DT-001) — cùng {@code vwUser_Draft_Deleted} (chặn bởi lý do khác:
     * xoá mềm {@code duthao} qua Java cũng chưa có use case, VÀ {@code DocumentRepository#searchDeleted}
     * hiện không đẩy {@code attributeFilters} xuống SQL/phân trang — cần bổ sung repository method
     * riêng, không tái dùng được {@code findByAnyFieldContains}/{@code findByVbdenOrgView} vốn luôn
     * lọc cứng {@code deleted_on IS NULL}).
     *
     * <p><b>Cập nhật danh sách trên (25/08/2026)</b>: TOÀN BỘ hàng đợi cá nhân còn lại đã port
     * (khoi constants "PORT LOAT 2") — tổng cộng 24 view GetView của module duthao. Chỉ còn 3 ngoại
     * lệ cố ý: {@code vwUser_Coordinator}/{@code vwUser_Coordinated} (selection dạng OR-group giữa
     * coordinated và coordinator-theo-status mà {@link com.td.application.documents.VbdenOrgViewFilter}
     * chưa biểu diễn được — chogopy vẫn đi Domino), {@code vwUser_Draft_Deleted} (xóa mềm qua Java
     * chưa có use case), và các view nhóm/báo cáo ({@code vwGroup_*}, {@code vwReport_*}, ...) không
     * thuộc hợp đồng GetView-theo-username.</p>
     */
    /**
     * 09/09/2026 (yêu cầu người dùng cutover route dashboard đang gọi Domino
     * {@code qlvb/tichhop/TimKiem?databasetype=duthao} — bỏ qua Elasticsearch): view MỚI (không có
     * tên Domino thật tương ứng — FE trước đây gọi thẳng {@code qlvb/tichhop/TimKiem}, KHÔNG đi qua
     * GetView/viewname) cho "toàn bộ dự thảo của tổ chức" — phạm vi NGANG HÀNG
     * {@link #VIEW_VBDI_DOCUMENTOUT_ALL} (KHÁC {@link #VIEW_DUTHAO_USER_CREATOR} — view đó chỉ
     * "dự thảo do CHÍNH tôi tạo"), xác nhận qua điều tra BE_THANHHOA ({@code TDQuery#createQuery}):
     * {@code form="draft_document"} KHÔNG bị override "chỉ author" như {@code document_profiles}
     * (hosocv) — vẫn giữ {@code userGroups} = author + identifier tổ chức đầy đủ, ĐÚNG mô hình
     * org-scope như {@code DocumentOut} (vbdi)/{@code vbden}. Khoá = 3 field identifier tổ chức
     * ({@code unit_publish_identifier}/{@code unit_identifier}/{@code unit_in_charge_identifier}) —
     * xác nhận cả 3 field NÀY THẬT SỰ tồn tại trên {@code duthao_documents} qua
     * {@code ChuyenThanhVanBanDiUseCase} (copy nguyên 3 field này khi chuyển dự thảo thành văn bản đi
     * — cùng field, cùng ý nghĩa với vbdi, vì dự thảo CHÍNH LÀ tiền thân của văn bản đi).
     */
    static final String VIEW_DUTHAO_TOANBO_TOCHUC = "toanbotochuc";
    private static final List<String> DUTHAO_TOANBO_TOCHUC_ORG_FIELDS =
        List.of("unit_publish_identifier", "unit_identifier", "unit_in_charge_identifier");

    static final String VIEW_DUTHAO_USER_CREATOR = "vwuser_creator";
    static final String VIEW_DUTHAO_CREATOR_DRAFTING = "vwcreator_drafting";
    static final String VIEW_DUTHAO_USER_INVOKED = "vwuser_invoked";
    // DT-002 - port lai lan 2 (25/08/2026): ban dau doc view_duthao.dxl dong 1775-1804 (khoa UNION
    // user_process_handler : user_other_process_handler) - STALE so voi design that. Nguoi dung cap
    // export MOI truc tiep tu DB that docs/views_domino/vwUserCreator_Process_Handler_Submit.xml
    // (botnmt\2024\duthao.nsf, design modified 25/08/2026) la NGUON CHUAN:
    //   SELECT Form="draft_document" & (user_other_processed_handler != "") & (status="5"|status="9")
    //   khoa ($5, cot categorized dau tien, sort ascending) = @Explode(user_other_processed_handler; ";")
    // 3 diem de sai da doi chieu cheo voi code ghi field truoc khi port:
    //   * Field khoa la user_other_PROCESSED_handler = LICH SU cac username DA CHUYEN di (noi ";"
    //     KHONG dedupe - DraftProcesses.java dong 107-118, mirror XuLyDuThaoUseCase), KHAC hoan toan
    //     user_process_handler/user_other_process_handler trong dxl cu. columnheader title trong XML
    //     ghi 'user_creator' cung chi la artefact copy-paste nua cua designer (tuong tu 'user_signer'
    //     o view Other ben duoi) - cong thuc cot THAT SU moi la chuan.
    //   * Nghia nghiep vu: queue "Da chuyen xu ly" cua nguoi GUI (xem lai VB minh da chuyen di con
    //     cho), khop MapsSoLuongCanXuLy "drafting_user_processed_handler_submit" (nguoi chuyen xu
    //     ly); KHAC vwUser_Other_Process_Handler_Submit = queue cua nguoi NHAN.
    //   * Gia tri "; "-joined la SCALAR nen containment/equality khong khop theo thanh vien - dung
    //     capability moi VbdenOrgViewFilter#orgListFieldNames (appendAnyListFieldMemberPredicate).
    static final String VIEW_DUTHAO_USERCREATOR_PROCESS_HANDLER_SUBMIT =
        "vwusercreator_process_handler_submit";
    private static final String DUTHAO_USER_OTHER_PROCESSED_HANDLER_FIELD = "user_other_processed_handler";
    private static final List<String> DUTHAO_PROCESS_HANDLER_STATUSES =
        List.of(DuThaoStatus.FORWARDED_FOR_APPROVAL, DuThaoStatus.OTHER_HANDLER_SUBMIT);
    private static final String DUTHAO_USER_CREATOR_FIELD = "user_creator";
    // Bugfix 24/08/2026 (bao loi nguoi dung: "vwCreator_Drafting -> chua co du lieu" du document da
    // co dung user_creator): TAI THOI DIEM bugfix nay, DuThaoStatus.DRAFTING con la chuoi Java rieng
    // ("DRAFTING", KHAC ma so Domino "0") nen hardcode "0" o day la SAI. Doi sang tham chieu thang
    // DuThaoStatus.DRAFTING (khong hardcode chuoi) de luon dung theo dinh nghia hien hanh - ke ca
    // sau khi DuThaoStatus.DRAFTING doi lai thanh "0" (dung ma so Domino, xem javadoc DuThaoStatus,
    // cap nhat thu 2 cung ngay) thi tham chieu nay van dung ma khong can sua lai lan nua.
    private static final String DUTHAO_STATUS_DRAFTING = DuThaoStatus.DRAFTING;

    // DT-002 (tiep, 25/08/2026): route xuly/choxuly - "Cho xu ly" (comment DXL goc "nguoi cho xu ly,
    // duyet"). Doc truc tiep view_duthao.dxl dong 874-905: SELECT Form="draft_document" &
    // user_other_process_handler != "" & (status = "9"), khoa ($4, cot categorized dau tien, sort
    // ascending) = @Unique(@Name([Abbreviate];@LowerCase(@Trim(user_other_process_handler)))).
    // LUU Y: columnheader title trong design Domino ghi 'user_signer' la artefact copy-paste cua
    // nguoi thiet ke - cong thuc THAT SU cua cot dung user_other_process_handler (da doi chieu ky
    // DXL truoc khi port de tranh sai theo ten goi, cung cach da lam cho view UserCreator o tren).
    // Khac VIEW_DUTHAO_CREATOR_PROCESS_HANDLER_SUBMIT o 2 diem: CHI khoa 1 field
    // user_other_process_handler (khong UNION them user_process_handler) va CHI status "9"
    // (chuyennguoikhac) - khong gom "5" (chuyenduyet).
    static final String VIEW_DUTHAO_USER_OTHER_PROCESS_HANDLER_SUBMIT =
        "vwuser_other_process_handler_submit";
    private static final String DUTHAO_USER_OTHER_PROCESS_HANDLER_FIELD = "user_other_process_handler";
    private static final String DUTHAO_STATUS_OTHER_HANDLER_SUBMIT = DuThaoStatus.OTHER_HANDLER_SUBMIT;

    // 09/09/2026 (port not SoLuongCanXuLy - xem javadoc GetSoLuongCanXuLyUseCase): dxl 1608-1641,
    // comment Domino "DT da chuyen VT, cho ban hanh, thong bao cho nguoi tao" - SELECT
    // Form="draft_document" & (status="4") & xoavanbandi="". Khoa ($5, "author") = don gian hoa
    // thanh user_creator (cung quyet dinh da ap dung cho toan bo module, xem javadoc
    // VIEW_DUTHAO_USER_INVOKED). KHAC VIEW_DUTHAO_CREATOR_DOCUMENTOUT_SUBMITED (view
    // "vwCreator_DocumentOut_Submited", dxl 334-369) - view do CUNG dieu kien status="4" nhung
    // KHONG co dieu kien xoavanbandi - 2 view Domino that su khac nhau (khong phai 1 view bi doi
    // ten), du hien tai (theo DuThaoVanThuKhongBanHanhUseCase) status="4" va xoavanbandi!="" loai
    // tru lan nhau (xoavanbandi chi duoc set CUNG luc voi chuyen status sang "8") nen 2 view cho
    // CUNG ket qua tren du lieu Java - van port dung theo dxl (rieng dieu kien) de an toan neu
    // sau nay co luong khac lam xoavanbandi khac invariant nay.
    static final String VIEW_DUTHAO_DOCUMENTOUT_SUBMITED = "vwdocumentout_submited";
    private static final String DUTHAO_XOA_VAN_BAN_DI_FIELD = "xoavanbandi";

    // 09/09/2026 (port not SoLuongCanXuLy): dxl 1806-1872 (view_duthao.dxl) - 2 view "nguoi ky,
    // Du thao trinh ky duyet", CUNG cong thuc, chi khac dieu kien phu mc_mahs (rong/khac rong):
    //   SELECT Form="draft_document" & ((user_signer!="" & (status="1"|status="3"))
    //     | (user_other_process_handler!="" & (status="9"))) & mc_mahs {="" | !=""}
    // OR-group giua 2 FIELD KHAC NHAU, MOI field gan 1 dieu kien status RIENG (khac
    // findByFieldOrFieldWithStatus da co - chi ho tro 1 nhanh KHONG dieu kien status) - khong
    // suy rong repository method chung cho 1 cap view khong co FE nao goi truc tiep (chi dung cho
    // dem badge). Vi 2 nhanh loai tru lan nhau tren status (khong doc nao vua status=1/3 vua =9),
    // TONG 2 query rieng (moi nhanh la 1 nhom AND don gian, VbdenOrgViewFilter bieu dien duoc)
    // CHO DUNG SO LUONG count cua OR-group - dung nguyen tac da ap dung cho "pt_tralai" o
    // GetKiemNhiemPendingCountUseCase. 4 viewname duoi day CHI dung NOI BO cho GetSoLuongCanXuLyUseCase
    // (khong phai ten view Domino that, khong dang ky cho FE goi) - xem javadoc do.
    static final String VIEW_DUTHAO_CHODUYETKYBANHANH_SIGNER = "vwchoduyetkybanhanh_signer_internal";
    static final String VIEW_DUTHAO_CHODUYETKYBANHANH_HANDLER = "vwchoduyetkybanhanh_handler_internal";
    static final String VIEW_DUTHAO_CHODUYETKYBANHANHMC_SIGNER = "vwchoduyetkybanhanhmc_signer_internal";
    static final String VIEW_DUTHAO_CHODUYETKYBANHANHMC_HANDLER = "vwchoduyetkybanhanhmc_handler_internal";
    private static final String DUTHAO_MC_MAHS_FIELD = "mc_mahs";

    // =====================================================================================
    // DT-002 - PORT LOAT 2 (25/08/2026): 19 view hang doi con lai dung boi FE (SystemsPageDuThao
    // routes + cac ItemsList con goi Domino), doc truc tiep view_duthao.dxl. Nguon van la dxl export
    // 20/08/2026 — DA BI STALE 1 LAN o view UserCreator (sua theo XML cung ngay) nen moi view deu
    // duoc doi chieu cheo voi code ghi field (XuLyDuThaoUseCase mirror DraftProcesses) truoc khi
    // port; khi nghi ngo khop khuat nao, xuat XML that cua rieng view do de doi chieu nhu UserCreator.
    // Ghi chung: columnheader title trong design Domino LAP TUONG nhieu lan (user_signer/user_creator
    // sai het cho nhau) - LUON theo cong thuc <formula> cua cot, KHONG theo title.
    // Cac helper dung chung: queryDuThaoQueueByFilter (executor) + builder filter truc tiep tung case.
    // =====================================================================================
    static final String VIEW_DUTHAO_SIGNER_SUBMIT = "vwsigner_submit";                       // dxl 231-260
    static final String VIEW_DUTHAO_PRE_SIGNER_SUBMIT = "vwpre_signer_submit";               // dxl 264-295
    static final String VIEW_DUTHAO_USER_RETURN_DRAFT = "vwuser_return_draft";               // dxl 297-332
    static final String VIEW_DUTHAO_CREATOR_DOCUMENTOUT_SUBMITED =
        "vwcreator_documentout_submited";                                                    // dxl 334-369
    static final String VIEW_DUTHAO_DOCUMENTOUT_PUBLISHED = "vwdocumentout_published";       // dxl 371-399
    static final String VIEW_DUTHAO_CREATOR_SIGNER_SUBMIT = "vwcreator_signer_submit";       // dxl 401-434
    static final String VIEW_DUTHAO_CREATOR_PRE_SIGNER_SUBMIT =
        "vwcreator_pre_signer_submit";                                                       // dxl 436-467
    static final String VIEW_DUTHAO_USER_PROCESS_HANDLER_SUBMIT =
        "vwuser_process_handler_submit";                                                     // dxl 469-500
    static final String VIEW_DUTHAO_USER_SIGNER_RETURN_DRAFT =
        "vwuser_signer_return_draft";                                                        // dxl 502-533
    static final String VIEW_DUTHAO_USER_HANDLER_RETURN_DRAFT =
        "vwuser_handler_return_draft";                                                       // dxl 535-567
    static final String VIEW_DUTHAO_SIGNER_DOCUMENTOUT_SUBMITED =
        "vwsigner_documentout_submited";                                                     // dxl 569-600
    static final String VIEW_DUTHAO_CREATOR_PROCESS_HANDLER_SUBMIT =
        "vwcreator_process_handler_submit";                                                  // dxl 602-633
    // Luu y ten view giu nguyen loi chinh ta Domino "Foward" (thieu U) - khong sua.
    static final String VIEW_DUTHAO_USER_PROCESS_HANDLER_FORWARD =
        "vwuser_process_handler_foward";                                                     // dxl 635-672
    static final String VIEW_DUTHAO_BY_HANDLER_DOCUMENTOUT_SUBMITED =
        "vwby_handler_documentout_submited";                                                 // dxl 674-706
    static final String VIEW_DUTHAO_PRE_SIGNER_SIGNED = "vwpre_signer_signed";               // dxl 708-739
    static final String VIEW_DUTHAO_USER_DOCUMENTOUT_PUBLISHED =
        "vwuser_documentout_published";                                                      // dxl 741-774
    static final String VIEW_DUTHAO_CREATOR_DOCUMENTOUT_RETURN =
        "vwcreator_documentout_return";                                                      // dxl 776-809
    static final String VIEW_DUTHAO_SIGNER_DOCUMENTOUT_RETURN =
        "vwsigner_documentout_return";                                                       // dxl 811-842
    static final String VIEW_DUTHAO_SIGNER_DOCUMENTOUT_WAITINGSUBMIT =
        "vwsigner_documentout_waitingsubmit";                                                // dxl 1382-1413
    static final String VIEW_DUTHAO_USERCREATOR_PROCESSED_HANDLER_SUBMIT =
        "vwusercreator_processed_handler_submit";                                            // dxl 1874-1903

    // Field dong dung lam khoa/dieu kien cho loat view tren (ten field trung voi XuLyDuThaoUseCase).
    private static final String DUTHAO_USER_SIGNER_FIELD = "user_signer";
    private static final String DUTHAO_USER_PRE_SIGNER_FIELD = "user_pre_signer";
    private static final String DUTHAO_USER_PROCESS_HANDLER_PLAIN_FIELD = "user_process_handler";
    private static final String DUTHAO_USER_PROCESSED_LIST_FIELD = "user_processed";
    private static final String DUTHAO_RETURN_DOCOUT_FOR_FIELD = "return_document_out_for";
    private static final String DUTHAO_PRE_SIGNER_SIGNED_FIELD = "pre_signer_signed";

    // Status "8" = "VT da ban hanh" (dxl vwDocumentOut_Published/vwUser_DocumentOut_Published) -
    // chua co ten trong DuThaoStatus (chi port den trang thai workflow da dinh nghia), dung chuoi goc.
    private static final List<String> DUTHAO_STATUS_SIGNER_WAITING =
        List.of(DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.SIGNED_WAITING_SUBMIT);
    private static final List<String> DUTHAO_STATUS_DOCOUT_PUBLISHED = List.of("8");

    // DT-002 (26/08/2026, theo yeu cau nguoi dung "port ca 2 view, bo cot khoa dac biet 'all'") -
    // chogopy: vwUser_Coordinator (dxl 134-163, "Cho phoi hop") va vwUser_Coordinated (dxl 165-194,
    // "Da phoi hop"). Doi lai nhan dinh cu (da ghi la "OR-group") - vwUser_Coordinator THAT SU la
    // AND don gian: Form="draft_document" & user_coordinator!="" & (status!="8" & status!="7" &
    // status!="4") (2 lan "user_coordinator != ''" lien tiep chi la artefact designer, khong phai
    // dieu kien kep). Khoa = @Explode(user_coordinator + ";all"; ";") - phan tu dac biet "all" (cho
    // xem TOAN BO khong loc theo user) CO CHU DICH KHONG port (khong co FE call site nao truyen
    // key="all", chogopy/components/ItemsList.jsx luon truyen currentUser.username that).
    // user_coordinator/user_coordinated luu JSON array (XinYKienPhoiHopUseCase#toStringList/put) -
    // dung duoc orgFieldNames (containment/scalar-equality) binh thuong, KHONG can orgListFieldNames.
    static final String VIEW_DUTHAO_USER_COORDINATOR = "vwuser_coordinator";
    // vwUser_Coordinated selection THAT SU la OR-group: (user_coordinated != "") | (user_coordinator
    // != "" & (status="8" | status="7" | status="4")) - khoa = union @Explode(user_coordinated + ";"
    // + user_coordinator; ";"), KHONG rieng status theo tung nhanh cua khoa (moi thanh vien coordinator
    // deu vao view mot khi status dat dieu kien, ke ca nguoi chua tung phan hoi - dung y do Domino,
    // khong phai bug). VbdenOrgViewFilter (1 nhom AND) khong bieu dien duoc 2 field khac nhau voi 2
    // dieu kien status khac nhau tren cung 1 request - can method repository rieng, xem
    // DuThaoDocumentRepository#findByFieldOrFieldWithStatus.
    static final String VIEW_DUTHAO_USER_COORDINATED = "vwuser_coordinated";
    private static final String DUTHAO_USER_COORDINATOR_FIELD = "user_coordinator";
    private static final String DUTHAO_USER_COORDINATED_FIELD = "user_coordinated";
    private static final List<String> DUTHAO_COORDINATOR_DONE_STATUSES =
        List.of("8", DuThaoStatus.ENDED, DuThaoStatus.PUBLISHED);

    // DT-007 (26/08/2026, tab "Quá trình xử lý" - duthaofile.jsx): KHÔNG phải hàng đợi cá nhân theo
    // username như các view VIEW_DUTHAO_* trên - đây là lịch sử xử lý của 1 dự thảo cụ thể, khoá =
    // id dự thảo cha (field động parentid trên draft_process_documents, xem
    // DraftProcessDocumentRepository#findByParentId). Domino gốc gọi qua endpoint KHÁC hẳn
    // (qlvb/view/query?databasetype=duthao_res, không phải qlvb/vanbanduthao/GetView?databasetype=
    // duthao) nhưng dispatch ở đây CHỈ dựa vào viewname (không phải dbType, xem javadoc lớp) nên tái
    // dùng nguyên vẹn POST /api/v1/duthao/GetView hiện có, không cần endpoint riêng.
    static final String VIEW_DUTHAO_GROUP_PROCESS_BY_DRAFT_ID = "vwgroup_process_by_draft_id";

    /**
     * N03/N13 (VBDen — hàng đợi/hạn xử lý theo tổ chức). Slice 4: đọc TRỰC TIẾP từ
     * {@code docs/views_domino/view_vanbanden.dxl} (export thiết kế View Domino thật — có $Formula +
     * công thức cột phân loại, không còn phải suy luận qua code Java xung quanh như Slice 2/3) —
     * xem {@link VbdenOrgViewFilter} về cơ chế filter dùng chung.
     */
    static final String VIEW_VBDEN_CAPTRENGIAO = "captrengiao";
    static final String VIEW_VBDEN_DENNOIBO = "dennoibo";
    static final String VIEW_VBDEN_QUAMANGNOIBO = "quamangnoibo";
    static final String VIEW_VBDEN_LIENTHONG = "lienthong";
    static final String VIEW_VBDEN_DENDIENTU = "dendientu";
    // 31/08/2026 (port nốt vbden - docs/analysis/02-checklist-port-vbden-con-lai.md muc 1.4): dxl
    // 1339-1372 - "denquamang" (route rieng /vbden/xuly/denquamang, KHAC quamangnoibo/lienthong da
    // co: khoa la UNION 4 field (contact_unit_identifier + unit_process + unit_coordinate +
    // unit_to_read), khong dieu kien document_internet, cung khong dieu kien status.
    static final String VIEW_VBDEN_DENQUAMANG = "denquamang";
    // 09/09/2026 (port view "vwDocumentIn_Internet_Refuse", route FE /vbden/xuly/tuchoidenquamang):
    // dxl view_vanbanden.dxl 828-858 - Form="DocumentIn"|"DocumentIn_Deleted" & status="5" &
    // is_document_refuse="1", khoa = UNION 2 field (contact_unit_identifier;identifier) - xem
    // javadoc TuChoiTiepNhanVbdenUseCase ve quyet dinh don gian hoa "status=5" (bo qua khai niem
    // "don vi cuoi cung" cua Domino, khong tuong duong duoc trong kien truc 1-dong/tenant).
    static final String VIEW_VBDEN_TUCHOIDENQUAMANG = "tuchoidenquamang";
    private static final List<String> VBDEN_TUCHOIDENQUAMANG_ORG_FIELDS =
        List.of("contact_unit_identifier", "identifier");
    // 31/08/2026 (port trang landing mac dinh "toanbovanban" - docs/analysis/02-checklist-port-vbden-
    // con-lai.md): dxl 860-892 - Form="DocumentIn" & identifier_creator!="" - KHONG dieu kien status/
    // ngay nao khac trong formula view (bo loc ngay nhan van ban la tinh nang FE tu them, xem
    // toanbovanbanFilter). Khoa UNION 7 field (khac 8-field *_BY_IDENTIFIER_CREATOR dung boi
    // daxuly_quahan/dunghan_cuatochuc - view do co THEM "unit_to_read_manage", view nay thi khong -
    // doi chieu ky DXL truoc khi ket luan giong nhau, KHONG tai dung nham).
    static final String VIEW_VBDEN_TOANBOVANBAN = "toanbovanban";
    private static final List<String> VBDEN_TOANBOVANBAN_ORG_FIELDS = List.of(
        "identifier_creator", "unit_process", "unit_process_registered", "unit_coordinate",
        "unit_coordinate_registered", "unit_to_read", "unit_to_read_registered");
    private static final DateTimeFormatter VBDEN_DD_MM_YYYY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final String VIEW_VBDEN_PHAIXULY_CUATOCHUC = "phaixuly_cuatochuc";
    static final String VIEW_VBDEN_QUAHAN_CUATOCHUC = "quahan_cuatochuc";
    static final String VIEW_VBDEN_DAXULY_QUAHAN_CUATOCHUC = "daxuly_quahan_cuatochuc";
    static final String VIEW_VBDEN_DAXULY_DUNGHAN_CUATOCHUC = "daxuly_dunghan_cuatochuc";
    static final String VIEW_VBDEN_SAPTOIHAN_CUATOCHUC = "saptoihan_cuatochuc";
    static final String VIEW_VBDEN_DENHAN_CUATOCHUC = "denhan_cuatochuc";

    // Khoa to chuc cho N03: dxl dong 1041-1075 (captrengiao), 2103-2136 (dennoibo).
    private static final List<String> VBDEN_CAPTRENGIAO_ORG_FIELDS =
        List.of("unit_process", "unit_coordinate", "unit_to_read");
    private static final List<String> VBDEN_DENNOIBO_ORG_FIELDS = List.of("unit_process");
    private static final List<String> VBDEN_CONTACT_UNIT_ORG_FIELDS = List.of("contact_unit_identifier");
    // dxl 1339-1372 (denquamang): UNION 4 field, rong hon 3 view quamangnoibo/lienthong/dendientu
    // (chi 1 field contact_unit_identifier).
    private static final List<String> VBDEN_DENQUAMANG_ORG_FIELDS =
        List.of("contact_unit_identifier", "unit_process", "unit_coordinate", "unit_to_read");
    // Khoa to chuc cho N13: dxl dong 894-1039/3232-3263 dung "identifier" (8-field union); dong
    // 951-1007 (daxuly_quahan/dunghan_cuatochuc) dung "identifier_creator" thay vi "identifier".
    private static final List<String> VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER = List.of(
        "identifier", "unit_process", "unit_process_registered", "unit_coordinate",
        "unit_coordinate_registered", "unit_to_read", "unit_to_read_registered", "unit_to_read_manage");
    private static final List<String> VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER_CREATOR = List.of(
        "identifier_creator", "unit_process", "unit_process_registered", "unit_coordinate",
        "unit_coordinate_registered", "unit_to_read", "unit_to_read_registered", "unit_to_read_manage");
    private static final String GIAY_MOI_DOCUMENT_TYPE = "Giấy mời";

    /**
     * Slice 6 (N05/N12/N22/N28). Đọc trực tiếp từ {@code docs/views_domino/view_vanbanden.dxl}:
     * <ul>
     *   <li>{@code chophanxuly} (dxl 253-278): khoá = CHỈ {@code identifier_creator} (không union 8
     *   field như nhóm hạn xử lý), điều kiện riêng {@code user_process=""} (field trống — xem
     *   {@link VbdenOrgViewFilter#getRequireEmptyFieldName()}).</li>
     *   <li>{@code cuatochuc}/{@code daxuly_cuatochuc} (dxl 1249-1305): TRƯỚC ĐÂY (D.7/D.8) hoãn vì
     *   cột khoá thiếu {@code categorized='true'} và "chưa xác nhận FE dùng thế nào". Xác nhận lại:
     *   FE hiện CHƯA cutover sang bất kỳ API view mới nào của slice này (N03/N06/N07/N10/N11/N13 đều
     *   vậy) nên lý do hoãn đó không còn hợp lý riêng cho 2 view này — Domino
     *   {@code getAllDocumentsByKey}/{@code @DbLookup} dùng CỘT SẮP XẾP ĐẦU TIÊN làm khoá bất kể có
     *   {@code categorized='true'} hay không (thuộc tính này chỉ ảnh hưởng hiển thị nhóm trên UI, xem
     *   {@code chutri} dxl 2350 cũng không có categorized nhưng vẫn dùng đúng làm khoá) — nên cài như
     *   mọi view khác, dùng lại {@code VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER(_CREATOR)} đã có.</li>
     *   <li>{@code khongvaoso} (dxl 1503-1536): {@code unit_discard!=""}, khoá 1 field tổ chức, cùng
     *   khuôn mẫu {@code captrengiao}.</li>
     * </ul>
     * <b>Chưa cài trong slice này</b>: {@code bithuhoi} (dxl 1906-1931) — formula
     * {@code Form="DocumentIn_revoked" & user_revoked!=""} dùng {@code Form} HOÀN TOÀN KHÁC
     * {@code "DocumentIn"} (mọi view khác trong file đều dùng {@code Form="DocumentIn"}), và không có
     * dấu vết field/document_type tương ứng nào trong Java hiện tại (đã grep toàn bộ {@code JAVA/},
     * không có "revoked"/"user_revoked") — khác hẳn {@code user_recalled} (field {@code dathuhoi} đã
     * cài ở Slice 5, cùng {@code Form="DocumentIn"}, dễ nhầm vì cùng nghĩa "thu hồi" tiếng Việt). Cần
     * xác nhận luồng nào tạo bản ghi {@code Form="DocumentIn_revoked"} trước khi thiết kế mapping,
     * tránh đoán — xem thêm Phụ lục D (D.9).
     */
    static final String VIEW_VBDEN_CHOPHANXULY = "chophanxuly";
    static final String VIEW_VBDEN_CUATOCHUC = "cuatochuc";
    static final String VIEW_VBDEN_DAXULY_CUATOCHUC = "daxuly_cuatochuc";
    static final String VIEW_VBDEN_KHONGVAOSO = "khongvaoso";

    private static final List<String> VBDEN_CHOPHANXULY_ORG_FIELDS = List.of("identifier_creator");
    private static final List<String> VBDEN_KHONGVAOSO_ORG_FIELDS = List.of("unit_discard");

    /**
     * Slice 5 (N06 biến thể/N07/N10/N11 — hàng đợi cá nhân đơn giản). Đọc trực tiếp từ
     * {@code docs/views_domino/view_vanbanden.dxl} — mỗi view chỉ containment 1 field cá nhân
     * (giống hệt cấu trúc {@code chutri}/{@code vwDocument_ChoXuLy} đã có), một số thêm điều kiện
     * {@code status}. An ninh: TRUST {@code request.getKeys()} (username) — nhất quán với
     * {@code vwDocument_ChoXuLy}/{@code vwDocument_DaXuLy} đã có từ trước (BL-102/210), KHÔNG áp
     * dụng quyết định "tự resolve, không tin client" của view org-scoped (N03/N13) vì đây là field
     * cá nhân (username), không phải field tổ chức (identifier) — Domino gốc cũng không có
     * row-level ACL chặn user tự truyền username khác trên field cá nhân.
     */
    static final String VIEW_VBDEN_CHOPHANPHOI = "chophanphoi";
    // 09/09/2026 (port not SoLuongCanXuLy - xem javadoc GetSoLuongCanXuLyUseCase): dxl moi
    // docs/views_domino/view_vanbanden_dennoibo_choxuly.dxl - SELECT Form="DocumentIn", khoa
    // categorized @Unique(@Explode(user_receiver;";")) - CUNG cong thuc/dang ca nhan nhu chophanphoi
    // (chi khac field). "user_receiver" da xac nhan la field THAT (ChuyenXuLyVbdenUseCase/
    // ThuHoiVanBanUseCase/KetThucXuLyUseCase deu doc/ghi field nay), khong phai field chet.
    static final String VIEW_VBDEN_DENNOIBO_CHOXULY = "dennoibo_choxuly";
    // 09/09/2026 (port not SoLuongCanXuLy): dxl view_vanbanden.dxl dong 2559-2584 - SELECT
    // Form="DocumentIn" & user_manage!="" - khoa categorized @Unique(@Explode(user_manage;";")).
    // "user_manage" da xac nhan la field THAT (GetVbdenDetailUseCase/ChuyenXuLyVbdenUseCase/
    // ThuHoiVanBanUseCase deu doc/ghi field nay - vai "phu trach" khi phan cong xu ly).
    static final String VIEW_VBDEN_PHUTRACH_CHUAXEM = "phutrach_chuaxem";
    // 31/08/2026 (port nốt "toàn bộ chức năng còn lại" - xem docs/analysis/02-checklist-port-vbden-con-lai.md
    // mục 1.2): dxl 2052-2077 - "Đã phân phối" (tab anh em của chophanphoi, cùng file FE
    // choxuly/components/ItemsActiveList_phanphoi.jsx) - Form="DocumentIn" & user_distributed!="" -
    // CÙNG công thức/khoá dạng cá nhân như chophanphoi (khác field: "_distributed" đã phân phối XONG,
    // khác "_distributor" người phân phối).
    static final String VIEW_VBDEN_DAPHANPHOI = "daphanphoi";
    static final String VIEW_VBDEN_PHOIHOP = "phoihop";
    static final String VIEW_VBDEN_DAPHOIHOP = "daphoihop";
    static final String VIEW_VBDEN_XEMDEBIET = "xemdebiet";
    static final String VIEW_VBDEN_DAXEMDEBIET = "daxemdebiet";
    static final String VIEW_VBDEN_THEODOI = "theodoi";
    // 01/09/2026: alias - FE (ItemsActiveList.jsx dong 304) gui viewname NAY (khong phai "theodoi")
    // khi sortActive==="" (trang thai MAC DINH luc vao trang, truoc khi bam sort) - thieu case nay se
    // vo man mac dinh. Cung dieu kien/du lieu voi "theodoi" (Java chua ho tro dao chieu sap xep theo
    // sortActive, gioi han da biet tu D.10).
    static final String VIEW_VBDEN_THEODOI_SORT_THOIHAN = "theodoi_sort_thoihan";
    static final String VIEW_VBDEN_DATHEODOI = "datheodoi";
    // N20 (07/09/2026, doc.02 muc "vanbancantheodoi") - view Domino THAT (dxl 2178-2208) chi co
    // selection "Form=DocumentIn" (KHONG dieu kien status/ngay nao khac) - loc DUY NHAT qua cot
    // categorized "tasks" (@Unique(@Explode(tasks;";"))). Field "tasks" duoc ghi/xoa boi
    // TheoDoiNhiemVuUseCase (action=theodoi/huytheodoi), HOAN TOAN doc lap voi module vbcddh/"nhiem
    // vu giao viec" (da doc truc tiep TheoDoiNhiemVu.java that xac nhan - chi la 1 field list don
    // gian tren CHINH van ban den, khong lien quan document rieng nao khac). FE
    // (ItemsListNhiemVuCanTheoDoi.jsx) dung 2 route CUNG view nay: "vanbancantheodoi" (key=identifier
    // nguoi dung hien tai) va "vanbancuabocantheodoi" (key="G13" co dinh - khong phai view Domino
    // rieng, FE tu doi lai viewname truoc khi gui). Ho tro "tim kiem nang cao" - CHI field
    // received_date (RangePicker, xem onDateChange) - tai dung buildAdvancedSearchFilter da co (D.44),
    // cac field khac cua object search (unit_in_charge/unit_process/user_process/status) KHONG co UI
    // nao gui cho view nay nhung khong hai gi vi buildAdvancedSearchFilter bo qua field rong.
    static final String VIEW_VBDEN_VANBANCANTHEODOI = "vanbancantheodoi";
    static final String VIEW_VBDEN_DACHUYENPHOIHOP = "dachuyenphoihop";
    static final String VIEW_VBDEN_DACHUYENXULY = "dachuyenxuly";
    static final String VIEW_VBDEN_DAKETTHUCXULY = "daketthucxuly";
    static final String VIEW_VBDEN_DANGXULY = "dangxuly";
    static final String VIEW_VBDEN_DAKETTHUCXULY_QUAHAN = "daketthucxulyquahan";
    static final String VIEW_VBDEN_DACHUYENXULY_QUAHAN = "dachuyenxulyquahan";
    static final String VIEW_VBDEN_CHUTRIQUAHAN = "chutriquahan";
    static final String VIEW_VBDEN_VANBANTRALAI = "vanbantralai";
    static final String VIEW_VBDEN_DATHUHOI = "dathuhoi";

    /**
     * Slice 10 (N06 — "Chờ xử lý" xem theo nhóm cơ quan ban hành). Đọc trực tiếp
     * {@code docs/views_domino/view_vanbanden.dxl} dòng 2319-2348 ({@code chutri_group}: categorized
     * 2 cấp — cấp 1 {@code user_process} (username hiện tại), cấp 2 {@code unit_in_charge} giảm dần,
     * cột 3 {@code totals='total'}) + đối chiếu {@code GetViewGroup.java} (Domino — endpoint SỐ ÍT,
     * KHÁC {@code GetViewGroups}/{@code GetViewGroupsByCategory} plural dùng cho màn thống kê khác).
     * An ninh: Domino thật CŨNG bỏ qua {@code keys} client gửi, luôn dùng
     * {@code connection.user_token.username} — Java làm giống, tự resolve qua
     * {@code currentUserProvider}, không dùng {@code request.getKeys()}.
     *
     * <p><b>Cải tiến có chủ đích so với Domino</b>: {@code GetViewGroup.java} thật phân trang theo
     * CHỈ SỐ DOCUMENT toàn cục xuyên qua mọi nhóm (dòng 167-205) — 1 trang có thể cắt ngang giữa 1
     * nhóm, và field {@code count} (tổng số của nhóm) không khớp số document thực tế hiển thị trong
     * trang đó — nhiều khả năng là lỗi thiết kế gốc, không phải chủ đích. Bản Java phân trang theo
     * SỐ NHÓM (mỗi trang = N nhóm đầy đủ, không cắt ngang) — {@code total} trả về = tổng số nhóm,
     * không phải tổng số document. Không phải hồi quy vì hành vi gốc đã không nhất quán nội bộ.</p>
     */
    static final String VIEW_VBDEN_CHUTRI_GROUP = "chutri_group";
    private static final String UNIT_IN_CHARGE_FIELD = "unit_in_charge";
    private static final int CHUTRI_GROUP_MAX_DOCUMENTS = 2000;

    /**
     * "Toàn bộ văn bản đến của cá nhân" ({@code toanbovanban_vbden_canhan}, component
     * {@code ItemsToanBoCuaCaNhan.jsx}). Đọc trực tiếp {@code docs/views_domino/view_vanbanden.dxl}:
     * <ul>
     *   <li>{@code cuacanhan} (dòng 1220-1247): {@code Form="DocumentIn"} — KHÔNG điều kiện status/ngày
     *   nào khác ("toàn bộ", giống {@code cuatochuc} nhưng khoá là 9 field CÁ NHÂN, không phải tổ
     *   chức). Khoá = {@code @Unique(@Explode(user_process+";"+user_coordinate+";"+user_track+";"
     *   +user_to_read+";"+user_processed+";"+user_coordinateed+";"+user_tracked+";"+user_to_readed
     *   +";"+user_delivered))} — union TẤT CẢ vai trò cá nhân (đang xử lý + đã xử lý), tái dùng
     *   {@link #queryVbdenPersonalQueue(String, List, String, String, String, List, Pageable)} (đã có,
     *   chỉ cần truyền list nhiều field thay vì 1 field).</li>
     *   <li>{@code sapdenhan_cuacanhan} (dòng 2504-2530): {@code status!="3" & status!="4" &
     *   0<=(deadline_date-today)<=2 & document_type!="Giấy mời"} — CÙNG điều kiện ngày/loại VB với
     *   {@code saptoihanCuatochucFilter} (tổ chức) nhưng khoá 3 field cá nhân
     *   {@code user_process+user_coordinate+user_processing}. An ninh: TRUST {@code username} client
     *   gửi — cùng quyết định với mọi view cá nhân khác ở Slice 5 (KHÁC view tổ chức tự resolve
     *   identifier), vì đây vốn cũng là field cá nhân, không phải field tổ chức.</li>
     * </ul>
     */
    static final String VIEW_VBDEN_CUACANHAN = "cuacanhan";
    static final String VIEW_VBDEN_SAPDENHAN_CUACANHAN = "sapdenhan_cuacanhan";
    private static final List<String> VBDEN_CUACANHAN_FIELDS = List.of(
        "user_process", "user_coordinate", "user_track", "user_to_read", "user_processed",
        "user_coordinateed", "user_tracked", "user_to_readed", "user_delivered");
    private static final List<String> VBDEN_SAPDENHAN_CUACANHAN_FIELDS =
        List.of("user_process", "user_coordinate", "user_processing");

    /**
     * Bổ sung 14/09/2026 (yêu cầu người dùng: "route thùng rác (vbden/thungrac/thungrac) -> chuyển
     * GetViewColl về dùng chung GetView, port sang java nếu chưa có, cutoverFE") — tương thích 3 view
     * {@code docs/views_domino/view_vanbanden_so.dxl}, đều categorized theo {@code identifier_creator}
     * + sort {@code document_number} giảm dần, đều dùng chung 1 component FE
     * ({@code ItemsList_sovb.jsx}, phân biệt bởi prop {@code view_name}):
     * <ul>
     *   <li>{@code sovanban_bixoa} (dòng 67-91, route {@code thungrac/thungrac}):
     *   {@code SELECT @All & @Contains(Form;"Deleted") & isrevoke!="1"}.</li>
     *   <li>{@code sovanban_bithuhoi} (dòng 327-351, route {@code thungrac/bithuhoi}, bổ sung
     *   14/09/2026 (2) sau khi người dùng yêu cầu "kiểm tra 2 view anh em, nếu dùng thì port luôn"
     *   — XÁC NHẬN có dùng, link trong {@code SidebarMenuSystemVBden.jsx} "Bị thu hồi"):
     *   {@code SELECT @All & @Contains(Form;"Deleted") & isrevoke="1"}.</li>
     *   <li>{@code sovanban_phieuyeucau_bixoa} (dòng 219-243, route {@code thungrac/phieuyeucau_thungrac},
     *   bổ sung 14/09/2026 (2), link "Phiếu yêu cầu đã xóa"):
     *   {@code SELECT @All & @Contains(Form;"Deleted") & request_form = "true"}.</li>
     * </ul>
     *
     * <p>Đúng kiến trúc 2-document (xem javadoc {@link com.td.application.vbden.TiepNhanVbdenUseCase}/
     * {@link com.td.application.vbden.XoaVanBanDenUseCase}): cả 3 view sống trên
     * {@code documentType="vbden_so"} (bản ghi "sổ"), KHÔNG PHẢI trên {@code documentType="vbden"} —
     * hardcode "vbden_so" ngay trong {@link #queryVbdenTrash} (bỏ qua {@code dbType} tham số của
     * {@link #execute}, luôn = "vbden" từ {@code VbdenController#getView}) — cùng cách
     * {@code queryHoSoCVTrash}/{@code queryPhieuTrinhTdTrash} mỗi module tự quyết loại document cần
     * trash, không phụ thuộc dbType truyền vào.</p>
     *
     * <p><b>"Deleted" của Domino KHÔNG map 1-1 sang 1 cơ chế Java duy nhất</b> (phát hiện khi port
     * {@code sovanban_bithuhoi} — đối chiếu {@code VbdiRevokeTaskHandler#revokeSoVanBanEntries}):
     * <ul>
     *   <li>{@code sovanban_bixoa}/{@code sovanban_phieuyeucau_bixoa} — "Deleted" thật = soft-delete
     *   CHUẨN qua {@code XoaVanBanDenUseCase}/{@code PhucHoiVanBanDenUseCase} (cột
     *   {@code deleted_on}/{@code ISoftDelete}) — dùng {@code VbdenOrgViewFilter#deletedOnly}.</li>
     *   <li>{@code sovanban_bithuhoi} — "Bị thu hồi" đến từ {@code VbdiRevokeTaskHandler} (worker,
     *   thu hồi CHÍNH THỨC qua giao thức vbdi) — worker này UPDATE TRỰC TIẾP {@code dynamic_fields}
     *   qua JDBC thuần ({@code reply='1', is_revoke='1', delete_date=...}), KHÔNG hề đụng cột
     *   {@code deleted_on} — nên KHÔNG dùng {@code deletedOnly}, mà lọc field động
     *   {@code reply="1" AND is_revoke="1"} (2 điều kiện AND qua {@code includeFieldValues} nhiều
     *   key). 2 cơ chế "xoá mềm" này ĐỘC LẬP, không giao nhau trong thực tế (1 dòng chỉ đi qua ĐÚNG 1
     *   trong 2 luồng) nên không cần loại trừ chéo.</li>
     * </ul>
     * <b>Bugfix ngay trong lần port {@code sovanban_bixoa} trước đó (14/09/2026 (1))</b>: đã lỡ dùng
     * SAI tên field {@code "isrevoke"} (chép nguyên văn DXL) làm điều kiện loại trừ — field THẬT được
     * {@code VbdiRevokeTaskHandler} ghi là {@code "is_revoke"} (có dấu gạch dưới, xác nhận qua grep
     * trực tiếp mã nguồn worker, KHÔNG suy đoán) — {@code "isrevoke"} không tồn tại ở BẤT KỲ đâu khác
     * trong dynamic_fields nên điều kiện cũ là no-op vô hại (không sai lệch kết quả trong thực tế, vì
     * 2 luồng xoá/thu hồi độc lập như trên) nhưng vẫn sửa lại cho đúng, tránh gài bẫy người đọc sau.</p>
     *
     * <p>{@code sovanban_phieuyeucau_bixoa}: field {@code request_form} — grep toàn bộ {@code JAVA/}
     * xác nhận CHƯA CÓ bất kỳ use case nào set field này (tính năng "Tạo phiếu yêu cầu văn bản đến"
     * — nếu tồn tại — CHƯA được port sang Java, không tìm thấy UI tạo mới nào trong
     * {@code ThanhHoaDEV/src/app/pages/vbden}). Vẫn port ĐÚNG cơ chế truy vấn (route/menu "Phiếu yêu
     * cầu đã xóa" đã xác nhận CÓ dùng thật qua {@code SidebarMenuSystemVBden.jsx}) để sẵn sàng khi
     * tính năng tạo phiếu yêu cầu được port sau này — hiện tại danh sách này sẽ LUÔN RỖNG, đây KHÔNG
     * phải lỗi của lần port này.</p>
     *
     * <p>Không tin {@code request.getKeys()} client gửi (identifier) — tự resolve server-side qua
     * {@link ResolveIdentifierByLevelUseCase}, cùng nguyên tắc {@code GetSoVanBanDenUseCase} (view
     * tổ chức, khác view cá nhân TRUST key client).</p>
     */
    static final String VIEW_VBDEN_TRASH = "sovanban_bixoa";
    static final String VIEW_VBDEN_TRASH_REVOKED = "sovanban_bithuhoi";
    static final String VIEW_VBDEN_TRASH_REQUEST_FORM = "sovanban_phieuyeucau_bixoa";
    private static final String VBDEN_SO_DOCUMENT_TYPE = "vbden_so";
    private static final String VBDEN_TRASH_PARENT_ID_FIELD = "parentid";
    private static final String VBDEN_TRASH_IDENTIFIER_CREATOR_FIELD = "identifier_creator";

    private final DocumentRepository documentRepository;
    // Cum du thao (duthao/draft_process) - bang vat ly doc lap duthao_documents, yeu cau nguoi dung
    // 24/08/2026 ("tao bang rieng, de doc lap du lieu") - xem javadoc DuThaoDocument.
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    // Mo rong BL-240, yeu cau nguoi dung ("tao bang moi, su dung jsonb (dynamic field) nhu cac module
    // da thuc hien") - bang vat ly doc lap hosocv_documents (tach khoi "documents" dung chung) - xem
    // javadoc HoSoCVDocument.
    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    // Port module "Ho so luu tru" (hosoluutru) - bang vat ly doc lap hosoluutru_documents/
    // hosoluutru_phieu_yeu_cau_documents - xem docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md.
    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    // Cum van ban di (vbdi/vbdi_res) - bang vat ly doc lap vbdi_documents, yeu cau nguoi dung (tach
    // vbdi khoi bang documents dung chung) - xem javadoc VbdiDocument.
    private final VbdiDocumentRepository vbdiDocumentRepository;
    // N08 ("cho y kien" TVUB/TVNB/BCH/BTV/TTDU) - bang vat ly doc lap vbden_xinykien_documents, yeu
    // cau nguoi dung 31/08/2026 ("van ban xin y kien se tao rieng form, nen tao bang rieng") - xem
    // javadoc XinYKienDocument.
    private final XinYKienDocumentRepository xinYKienDocumentRepository;
    // BL-210 DoD (4), yeu cau nguoi dung 05/09/2026 ("tach vbden_res sang bang rieng") - bang vat ly
    // doc lap vbden_res_documents (tach khoi "documents" dung chung, van ban den CHINH van o lai
    // documents) - xem javadoc VbdenResDocument.
    private final VbdenResDocumentRepository vbdenResDocumentRepository;
    // BL-250 (VBCDDH Core), bo sung 08/09/2026 (yeu cau nguoi dung "tach vbcddh khoi bang documents
    // dung chung, tao bang rieng") - bang vat ly doc lap vbcddh_documents - xem javadoc VbcddhDocument.
    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    // Port module "Phieu trinh tham dinh" (phieutrinhtd), 09/09/2026 - bang vat ly doc lap
    // phieutrinhtd_documents - xem docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md.
    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    // vwuserbyparentid (25/08/2026, ItemsActiveList.jsx) - tai dung logic suy luan breadcrumb to
    // chuc (agency/unit/department/agency_level) + position/caplanhdao/level DA CO, KHONG viet lai
    // (xem toUserFieldsWithOrgProfile).
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    // DT-007 (26/08/2026, vwGroup_process_by_draft_id) - bang vat ly doc lap draft_process_documents
    // (tach khoi duthao_documents 25/08/2026, xem javadoc DraftProcessDocument).
    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    // Bugfix 27/08/2026 (phan hoi nguoi dung "chua thay hien thi duoc tep dinh kem", sau do dao lai
    // sang "quan ly theo id cha"): moi vet draft_process can loi field "files" (shape
    // {name,size,type}) trong response GetView - LOC theo cap (parentDraftId, processId) qua
    // ListDocumentFilesByProcessUseCase (tep van luu theo document_id = id du thao cha) - xem
    // toFilteredProcessFields.
    private final ListDocumentFilesByProcessUseCase listDocumentFilesByProcessUseCase;
    // Bugfix (bao loi nguoi dung "danh sach chua hien thi duoc tep dinh kem (tepbanhanh)"): KHAC
    // listDocumentFilesByProcessUseCase o tren (loc theo TUNG vet xu ly) - day la toan bo tep cua
    // CHINH document du thao (khong loc processId), dung cho cot "File" o cac danh sach
    // toanbo/choxuly/dangduthao/... (xem toFilteredDocumentFields(DuThaoDocument,...)), cung 1 query
    // GetDuThaoDetailUseCase#collectFiles da dung cho man chi tiet.
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;

    public PaginationResponse<Map<String, Object>> execute(String dbType, ViewQueryRequest request) {
        String viewname = normalizeViewname(request.getViewname());
        Pageable pageable = buildPageable(request);
        String key = firstKey(request.getKeys());

        String keyword = request.getKeyword();
        List<String> keywordColumns = request.getColumns();

        return switch (viewname) {
            case VIEW_DOCUMENT_CHO_XU_LY, VIEW_VBDEN_CHUTRI ->
                queryDocumentsChoXuLy(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DOCUMENT_DA_XU_LY -> queryDocumentsDaXuLy(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_QTXL_BY_PARENT_ID -> queryChildrenByParentId(dbType, key, request.getItems(), pageable);
            case VIEW_USER_BY_USERNAME -> queryUserByUsername(key, request.getItems(), pageable);
            case VIEW_USER_BY_PARENT_ID -> queryUsersByParentId(key, request.getItems(), keyword, pageable);
            case VIEW_USER_LEADER_BY_PARENT_ID -> queryUserLeadersByParentId(key, request.getItems(), pageable);
            case VIEW_VANDI_CHO_BAN_HANH ->
                queryVbdiByStatus(dbType, VbdiStatus.PENDING_SIGNATURE, request.getItems(), pageable);
            case VIEW_VANDI_DA_BAN_HANH ->
                queryVbdiByStatus(dbType, VbdiStatus.ISSUED, request.getItems(), pageable);
            case VIEW_VBDI_DOCUMENTOUT_TEXTBOOK ->
                queryVbdiDocumentOutTextBook(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_KIEMTRUNGVB ->
                queryVbdiKiemTrungVanBan(dbType, key, request.getItems(), pageable);
            case VIEW_VBDI_DOCUMENTOUT_TODAY, VIEW_VBDI_DOCUMENTOUT_YESTERDAY,
                 VIEW_VBDI_DOCUMENTOUT_THISWEEK, VIEW_VBDI_DOCUMENTOUT_THISMONTH ->
                queryVbdiDocumentOutByDateRange(dbType, key, viewname, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SNV_NCC ->
                queryVbdiDocumentOutBySnvCategory(dbType, key, SNV_FILTER_NCC, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SNV_BHTN ->
                queryVbdiDocumentOutBySnvCategory(dbType, key, SNV_FILTER_BHTN, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SNV_GPLD ->
                queryVbdiDocumentOutBySnvCategory(dbType, key, SNV_FILTER_GPLD, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SNV_HANHCHINH ->
                queryVbdiDocumentOutBySnvCategory(dbType, key, SNV_FILTER_HANHCHINH, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_DOCUMENTOUT_BYTEXTBOOK ->
                queryVbdiDocumentOutByTextBook(dbType, request.getKeys(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_BY_AUTHOR ->
                queryVbdiFieldContainsNoStatus(dbType, "author", key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_BY_USER_DRAFT ->
                queryVbdiFieldContainsNoStatus(dbType, "user_draft", resolveUsernameToken(key), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_TOANBO_ALL_BO ->
                queryVbdiByFieldsEquals(dbType, key, List.of("unit_publish_identifier"), VbdiStatus.ISSUED,
                    null, request.getItems(), keyword, keywordColumns, pageable);
            // 09/09/2026 (yeu cau nguoi dung cutover route dashboard tu Domino qlvb/tichhop/TimKiem?
            // databasetype=vbdi, bo qua Elasticsearch): view nay CHINH LA "toan bo van ban di cua to
            // chuc" (status=ISSUED) - dung PHAM VI cua Domino TimKiem mac dinh (khong doi identifier
            // cu the) - nen ghep buildVbdiAdvancedSearchFilter(request.getSearch()) NGAY o day thay vi
            // tao view rieng, giong cach da lam voi toanbovanban (vbden). identifier: request KHONG
            // gui keys (dashboard) -> resolveCurrentIdentifier() (server-resolved, giong toanbovanban);
            // co gui keys (cq_toanbo/dv_toanbo/pb_toanbo cu) -> GIU NGUYEN client-supplied + isOwnIdentifier.
            // Bugfix 15/09/2026 (doi chieu lai nhanh Elastic CU cua view nay - ItemsList.jsx#fetchData,
            // filter.key_or - truoc khi go han nhanh do o FE): Elastic dung 4 field
            // (unit_publish_identifier/unit_identifier/unit_in_charge_identifier/unit_creator_identifier)
            // nhung case nay lúc port (09/09/2026) chi liet ke 3, thieu "unit_creator_identifier" - bo
            // sung du 4 de KHONG bi hep pham vi ket qua sau khi FE khong con fallback qua Elastic nua.
            case VIEW_VBDI_DOCUMENTOUT_ALL ->
                queryVbdiByFieldsEquals(dbType, (key != null && !key.isBlank()) ? key : resolveCurrentIdentifier(),
                    List.of("unit_publish_identifier", "unit_identifier", "unit_in_charge_identifier",
                        "unit_creator_identifier"),
                    VbdiStatus.ISSUED, null, buildVbdiAdvancedSearchFilter(request.getSearch()),
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_USER_ALL ->
                queryVbdiByFieldsContainsMember(dbType,
                    resolveUsernameToken(key),
                    List.of("user_creator", "user_draft", "coordinator_hide_rtext", "userxemdebiet"),
                    VbdiStatus.ISSUED, null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_WAITING_TO_SEND ->
                queryVbdiDocumentOutTextBookLike(dbType, key, VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'send_agent', '') = '0' AND COALESCE(d.dynamic_fields->>'deletelistWaitingToSend', '') != '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SENT ->
                queryVbdiDocumentOutTextBookLike(dbType, key, VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'send_agent', '') = '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_REVOKE ->
                queryVbdiByFieldsEquals(dbType, key, List.of("user_creator", "unit_creator_identifier"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'revoke_documentOut', '') = '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_REPLY_REFUSE ->
                queryVbdiByFieldsEquals(dbType, key,
                    List.of("user_creator", "unit_creator_identifier", "unit_publish_identifier", "user_draft"),
                    VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'is_document_reply', '') = '1'"
                        + " OR (COALESCE(d.dynamic_fields->>'unit_reply_refuse', '') != ''"
                        + " AND COALESCE(d.dynamic_fields->>'is_unit_reply_refuse', '') = '1')",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_REPLY_DRAFTOR ->
                queryVbdiByFieldsEquals(dbType, key,
                    List.of("user_creator", "unit_creator_identifier", "unit_publish_identifier"),
                    VbdiStatus.RETURNED_BY_CLERK, null, request.getItems(), keyword, keywordColumns, pageable);
            // xem javadoc VIEW_VBDI_CREATOR_DOCUMENTOUT_REFUSE/_REPLY.
            case VIEW_VBDI_CREATOR_DOCUMENTOUT_REFUSE ->
                queryVbdiByFieldsEquals(dbType, key,
                    List.of("user_creator", "unit_creator_identifier"),
                    VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'unit_reply_refuse', '') != ''"
                        + " AND COALESCE(d.dynamic_fields->>'is_unit_reply_refuse', '') = '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_CREATOR_DOCUMENTOUT_REPLY ->
                queryVbdiByFieldsEquals(dbType, key,
                    List.of("user_creator", "unit_creator_identifier", "unit_publish_identifier", "user_draft"),
                    VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'is_document_reply', '') = '1'"
                        + " AND COALESCE(d.dynamic_fields->>'deletelistWaitingToSend', '') != '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_DOCUMENT_REPLACE ->
                queryVbdiByFieldsEquals(dbType, key,
                    List.of("user_creator", "unit_creator_identifier", "unit_publish_identifier"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'document_replace_retrieve', '') = '4'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_COORDINATER_RECEIVE ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key), List.of("usercoordinater"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'isgiveopinion', '') != '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_COORDINATER ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key), List.of("usercoordinater_send"),
                    VbdiStatus.ISSUED, null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_CHO_XEM_DE_BIET ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key),
                    List.of("coordinator_hide_not_seen_rtext", "userxemdebiet"),
                    VbdiStatus.ISSUED, null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_XEM_DE_BIET ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key), List.of("userxemdebiet_send"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'userxemdebiet', '') != ''",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SEND_NO_RESPONSE ->
                queryVbdiSendNoResponse(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_XULY ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key), List.of("userchuyenxuly"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'userchuyenxuly', '') != ''",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_NHANXEMDEBIET ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key), List.of("userxemdebiet"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'userxemdebiet', '') != ''",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_DAXEMDEBIET ->
                queryVbdiByFieldsContainsMember(dbType, resolveUsernameToken(key), List.of("coordinator_hide_seen_rtext"),
                    VbdiStatus.ISSUED, "COALESCE(d.dynamic_fields->>'coordinator_hide_seen_rtext', '') != ''",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_NHANDEBIET_UNSEEN ->
                queryVbdiByFieldsContainsMember(dbType, key,
                    List.of("coordinator_hide_not_seen_rtext", "userxemdebiet"),
                    VbdiStatus.ISSUED, null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_NHANDEBIET_SEEN ->
                queryVbdiByFieldsContainsMember(dbType, key, List.of("coordinator_hide_seen_rtext"),
                    VbdiStatus.ISSUED, null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_BAOCAO_CHUA_XEM ->
                queryVbdiBaoCaoChuaXem(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_DRAFTING_MC ->
                queryVbdiByFieldsEquals(dbType, key,
                    List.of("unit_publish_identifier", "unit_publish_identifier_other"),
                    VbdiStatus.DRAFT, "COALESCE(d.dynamic_fields->>'mc_madv', '') != ''",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_GROUP_BY_SIGNER ->
                queryVbdiByFieldsEqualsUnvalidated(dbType, key, List.of("user_signer"), VbdiStatus.ISSUED,
                    null, request.getItems(), keyword, keywordColumns, pageable);
            // xem javadoc VIEW_VBDI_SIGNER_DOCUMENTOUT_WAITINGTOSEND/_SENT.
            case VIEW_VBDI_SIGNER_DOCUMENTOUT_WAITINGTOSEND ->
                queryVbdiByFieldsEqualsUnvalidated(dbType, key, List.of("user_signer"), VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'send_agent', '') = '0'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_SIGNER_DOCUMENTOUT_SENT ->
                queryVbdiByFieldsEqualsUnvalidated(dbType, key, List.of("user_signer"), VbdiStatus.ISSUED,
                    "COALESCE(d.dynamic_fields->>'send_agent', '') = '1'",
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_GROUP_BY_DRAFTOR ->
                queryVbdiByFieldsEqualsUnvalidated(dbType, key, List.of("user_draft"), VbdiStatus.ISSUED,
                    null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_GROUP_UNIT_IN_CHARGE ->
                queryVbdiByFieldsEquals(dbType, key, List.of("unit_in_charge_identifier", "unit_identifier"),
                    VbdiStatus.ISSUED, null, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_TRASH ->
                queryVbdiTrash(dbType, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDI_PRIVATE_IMPORTANT ->
                queryVbdiPrivateImportant(dbType, resolveUsernameToken(key), request.getItems(), keyword, keywordColumns, pageable);
            // Mo rong BL-240 (bugfix): doi "creator" -> "user_creator" (dung field TaoHoSoCVUseCase
            // that su stamp - xem javadoc lop do) + doi sang HoSoCVDocumentRepository (bang rieng
            // hosocv_documents, khong con la "documents" dung chung).
            // Bugfix 08/09/2026 (nguoi dung bao "GetView hosocv voi keys=[\"$username$\"] khong ra du
            // lieu"): TOAN BO case hosocv duoi day truoc gio dung THANG "key" (macro "$username$" tu
            // FE, xem ItemsList.jsx dong 81 default) lam gia tri loc username - GIONG HET lop bug da
            // tung gap va sua o vbdi ("$username$" la MACRO THAT cua Domino, server PHAI tu thay bang
            // username phien dang nhap qua CurrentUserProvider, xem javadoc resolveUsernameToken) -
            // ap dung LAI dung cong thuc do cho moi case hosocv khoa theo username (KHONG ap dung cho
            // VIEW_HOSOCV_BY_DOCUMENT_PROFILES_TYPE - khoa theo category, khong phai username).
            // 09/09/2026 (yeu cau nguoi dung cutover route dashboard tu Domino qlvb/search?
            // databasetype=hosocv, bo qua Elasticsearch): xac nhan qua dieu tra BE_THANHHOA (TDQuery#
            // createQuery dong 141-167, comment goc "hoso: xem cua ca nhan thoi") - form=document_profiles
            // CHI scope theo CHINH nguoi dung (author/user_creator), KHONG co org-scope nhu vbden/vbdi -
            // nen "tim kiem nang cao" GHEP THANG vao view "toan bo" hien co (van chi user_creator=minh),
            // chi hop nhat them cac tieu chi loc phu tu buildHoSoCVAdvancedSearchFilter.
            case VIEW_HOSOCV_TOAN_BO -> {
                VbdenOrgViewFilter advancedFilter = buildHoSoCVAdvancedSearchFilter(request.getSearch());
                yield (advancedFilter != null)
                    ? queryHoSoCVOrgView(dbType, HOSOCV_USER_CREATOR_FIELD, resolveUsernameToken(key),
                        advancedFilter, request.getItems(), pageable)
                    : queryHoSoCVPersonalQueue(dbType, HOSOCV_USER_CREATOR_FIELD, resolveUsernameToken(key), request.getItems(), pageable);
            }
            case VIEW_HOSOCV_USER_PROCESS_HANDLER ->
                queryHoSoCVPersonalQueue(dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_CHUTRI_DANGXULY -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_STATUS_STATIC_FIELD,
                List.of(HoSoCVStatus.DANG_XU_LY), resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_CHUTRI_DAXULY -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_STATUS_STATIC_FIELD,
                List.of(HoSoCVStatus.DA_KET_THUC), resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_0 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_STORAGED_FIELD, List.of("0"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_1 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_STORAGED_FIELD, List.of("1"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_2 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_STORAGED_FIELD, List.of("2"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_STORAGED_3 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_STORAGED_FIELD, List.of("3"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_TRINH_1 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_TRINHLANHDAODUYET_FIELD, List.of("1"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_TRINH_2 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_TRINHLANHDAODUYET_FIELD, List.of("2"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_TRINH_3 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, HOSOCV_TRINHLANHDAODUYET_FIELD, List.of("3"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_PROCESS_HANDLER_DEN_HAN_NOP_LUU_TRU -> queryHoSoCVDueDate(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, resolveUsernameToken(key), HOSOCV_STORAGE_DEADLINE_FIELD, false,
                Map.of(HOSOCV_STORAGED_FIELD, "0"), request.getItems(), pageable);
            case VIEW_HOSOCV_APPROVED_BY_TRINH_2 -> queryHoSoCVPersonalQueueWithStatus(
                dbType, HOSOCV_USER_APPROVED_BY_FIELD, HOSOCV_TRINHLANHDAODUYET_FIELD, List.of("2"),
                resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_LEADER_IN_CHARGE, VIEW_HOSOCV_BY_LEADER_IN_CHARGE ->
                queryHoSoCVPersonalQueue(dbType, HOSOCV_USER_LEADER_IN_CHARGE_FIELD, resolveUsernameToken(key), request.getItems(), pageable);
            // VIEW_HOSOCV_COORDINATOR ("vwuser_coordinator") TRUNG ten voi VIEW_DUTHAO_USER_COORDINATOR
            // - xu ly gop trong case do (nhanh dbType="hosocv"), xem chu thich o day.
            case VIEW_HOSOCV_BY_COORDINATOR ->
                queryHoSoCVListMember(dbType, HOSOCV_USER_COORDINATOR_FIELD, resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_COORDINATED_STATUS ->
                // Khoa la "<username>_chua"/"<username>_da" (FE tu ghep username that truoc, xem
                // ItemsList.jsx dong 173/178) - KHONG BAO GIO la macro "$username$" thuan, nhung goi
                // resolveUsernameToken van AN TOAN (no-op neu key khac dung "$username$").
                queryHoSoCVCoordinatedStatus(dbType, resolveUsernameToken(key), request.getItems(), pageable);
            // "vwtrash" TRUNG ten voi VIEW_HOSOLUUTRU_TRASH (module hosoluutru dung chung 1 hang so
            // rong hon - xem khai bao VIEW_HOSOLUUTRU_TOAN_BO...) - nhanh theo dbType, dung khuon
            // collision VIEW_DUTHAO_USER_COORDINATOR da dung.
            case VIEW_HOSOCV_TRASH -> "hosoluutru".equalsIgnoreCase(dbType)
                ? queryHoSoLuuTruTrash(request.getItems(), pageable)
                : queryHoSoCVTrash(dbType, request.getItems(), pageable);
            case VIEW_HOSOCV_BY_PROCESS_HANDLER, VIEW_HOSOCV_END_DATE ->
                queryHoSoCVPersonalQueue(dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, resolveUsernameToken(key), request.getItems(), pageable);
            case VIEW_HOSOCV_BY_DOCUMENT_PROFILES_TYPE ->
                queryHoSoCVPersonalQueue(dbType, HOSOCV_CATEGORY_FIELD, key, request.getItems(), pageable);
            case VIEW_HOSOCV_USER_OVERDUE_PROCESSING -> queryHoSoCVDueDate(
                dbType, HOSOCV_USER_PROCESS_HANDLER_FIELD, resolveUsernameToken(key), HOSOCV_DEADLINE_DATE_FIELD, true,
                Map.of(HOSOCV_STATUS_STATIC_FIELD, HOSOCV_STATUS_DANG_XU_LY), request.getItems(), pageable);
            case VIEW_HOSOCV_OVERDUE_PROCESSING -> queryHoSoCVDueDate(
                dbType, null, null, HOSOCV_DEADLINE_DATE_FIELD, true,
                Map.of(HOSOCV_STATUS_STATIC_FIELD, HOSOCV_STATUS_DANG_XU_LY), request.getItems(), pageable);
            // Port module "Phieu trinh tham dinh" (phieutrinhtd), 09/09/2026 - xem khai bao hang so
            // VIEW_PHIEUTRINHTD_* + docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md muc 6.3.
            case VIEW_PHIEUTRINHTD_TOANBO ->
                queryPhieuTrinhTdByAnyField(PHIEUTRINHTD_FIELDS_TOANBO, key, request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_DANGSOAN -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key, List.of(PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_TRINHDUYET -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key,
                List.of(PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1, PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP2),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_CHODUYET -> queryPhieuTrinhTdApproverWaiting(key, request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_DADUYET -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key,
                List.of(PhieuTrinhTdDocument.STATUS_DA_DUYET_CAP1, PhieuTrinhTdDocument.STATUS_DA_DUYET_CAP2),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_KHONGDUYET -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key,
                List.of(PhieuTrinhTdDocument.STATUS_KHONG_DUYET_CAP1, PhieuTrinhTdDocument.STATUS_KHONG_DUYET_CAP2),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_TRINHTHONGQUA -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key, List.of(PhieuTrinhTdDocument.STATUS_TRINH_THONG_QUA),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_CHOTHONGQUA -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_LANHDAOVANPHONG, key, List.of(PhieuTrinhTdDocument.STATUS_TRINH_THONG_QUA),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_DATHONGQUA -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key, List.of(PhieuTrinhTdDocument.STATUS_DA_THONG_QUA),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_KHONGTHONGQUA -> queryPhieuTrinhTdByAnyFieldAndStatusIn(
                PHIEUTRINHTD_FIELDS_USERCREATOR, key, List.of(PhieuTrinhTdDocument.STATUS_KHONG_THONG_QUA),
                request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_THAMKHAO -> queryPhieuTrinhTdThamKhao(key, request.getItems(), pageable);
            case VIEW_PHIEUTRINHTD_THUNGRAC -> queryPhieuTrinhTdTrash(request.getItems(), pageable);
            // Port module "Ho so luu tru" (hosoluutru) - xem khai bao hang so VIEW_HOSOLUUTRU_* +
            // docs/analysis/17-checklist-...md Phan 1.
            case VIEW_HOSOLUUTRU_TOAN_BO ->
                queryHoSoLuuTruByIdentifier(key, null, request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_CHO_TIEP_NHAN -> queryHoSoLuuTruByIdentifier(
                key, Map.of("status", List.of(HoSoLuuTruLifecycleStatus.FORM_CHO_TIEP_NHAN)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_CHO_CHUYEN_LUU_TRU -> queryHoSoLuuTruByIdentifier(
                key, Map.of(HOSOLUUTRU_CHUYENLUUTRU_FIELD, List.of("", HoSoLuuTruLifecycleStatus.CHUYENLUUTRU_CHUA)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_DA_CHUYEN_LUU_TRU -> queryHoSoLuuTruByIdentifier(
                key, Map.of(HOSOLUUTRU_CHUYENLUUTRU_FIELD, List.of(HoSoLuuTruLifecycleStatus.CHUYENLUUTRU_DA)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_CHUYEN_LTLS_0 -> queryHoSoLuuTruByIdentifier(
                key, Map.of(HOSOLUUTRU_CHUYENLUUTRUTINH_FIELD,
                    List.of("", HoSoLuuTruLifecycleStatus.CHUYENLUUTRUTINH_THU_HOI)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_CHUYEN_LTLS_2 -> queryHoSoLuuTruByIdentifier(
                key, Map.of(HOSOLUUTRU_CHUYENLUUTRUTINH_FIELD, List.of(HoSoLuuTruLifecycleStatus.CHUYENLUUTRUTINH_CHO_DUYET)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_0 -> queryPhieuYeuCau(
                key, Map.of("status", List.of(HoSoLuuTruLifecycleStatus.PHIEU_CHO_GIAI_QUYET)), request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_1 -> queryPhieuYeuCau(
                key, Map.of("status", List.of(HoSoLuuTruLifecycleStatus.PHIEU_CHAP_NHAN)), request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_2 -> queryPhieuYeuCau(
                key, Map.of("status", List.of(HoSoLuuTruLifecycleStatus.PHIEU_NGUOI_MUON_THU_HOI)), request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_3 -> queryPhieuYeuCau(
                key, Map.of("status", List.of(HoSoLuuTruLifecycleStatus.PHIEU_KHONG_CHAP_NHAN)), request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_13 -> queryPhieuYeuCau(
                key, Map.of("status", List.of(
                    HoSoLuuTruLifecycleStatus.PHIEU_CHAP_NHAN, HoSoLuuTruLifecycleStatus.PHIEU_KHONG_CHAP_NHAN)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_DANG_MUON -> queryPhieuYeuCau(
                key, Map.of("status", List.of(HoSoLuuTruLifecycleStatus.PHIEU_CHAP_NHAN),
                    HOSOLUUTRU_STATUS1_FIELD, List.of("", HoSoLuuTruLifecycleStatus.PHIEU_DANG_MUON)),
                request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_QUA_HAN -> queryPhieuYeuCauQuaHan(key, request.getItems(), pageable);
            case VIEW_HOSOLUUTRU_PHIEU_YEU_CAU_DA_TRA -> queryPhieuYeuCau(
                key, Map.of(HOSOLUUTRU_STATUS1_FIELD, List.of(HoSoLuuTruLifecycleStatus.PHIEU_DA_TRA)),
                request.getItems(), pageable);
            case VIEW_VBDEN_TRASH ->
                queryVbdenTrash(vbdenTrashBixoaFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_TRASH_REVOKED ->
                queryVbdenTrash(vbdenTrashBithuhoiFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_TRASH_REQUEST_FORM ->
                queryVbdenTrash(vbdenTrashRequestFormFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CAPTRENGIAO -> queryVbdenOrgView(dbType, captrengiaoFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DENNOIBO -> queryVbdenOrgView(dbType, dennoiboFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_QUAMANGNOIBO -> queryVbdenOrgView(dbType, quamangnoiboFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_LIENTHONG -> queryVbdenOrgView(dbType, lienthongFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DENDIENTU -> queryVbdenOrgView(dbType, dendientuFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DENQUAMANG -> queryVbdenOrgView(dbType, denquamangFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_TUCHOIDENQUAMANG -> queryVbdenOrgView(dbType, tuChoiDenQuaMangFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_TOANBOVANBAN ->
                queryVbdenOrgView(dbType, toanbovanbanFilter(request), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_PHAIXULY_CUATOCHUC ->
                queryVbdenOrgView(dbType, phaixulyCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_QUAHAN_CUATOCHUC ->
                queryVbdenOrgView(dbType, quahanCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DAXULY_QUAHAN_CUATOCHUC ->
                queryVbdenOrgView(dbType, daxulyQuahanCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DAXULY_DUNGHAN_CUATOCHUC ->
                queryVbdenOrgView(dbType, daxulyDunghanCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_SAPTOIHAN_CUATOCHUC ->
                queryVbdenOrgView(dbType, saptoihanCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DENHAN_CUATOCHUC ->
                queryVbdenOrgView(dbType, denhanCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CHOPHANXULY ->
                queryVbdenOrgView(dbType, chophanxulyFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CUATOCHUC -> queryVbdenOrgView(dbType, cuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DAXULY_CUATOCHUC ->
                queryVbdenOrgView(dbType, daxulyCuatochucFilter(), request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_KHONGVAOSO ->
                queryVbdenOrgView(dbType, khongvaosoFilter(), request.getItems(), keyword, keywordColumns, pageable);
            // dxl 14-45: N07 (Chờ phân phối) - user_distributor!="".
            case VIEW_VBDEN_CHOPHANPHOI ->
                queryVbdenPersonalQueue(dbType, "user_distributor", key, request.getItems(), keyword, keywordColumns, pageable);
            // 09/09/2026 (SoLuongCanXuLy) - xem javadoc VIEW_VBDEN_DENNOIBO_CHOXULY/VIEW_VBDEN_PHUTRACH_CHUAXEM.
            case VIEW_VBDEN_DENNOIBO_CHOXULY ->
                queryVbdenPersonalQueue(dbType, "user_receiver", key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_PHUTRACH_CHUAXEM ->
                queryVbdenPersonalQueue(dbType, "user_manage", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 2052-2077: "Đã phân phối" - user_distributed!="".
            case VIEW_VBDEN_DAPHANPHOI ->
                queryVbdenPersonalQueue(dbType, "user_distributed", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 81-113: N10 Phối hợp - user_coordinate!="".
            case VIEW_VBDEN_PHOIHOP ->
                queryVbdenPersonalQueue(dbType, "user_coordinate", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 115-140: N10 Đã phối hợp - user_coordinated!="".
            case VIEW_VBDEN_DAPHOIHOP ->
                queryVbdenPersonalQueue(dbType, "user_coordinated", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 47-79: N10 Xem để biết - user_to_read!="".
            case VIEW_VBDEN_XEMDEBIET ->
                queryVbdenPersonalQueue(dbType, "user_to_read", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 142-167: N10 Đã xem để biết - user_to_readed!="".
            case VIEW_VBDEN_DAXEMDEBIET ->
                queryVbdenPersonalQueue(dbType, "user_to_readed", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 743-768: N10 Theo dõi - user_track!="". Ho tro "tim kiem nang cao" (D.44) - xem
            // buildAdvancedSearchFilter. "theodoi_sort_thoihan" alias CUNG dieu kien (xem hang so).
            case VIEW_VBDEN_THEODOI, VIEW_VBDEN_THEODOI_SORT_THOIHAN ->
                queryVbdenPersonalQueue(
                    dbType, "user_track", key, request.getItems(), keyword, keywordColumns,
                    buildAdvancedSearchFilter(request.getSearch()), pageable);
            // dxl 770-795: N10 Đã theo dõi - user_tracked!="". Ho tro "tim kiem nang cao" (D.44).
            case VIEW_VBDEN_DATHEODOI ->
                queryVbdenPersonalQueue(
                    dbType, "user_tracked", key, request.getItems(), keyword, keywordColumns,
                    buildAdvancedSearchFilter(request.getSearch()), pageable);
            // N20 (07/09/2026): xem javadoc VIEW_VBDEN_VANBANCANTHEODOI.
            case VIEW_VBDEN_VANBANCANTHEODOI ->
                queryVbdenPersonalQueue(
                    dbType, "tasks", key, request.getItems(), keyword, keywordColumns,
                    buildAdvancedSearchFilter(request.getSearch()), pageable);
            // dxl 1879-1904: N10 Đã chuyển phối hợp - user_coordinated_movement!="".
            case VIEW_VBDEN_DACHUYENPHOIHOP ->
                queryVbdenPersonalQueue(dbType, "user_coordinated_movement", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 169-194: N06 Đã chuyển xử lý - user_delivered!="".
            case VIEW_VBDEN_DACHUYENXULY ->
                queryVbdenPersonalQueue(dbType, "user_delivered", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 196-221: user_processed!="" - CHINH LA vwDocument_DaXuLy da co, tai dung.
            case VIEW_VBDEN_DAKETTHUCXULY -> queryDocumentsDaXuLy(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 618-642: N06 Đang xử lý - (status="1"|status="2") & key=user_processing.
            case VIEW_VBDEN_DANGXULY ->
                queryVbdenPersonalQueueWithStatus(
                    dbType, "user_processing",
                    List.of(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY, VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY),
                    key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 1161-1189: N06 Đã kết thúc XL quá hạn - status="3" & key=user_processed.
            case VIEW_VBDEN_DAKETTHUCXULY_QUAHAN ->
                queryVbdenPersonalQueueWithStatus(
                    dbType, "user_processed", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY),
                    key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 1191-1218: N06 Đã chuyển XL quá hạn - (status="1"|status="3") & key=user_delivered.
            case VIEW_VBDEN_DACHUYENXULY_QUAHAN ->
                queryVbdenPersonalQueueWithStatus(
                    dbType, "user_delivered",
                    List.of(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY, VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY),
                    key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 223-251: N11 Chủ trì quá hạn - (status="1"|status="3") & user_process!="".
            case VIEW_VBDEN_CHUTRIQUAHAN ->
                queryVbdenPersonalQueueWithStatus(
                    dbType, "user_process",
                    List.of(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY, VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY),
                    key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 2532-2557: N11 Văn bản trả lại - user_returned!="".
            case VIEW_VBDEN_VANBANTRALAI ->
                queryVbdenPersonalQueue(dbType, "user_returned", key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 1134-1159: N11 Đã thu hồi - user_recalled!="".
            case VIEW_VBDEN_DATHUHOI ->
                queryVbdenPersonalQueue(dbType, "user_recalled", key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CHUTRI_GROUP ->
                queryChuTriGroup(dbType, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 1220-1247: "toan bo VB den cua ca nhan" - union 9 field ca nhan, khong dieu kien status/ngay.
            case VIEW_VBDEN_CUACANHAN ->
                queryVbdenPersonalQueue(dbType, VBDEN_CUACANHAN_FIELDS, key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 2504-2530: "sap den han (ca nhan)" - union 3 field ca nhan + status!=3/4 + 0<=deadline-today<=2 + document_type!=Giay moi.
            case VIEW_VBDEN_SAPDENHAN_CUACANHAN ->
                querySapDenHanCuaCaNhan(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            // N08 (31/08/2026) - xem javadoc XINYKIEN_DOCUMENT_TYPE: 14 view, document_type RIENG,
            // bo qua dbType tham so truyen vao (giong tien le QuaTrinhXuLy/vbden_res).
            case VIEW_VBDEN_CHOCHOYKIEN ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESS_FIELD, OPINION_FLAG_TVUB, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CHOCHOYKIEN_NOI_BO ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESS_FIELD, OPINION_FLAG_TVNB, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CHOCHOYKIENBCH ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESS_FIELD, OPINION_FLAG_BCH, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CHOCHOYKIENBTV ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESS_FIELD, OPINION_FLAG_BTV, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_CHOCHOYKIENTTDU ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESS_FIELD, OPINION_FLAG_TTDU, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHOYKIEN ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESSED_FIELD, OPINION_FLAG_TVUB, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHOYKIEN_NOI_BO ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESSED_FIELD, OPINION_FLAG_TVNB, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHOYKIENBCH ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESSED_FIELD, OPINION_FLAG_BCH, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHOYKIENBTV ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESSED_FIELD, OPINION_FLAG_BTV, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHOYKIENTTDU ->
                queryXinYkienQueue(XINYKIEN_USER_PROCESSED_FIELD, OPINION_FLAG_TTDU, key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 3118-3143: KHONG co dieu kien co - NGOAI LE duy nhat, giu nguyen dung DXL.
            case VIEW_VBDEN_DACHUYENCHOYKIEN ->
                queryXinYkienQueue(XINYKIEN_USER_DELIVERED_FIELD, null, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHUYENCHOYKIENBCH ->
                queryXinYkienQueue(XINYKIEN_USER_DELIVERED_FIELD, OPINION_FLAG_BCH, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHUYENCHOYKIENBTV ->
                queryXinYkienQueue(XINYKIEN_USER_DELIVERED_FIELD, OPINION_FLAG_BTV, key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_VBDEN_DACHUYENCHOYKIENTTDU ->
                queryXinYkienQueue(XINYKIEN_USER_DELIVERED_FIELD, OPINION_FLAG_TTDU, key, request.getItems(), keyword, keywordColumns, pageable);
            // DT-002 Slice dau: xem javadoc VIEW_DUTHAO_USER_CREATOR o tren ve pham vi/ly do hoan
            // 16+ view con lai. Cap nhat 24/08/2026 (yeu cau nguoi dung "tao bang rieng, de doc lap
            // du lieu"): duthao chuyen sang bang doc lap duthao_documents - KHONG con tai dung
            // queryVbdenPersonalQueue*/documentRepository (bang "documents" dung chung) duoc nua,
            // phai co method rieng doc DuThaoDocumentRepository (xem queryDuThaoPersonalQueue*).
            case VIEW_DUTHAO_USER_CREATOR, VIEW_DUTHAO_USER_INVOKED ->
                queryDuThaoPersonalQueue(
                    dbType, DUTHAO_USER_CREATOR_FIELD, key, request.getItems(), keyword, keywordColumns, pageable);
            // 09/09/2026 (cutover dashboard - xem javadoc VIEW_DUTHAO_TOANBO_TOCHUC): org-scope, KHONG
            // phai personal - identifier tu resolveCurrentIdentifier() (server-resolved, cung co che
            // vbden/vbdi), KHONG dung "key"/request.getKeys() (route nay FE khong gui keys).
            case VIEW_DUTHAO_TOANBO_TOCHUC -> {
                VbdenOrgViewFilter advancedFilter = buildDuThaoAdvancedSearchFilter(request.getSearch());
                String identifier = resolveCurrentIdentifier();
                VbdenOrgViewFilter filter = (advancedFilter != null ? advancedFilter.toBuilder() : VbdenOrgViewFilter.builder())
                    .orgFieldNames(DUTHAO_TOANBO_TOCHUC_ORG_FIELDS)
                    .orgValue(identifier)
                    .keyword(keyword)
                    .keywordColumns(keywordColumns)
                    .build();
                yield (identifier == null || identifier.isBlank())
                    ? emptyPage(pageable)
                    : queryDuThaoQueueByFilter(dbType, filter, request.getItems(), pageable);
            }
            // 27/08/2026 (port route FE "vbdi/xuly/chocapkyso"): TRÙNG TÊN THẬT với view "chờ vào
            // sổ" của module vbdi (dxl view_vanbandi.dxl dòng ~276-306) — 2 view Domino HOÀN TOÀN
            // KHÁC NHAU (2 database khác nhau) nhưng CÙNG tên "vwCreator_Drafting". Route FE thật
            // (`SystemsPageDi.tsx`, cả `chobanhanh` LẪN `chocapkyso` — 2 route CÙNG dùng
            // `ItemsList.jsx` với `dom_view_name="vwCreator_Drafting"`) gửi ĐÚNG viewname này — đã
            // xác nhận với người dùng (AskUserQuestion): port Y NGUYÊN hành vi đang chạy hôm nay
            // (dùng view "chờ vào sổ" cho CẢ 2 route), KHÔNG đổi sang view chuyên biệt
            // `vwCreator_Waiting_Signed` (bị comment out trong FE, tắt do lỗi/quên bật lại chứ
            // không phải chủ ý — xem docs/analysis/02-...vbdi-fe-sang-java.md). Vì {@code
            // execute(dbType, request)} dispatch THUẦN theo viewname (dbType không dùng để routing —
            // xem javadoc lớp), PHẢI tự phân nhánh theo dbType ngay trong case này.
            case VIEW_DUTHAO_CREATOR_DRAFTING -> "vbdi".equalsIgnoreCase(dbType)
                ? queryVbdiWaitingForRegistration(dbType, request.getItems(), pageable)
                : queryDuThaoPersonalQueueWithStatus(
                    dbType, DUTHAO_USER_CREATOR_FIELD, List.of(DUTHAO_STATUS_DRAFTING), key,
                    request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_USERCREATOR_PROCESS_HANDLER_SUBMIT ->
                queryDuThaoCreatorProcessHandlerSubmit(
                    dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            // ---- DT-002 port loat 2 (25/08/2026): cac view con lai, xem khoi comment constants ----
            case VIEW_DUTHAO_SIGNER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", DUTHAO_STATUS_SIGNER_WAITING)).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_PRE_SIGNER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_PRE_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.PRE_SIGN_SUBMIT))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_USER_RETURN_DRAFT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.RETURNED)))
                    .requireEmptyFieldName(DUTHAO_RETURN_DOCOUT_FOR_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_CREATOR_DOCUMENTOUT_SUBMITED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.PUBLISHED))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Khoa DXL la author - don gian hoa thanh user_creator theo dung quyet dinh da ghi o
            // javadoc VIEW_DUTHAO_USER_INVOKED (duthao khong co luong tao ho).
            // "vwDocumentOut_Submited" that (KHAC view tren) - xem javadoc VIEW_DUTHAO_DOCUMENTOUT_SUBMITED.
            case VIEW_DUTHAO_DOCUMENTOUT_SUBMITED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.PUBLISHED)))
                    .requireEmptyFieldName(DUTHAO_XOA_VAN_BAN_DI_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // 4 nhanh noi bo cua "vwChoDuyetKyBanHanh"/"vwChoDuyetKyBanHanhMC" - xem javadoc hang so.
            case VIEW_DUTHAO_CHODUYETKYBANHANH_SIGNER -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", DUTHAO_STATUS_SIGNER_WAITING))
                    .requireEmptyFieldName(DUTHAO_MC_MAHS_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_CHODUYETKYBANHANH_HANDLER -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_OTHER_PROCESS_HANDLER_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DUTHAO_STATUS_OTHER_HANDLER_SUBMIT)))
                    .requireEmptyFieldName(DUTHAO_MC_MAHS_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_CHODUYETKYBANHANHMC_SIGNER -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", DUTHAO_STATUS_SIGNER_WAITING))
                    .requireNonEmptyFieldName(DUTHAO_MC_MAHS_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_CHODUYETKYBANHANHMC_HANDLER -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_OTHER_PROCESS_HANDLER_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DUTHAO_STATUS_OTHER_HANDLER_SUBMIT)))
                    .requireNonEmptyFieldName(DUTHAO_MC_MAHS_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_DOCUMENTOUT_PUBLISHED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", DUTHAO_STATUS_DOCOUT_PUBLISHED)).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_CREATOR_SIGNER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", DUTHAO_STATUS_SIGNER_WAITING))
                    .requireNonEmptyFieldName(DUTHAO_USER_SIGNER_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Header cua cot khoa ghi 'user_signer' nhung cong thuc that su la user_creator (artefact).
            case VIEW_DUTHAO_CREATOR_PRE_SIGNER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.PRE_SIGN_SUBMIT)))
                    .requireNonEmptyFieldName(DUTHAO_USER_PRE_SIGNER_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_USER_PROCESS_HANDLER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_PROCESS_HANDLER_PLAIN_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.FORWARDED_FOR_APPROVAL))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_USER_SIGNER_RETURN_DRAFT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.RETURNED))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Khoa la union user_process_handler (scalar) : explode(user_processed;";") - member match
            // boundary-LIKE khop dung ca 2 dang luu.
            case VIEW_DUTHAO_USER_HANDLER_RETURN_DRAFT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgListFieldNames(List.of(
                        DUTHAO_USER_PROCESS_HANDLER_PLAIN_FIELD, DUTHAO_USER_PROCESSED_LIST_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.RETURNED))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_SIGNER_DOCUMENTOUT_SUBMITED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.PUBLISHED))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Header ghi 'user_signer' nhung khoa that su la user_creator (artefact, dxl 624-625).
            case VIEW_DUTHAO_CREATOR_PROCESS_HANDLER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.FORWARDED_FOR_APPROVAL)))
                    .requireNonEmptyFieldName(DUTHAO_USER_PROCESS_HANDLER_PLAIN_FIELD).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Cong thuc cot that su (sau khoi REM) = uniq(user_creator) : explode(user_processed)
            // - KHONG co user_process_handler du selection co nhac den (dxl 657-664).
            case VIEW_DUTHAO_USER_PROCESS_HANDLER_FORWARD -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgListFieldNames(List.of(DUTHAO_USER_CREATOR_FIELD, DUTHAO_USER_PROCESSED_LIST_FIELD))
                    .includeFieldValues(Map.of("status", List.of(
                        DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.FORWARDED_FOR_APPROVAL))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_BY_HANDLER_DOCUMENTOUT_SUBMITED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgListFieldNames(List.of(
                        DUTHAO_USER_PROCESS_HANDLER_PLAIN_FIELD, DUTHAO_USER_PROCESSED_LIST_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.PUBLISHED))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Khong loc status - selection chi la pre_signer_signed = "1" (dxl 726).
            case VIEW_DUTHAO_PRE_SIGNER_SIGNED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_PRE_SIGNER_FIELD))
                    .includeFieldValues(Map.of(DUTHAO_PRE_SIGNER_SIGNED_FIELD, List.of("1"))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_USER_DOCUMENTOUT_PUBLISHED -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgListFieldNames(List.of(
                        DUTHAO_USER_SIGNER_FIELD, DUTHAO_USER_PROCESS_HANDLER_PLAIN_FIELD,
                        DUTHAO_USER_PROCESSED_LIST_FIELD))
                    .includeFieldValues(Map.of("status", DUTHAO_STATUS_DOCOUT_PUBLISHED)).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // Van thu tra lai: retfor="2" la dieu kien IN tren field dong (khong phai requireEmpty).
            case VIEW_DUTHAO_CREATOR_DOCUMENTOUT_RETURN -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgListFieldNames(List.of(
                        DUTHAO_USER_CREATOR_FIELD, DUTHAO_USER_SIGNER_FIELD,
                        DUTHAO_USER_OTHER_PROCESS_HANDLER_FIELD))
                    .includeFieldValues(Map.of(
                        "status", List.of(DuThaoStatus.RETURNED),
                        DUTHAO_RETURN_DOCOUT_FOR_FIELD, List.of("2"))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_SIGNER_DOCUMENTOUT_RETURN -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of(
                        "status", List.of(DuThaoStatus.PENDING_SIGNATURE),
                        DUTHAO_RETURN_DOCOUT_FOR_FIELD, List.of("1"))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_SIGNER_DOCUMENTOUT_WAITINGSUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgFieldNames(List.of(DUTHAO_USER_SIGNER_FIELD))
                    .includeFieldValues(Map.of("status", List.of(DuThaoStatus.SIGNED_WAITING_SUBMIT))).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            // dxl 1874-1903: chi co user_other_processed_handler != "" - KHONG loc status.
            case VIEW_DUTHAO_USERCREATOR_PROCESSED_HANDLER_SUBMIT -> queryDuThaoPersonalQueueByFilter(dbType,
                VbdenOrgViewFilter.builder()
                    .orgListFieldNames(List.of(DUTHAO_USER_OTHER_PROCESSED_HANDLER_FIELD)).build(),
                key, request.getItems(), keyword, keywordColumns, pageable);
            case VIEW_DUTHAO_USER_OTHER_PROCESS_HANDLER_SUBMIT ->
                queryDuThaoPersonalQueueWithStatus(
                    dbType, DUTHAO_USER_OTHER_PROCESS_HANDLER_FIELD, List.of(DUTHAO_STATUS_OTHER_HANDLER_SUBMIT),
                    key, request.getItems(), keyword, keywordColumns, pageable);
            // DT-002 (26/08/2026), chogopy: "Cho phoi hop" - AND don gian, khong loc "all".
            // Mo rong BL-240: "vwuser_coordinator" TRUNG ten voi view hosocv (VIEW_HOSOCV_COORDINATOR)
            // - dispatch THUAN theo viewname (khong theo dbType) nen phai tu if/else NGAY TRONG case,
            // dung khuon da co (vd VIEW_DUTHAO_CREATOR_DRAFTING) khi 2 module trung ten view.
            case VIEW_DUTHAO_USER_COORDINATOR -> "hosocv".equalsIgnoreCase(dbType)
                ? queryHoSoCVListMember(dbType, HOSOCV_USER_COORDINATOR_FIELD, key, request.getItems(), pageable)
                : queryDuThaoPersonalQueueByFilter(dbType,
                    VbdenOrgViewFilter.builder()
                        .orgFieldNames(List.of(DUTHAO_USER_COORDINATOR_FIELD))
                        .excludeFieldValues(Map.of("status", DUTHAO_COORDINATOR_DONE_STATUSES)).build(),
                    key, request.getItems(), keyword, keywordColumns, pageable);
            // "Da phoi hop" - OR-group thuc su, xem javadoc VIEW_DUTHAO_USER_COORDINATED.
            case VIEW_DUTHAO_USER_COORDINATED ->
                queryDuThaoCoordinatedQueue(dbType, key, request.getItems(), keyword, keywordColumns, pageable);
            // DT-007 (26/08/2026) - lich su xu ly 1 du thao cu the, khoa la id du thao (khong phai
            // username) - xem javadoc VIEW_DUTHAO_GROUP_PROCESS_BY_DRAFT_ID.
            case VIEW_DUTHAO_GROUP_PROCESS_BY_DRAFT_ID ->
                queryDraftProcessHistory(key, request.getItems(), pageable);
            // BL-250 (VBCDDH Core), bo sung 08/09/2026 (2) - Nhom 1: vwNhiem_Vu_By_Donvithuchienhide*
            // (24 view, dxl xac nhan - xem javadoc hang so). extraPredicateSql = null nghia khong loc
            // them (view "Toan bo" bare).
            case VIEW_VBCDDH_NV_DVTH -> queryVbcddhByDvth(key, null, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUAXULY ->
                queryVbcddhByDvth(key, VBCDDH_TT_CHUA_EQ + " AND " + VBCDDH_NOT_TRALAI, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUAXULY_TRONGHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_CHUA_EQ + " AND " + VBCDDH_QUAHAN_TRONGHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUAXULY_QUAHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_CHUA_EQ + " AND " + VBCDDH_QUAHAN_QUAHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DANGXULY ->
                queryVbcddhByDvth(key, VBCDDH_TT_DANG_EQ + " AND " + VBCDDH_NOT_TRALAI, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DANGXULY_TRONGHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_DANG_EQ + " AND " + VBCDDH_QUAHAN_TRONGHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DANGXULY_QUAHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_DANG_EQ + " AND " + VBCDDH_QUAHAN_QUAHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DAXULY ->
                queryVbcddhByDvth(key, VBCDDH_TT_DA_EQ + " AND " + VBCDDH_NOT_TRALAI, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DAXULY_TRONGHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_DA_EQ + " AND " + VBCDDH_QUAHAN_TRONGHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DAXULY_QUAHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_DA_EQ + " AND " + VBCDDH_QUAHAN_QUAHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_1DAY -> queryVbcddhByDvth(key,
                VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_NOT_TRALAI + " AND " + VBCDDH_HANXULY_1DAY,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_2DAY -> queryVbcddhByDvth(key,
                VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_NOT_TRALAI + " AND " + VBCDDH_HANXULY_2DAY,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CREATE_2DAY -> queryVbcddhByDvth(key,
                VBCDDH_NOT_TRALAI + " AND " + VBCDDH_CREATED_2DAY_AGO, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUA_HOAN_THANH, VIEW_VBCDDH_NV_DVTH_CHUAHOANTHANH ->
                // dxl: 2 view TEN KHAC NHAU nhung CUNG cong thuc (tinhtrang!="Đã thực hiện" hoac
                // (="Chưa thực hiện"|"Đang thực hiện") - tuong duong voi chi 3 gia tri tinhtrang co the).
                queryVbcddhByDvth(key, VBCDDH_TT_NOT_DA + " AND " + VBCDDH_NOT_TRALAI, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUA_HOAN_THANH_QUA_HAN, VIEW_VBCDDH_NV_DVTH_CHUAHOANTHANH_QUAHAN ->
                queryVbcddhByDvth(key,
                    VBCDDH_TT_NOT_DA + " AND " + VBCDDH_QUAHAN_QUAHAN + " AND " + VBCDDH_NOT_TRALAI,
                    request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUAHOANTHANH_TRONGHAN -> queryVbcddhByDvth(key,
                VBCDDH_TT_NOT_DA + " AND " + VBCDDH_QUAHAN_TRONGHAN + " AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_XU_LY_LAI -> queryVbcddhByDvth(key,
                VBCDDH_TT_NOT_DA + " AND COALESCE(d.dynamic_fields->>'xulylai','') = '1' AND " + VBCDDH_NOT_TRALAI,
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_TRALAI -> queryVbcddhByDvth(key,
                "(" + VBCDDH_F_TRALAI + " = '1' OR COALESCE(d.dynamic_fields->>'nguoitralaihide','') != '')",
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUYENXULY -> queryVbcddhByDvth(key,
                "COALESCE(d.dynamic_fields->>'nguoichuyenxulyhide','') != ''", request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_CHUAPHOIHOP -> queryVbcddhByDvth(key,
                VBCDDH_PHOIHOP_CHUA + " AND " + VBCDDH_TT_NOT_DA, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DAPHOIHOP -> queryVbcddhByDvth(key, VBCDDH_PHOIHOP_DA, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_DVTH_DONDOC -> queryVbcddhByDvth(
                key, "COALESCE(d.dynamic_fields->>'dondoc','') = '1'", request.getItems(), pageable);

            // Nhom 2: vwNhiem_Vu_Ca_Nhan_Co_Quan* (23 view, khoa 2 cap [typeLoaiNhiemVu, danh tinh]).
            case VIEW_VBCDDH_NV_CNCQ -> queryVbcddhCaNhanCoQuan(request.getKeys(), null, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CHUAXULY ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_TT_CHUA_EQ, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_DANGXULY ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_TT_DANG_EQ, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_DAXULY ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_TT_DA_EQ, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_DAXULY_TRONGHAN -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_DA_EQ + " AND " + VBCDDH_F_QUAHAN + " != '1'", request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_TRALAI ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_F_TRALAI + " = '1'", request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_QUA_HAN ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_QUAHAN_QUAHAN, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_DAKETTHUC -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_DA_EQ + " AND COALESCE(d.dynamic_fields->>'ketthuc','') = '1'",
                request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CHUA_HOAN_THANH ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_TT_NOT_DA, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CHUA_HOAN_THANH_QUA_HAN -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA + " AND " + VBCDDH_QUAHAN_QUAHAN, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_DONDOC -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), "COALESCE(d.dynamic_fields->>'dondoc','') = '1'", request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_1DAY, VIEW_VBCDDH_NV_CNCQ_1NGAY -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_HANXULY_1DAY, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_2DAY, VIEW_VBCDDH_NV_CNCQ_2NGAY -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_HANXULY_2DAY, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_TRONGNGAY -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_HANXULY_TODAY, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_TRONGTUAN -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_HANXULY_TRONGTUAN, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_TRONGTHANG -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_HANXULY_TRONGTHANG, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_TRONGQUY -> queryVbcddhCaNhanCoQuan(
                request.getKeys(), VBCDDH_TT_NOT_DA_LOWER + " AND " + VBCDDH_HANXULY_TRONGQUY, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGNGAY ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_CREATED_TODAY, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CREATE_2NGAY ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_CREATED_2DAY_AGO, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGTUAN ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_CREATED_TRONGTUAN, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGTHANG ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_CREATED_TRONGTHANG, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CNCQ_CREATE_TRONGQUY ->
                queryVbcddhCaNhanCoQuan(request.getKeys(), VBCDDH_CREATED_TRONGQUY, request.getItems(), pageable);

            // Nhom 3: vwVan_Ban_Ca_Nhan_Co_Quan* (6 view, form=vanban, khoa UNION 6 field, KHONG loai).
            case VIEW_VBCDDH_VB_CNCQ_1 -> queryVbcddhVanBan(key, null, request.getItems(), pageable);
            case VIEW_VBCDDH_VB_CNCQ_CHUAPHANCONG -> queryVbcddhVanBan(
                key, VBCDDH_F_TINHTRANGPHANCONG + " = 'Chưa phân công'", request.getItems(), pageable);
            case VIEW_VBCDDH_VB_CNCQ_DAPHANCONG -> queryVbcddhVanBan(
                key, VBCDDH_F_TINHTRANGPHANCONG + " = 'Đã phân công'", request.getItems(), pageable);
            case VIEW_VBCDDH_VB_CNCQ_CHUAXULY -> queryVbcddhVanBan(key, VBCDDH_TT_CHUA_EQ, request.getItems(), pageable);
            case VIEW_VBCDDH_VB_CNCQ_DANGXULY -> queryVbcddhVanBan(key, VBCDDH_TT_DANG_EQ, request.getItems(), pageable);
            case VIEW_VBCDDH_VB_CNCQ_DAHOANTHANH ->
                queryVbcddhVanBan(key, VBCDDH_F_TINHTRANG + " = 'Đã hoàn thành'", request.getItems(), pageable);

            // 5 view doc lap.
            case VIEW_VBCDDH_NV_DA_XOA -> queryVbcddhByForm("nhiemvu_Deleted", null, request.getItems(), pageable);
            case VIEW_VBCDDH_VB_DA_XOA -> queryVbcddhByForm("vanban_Deleted", null, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_BY_IDVANBAN ->
                queryVbcddhByAnyFieldValue(VBCDDH_NV_BY_IDVANBAN_FIELDS, key, request.getItems(), pageable);
            case VIEW_VBCDDH_TIENDO_BY_IDNHIEMVU ->
                queryVbcddhTienDoByIdNhiemVu(key, request.getItems(), pageable);
            case VIEW_VBCDDH_NV_CO_QUAN ->
                queryVbcddhByDvthFields(VBCDDH_FORM_NHIEMVU, List.of("coquanid"), key, null, request.getItems(), pageable);

            default -> throw new IllegalArgumentException("Khong ho tro viewname: " + request.getViewname());
        };
    }

    private PaginationResponse<Map<String, Object>> queryDocumentsChoXuLy(
            String dbType, String username, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }

        Page<BusinessDocument> page = hasKeyword(keyword, keywordColumns)
            ? documentRepository.findPendingForUser(dbType, username.trim(), keyword.trim(), keywordColumns, pageable)
            : documentRepository.findPendingForUser(dbType, username.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * BL-210 (VBDen Core) — DoD (2): view "đã xử lý", đối xứng với {@code vwDocument_ChoXuLy} nhưng
     * dựa trên field động {@code user_processed} thay vì {@code user_process}.
     */
    private PaginationResponse<Map<String, Object>> queryDocumentsDaXuLy(
            String dbType, String username, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }

        Page<BusinessDocument> page = hasKeyword(keyword, keywordColumns)
            ? documentRepository.findProcessedForUser(dbType, username.trim(), keyword.trim(), keywordColumns, pageable)
            : documentRepository.findProcessedForUser(dbType, username.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Slice 7 (tim kiem/loc): chi coi la "co keyword" khi CA keyword lan keywordColumns deu hop le -
    // tranh goi nham overload keyword khi FE khong gui columns (vd man khong co o tim kiem).
    private boolean hasKeyword(String keyword, List<String> keywordColumns) {
        return keyword != null && !keyword.isBlank() && keywordColumns != null && !keywordColumns.isEmpty();
    }

    /**
     * BL-210 (VBDen Core) — DoD (4): tương đương view {@code QTXL_by_parentid} phía Domino — danh
     * sách document con (vd. bản ghi quá trình xử lý, {@code dbType="vbden_res"}) theo field động
     * {@code parentid} trỏ tới document cha (vd. văn bản đến).
     *
     * <p>Bổ sung 01/09/2026 (rà soát trước khi cutover FE N08): {@code ChoYKienXinYKienUseCase} ghi
     * bản ghi quá trình xử lý riêng vào BẢNG KHÁC ({@code vbden_xinykien_documents}, document_type
     * {@code vbden_xinykien_res} — xem javadoc lớp đó), KHÔNG phải bảng {@code vbden_res_documents}
     * mà query dưới đọc — nên khi văn bản cha là "văn bản xin ý kiến giả", query đó LUÔN rỗng dù đã
     * có bản ghi cho ý kiến thật. Một {@code parentid} chỉ thuộc 1 trong 2 loại văn bản (không bao
     * giờ vừa là vbden thật vừa là xinykien) nên fallback sang bảng riêng KHI VÀ CHỈ KHI kết quả bảng
     * chính rỗng là an toàn (không trộn lẫn 2 nguồn, không đổi hành vi của mọi dbType khác).</p>
     *
     * <p>Cập nhật 05/09/2026 (yêu cầu người dùng "tách vbden_res sang bảng riêng"): {@code dbType}
     * cho view này LUÔN là {@code "vbden_res"} (hardcode ở {@code VbdenController}, xem javadoc lớp
     * ở trên) — vì bảng {@code documents} dùng chung KHÔNG còn dòng nào loại này sau migration
     * {@code V1.0.42}, phải đọc {@link VbdenResDocumentRepository} thay vì
     * {@code documentRepository.findByParentId} như trước. Fallback xin ý kiến ở trên GIỮ NGUYÊN.</p>
     */
    private PaginationResponse<Map<String, Object>> queryChildrenByParentId(
            String dbType, String parentId, String items, Pageable pageable) {
        if (parentId == null || parentId.isBlank()) {
            return emptyPage(pageable);
        }

        if (ChuyenXuLyVbdenUseCase.CHILD_DOCUMENT_TYPE.equalsIgnoreCase(dbType)) {
            Page<VbdenResDocument> page = vbdenResDocumentRepository.findByParentId(parentId.trim(), pageable);
            if (page.getTotalElements() == 0) {
                Page<XinYKienDocument> xinYKienPage = xinYKienDocumentRepository.findByAnyFieldContains(
                    ChoYKienXinYKienUseCase.CHILD_DOCUMENT_TYPE, List.of("parentid"), parentId.trim(), pageable);
                if (xinYKienPage.getTotalElements() > 0) {
                    List<Map<String, Object>> xinYKienContent = xinYKienPage.getContent().stream()
                        .map(document -> toFilteredDocumentFields(document, items))
                        .toList();
                    return toPaginationResponse(xinYKienContent, xinYKienPage);
                }
            }

            List<Map<String, Object>> content = page.getContent().stream()
                .map(document -> toFilteredDocumentFields(document, items))
                .toList();
            return toPaginationResponse(content, page);
        }

        Page<BusinessDocument> page = documentRepository.findByParentId(dbType, parentId.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * BL-220 (VBDi Core) — DoD (4): danh sách văn bản đi theo trạng thái (status tĩnh, không phải
     * field động) — {@code vwvandi_chobanhanh} (đã trình ký, chờ ban hành)/{@code vwvandi_dabanhanh}
     * (đã ban hành).
     */
    private PaginationResponse<Map<String, Object>> queryDocumentsByStatus(
            String dbType, String status, String items, Pageable pageable) {
        Page<BusinessDocument> page = documentRepository.findByStatus(dbType, status, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }
    /**
     * BL-220 (VBDi Core) — DoD (4), tách bảng riêng: danh sách văn bản đi theo trạng thái (status tĩnh)
     * từ bảng vật lý ĐỘC LẬP {@code vbdi_documents} (xem javadoc {@link VbdiDocument}) — KHÔNG dùng
     * {@link #queryDocumentsByStatus} (đọc bảng {@code documents} dùng chung, sẽ không bao giờ thấy
     * {@code vbdi} nữa sau khi tách).
     */
    private PaginationResponse<Map<String, Object>> queryVbdiByStatus(
            String dbType, String status, String items, Pageable pageable) {
        Page<VbdiDocument> page = vbdiDocumentRepository.findByStatus(dbType, status, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 27/08/2026 (port route FE "vbdi/xuly/chocapkyso", đúng tên biến vwCreator_Drafting — xem
     * ghi chú tại case dispatch {@code VIEW_DUTHAO_CREATOR_DRAFTING}): "Văn bản đi chờ vào sổ" (dxl
     * {@code view_vanbandi.dxl} dòng ~276-306) — {@code Form="DocumentOut" & status="0" &
     * mc_madv=""}, khoá theo {@code unit_publish_identifier} HOẶC {@code
     * unit_publish_identifier_other} khớp identifier người dùng hiện tại (đúng key formula Domino:
     * {@code unit_publish_identifier + unit_publish_identifier_other}, exploded).
     *
     * <p>Domino status {@code "0"} = {@link VbdiStatus#DRAFT} — đúng mapping đã dùng ở
     * {@code ChuyenThanhVanBanDiUseCase#markDraftAsPublished} (tạo vbdi mới với
     * {@code status="0"}/{@code VbdiStatus.DRAFT}).</p>
     *
     * <p>An ninh: KHÔNG dùng {@code request.getKeys()} do client gửi — luôn tự resolve
     * {@code identifier} từ user đang đăng nhập qua {@link ResolveIdentifierByLevelUseCase}, đúng
     * pattern {@link #queryVbdenOrgView}/{@code resolveCurrentIdentifier()} (an toàn hơn, không cho
     * spoof identifier).</p>
     */
    private PaginationResponse<Map<String, Object>> queryVbdiWaitingForRegistration(
            String dbType, String items, Pageable pageable) {
        String identifier = resolveCurrentIdentifier();
        if (identifier == null) {
            return emptyPage(pageable);
        }
        Page<VbdiDocument> page = vbdiDocumentRepository.findWaitingForRegistrationByIdentifier(
            dbType, VbdiStatus.DRAFT, identifier, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 28/08/2026 (port route FE "vbdi/xuly/cq_toanboso" — "Sổ văn bản đi cơ quan", CÙNG dùng bởi 3
     * route anh em "toanboso_all"/"dv_toanboso"/"pb_toanboso", cả 4 route gửi CHUNG dom_view_name
     * {@code vwCreator_DocumentOut_TextBook} — xem {@code SystemsPageDi.tsx} dòng ~454-507): view
     * Domino thật {@code vwCreator_DocumentOut_TextBook} (dxl {@code view_vanbandi.dxl} dòng
     * ~1732-1778, {@code Form="DocumentOut" & status="2"} — đã ban hành), khoá bởi identifier
     * categorized theo công thức {@code unit_publish_identifier + @If(...)} (xem javadoc
     * {@link VbdiDocumentRepository#findIssuedByPublishOrCreatorIdentifier}).
     *
     * <p>4 route anh em CHỈ khác nhau ở CẤP tổ chức mà FE tự tính {@code key} gửi lên
     * ({@code getIdentifierByLevel(currentUser?.identifiers, level)} — level 1/3/4/5, xem
     * {@code ItemsList.jsx} dòng ~208-216, module {@code vbdi/chobanhanh}) — {@code execute(dbType,
     * request)} KHÔNG nhận được level/actiontype (chỉ viewname chung cho cả 4 route), nên PHẢI dùng
     * đúng identifier client gửi (khác hẳn {@link #queryVbdiWaitingForRegistration} chỉ có 1
     * route/1 cấp nên tự resolve được, không cần tin request.getKeys()). Để KHÔNG tin mù giá trị
     * client gửi (rủi ro spoof identifier của tổ chức khác), validate identifier đó PHẢI là 1 trong
     * các tổ tiên THẬT của chính user hiện tại ({@code identifiers}, LÁ→GỐC — xem
     * {@link #isOwnIdentifier}) — đúng những gì {@code getIdentifierByLevel} vốn dĩ CHỈ có thể tính
     * ra từ chuỗi tổ tiên của CHÍNH user đó, nên validate này không chặn nhầm ca hợp lệ nào, chỉ
     * chặn giá trị bị chỉnh sửa/giả mạo.</p>
     *
     * <p>Sắp xếp: dxl gốc sort theo {@code promulgation_date DESC, document_number DESC} (2 field
     * ĐỘNG) — theo ĐÚNG simplification đã áp dụng cho MỌI query vbdi khác trong lớp này
     * ({@link #queryVbdiByStatus}/{@link #queryVbdiWaitingForRegistration}, xem
     * {@code PostgresVbdiDocumentRepository}): sort theo {@code last_modified_on DESC} (cột tĩnh)
     * thay vì field JSONB động — KHÔNG phải hồi quy mới, giữ nhất quán với các view vbdi đã port
     * trước (rủi ro format ngày lưu trong JSONB không đảm bảo sortable, chưa có tiền lệ ép kiểu
     * trong lớp này).</p>
     */
    private PaginationResponse<Map<String, Object>> queryVbdiDocumentOutTextBook(
            String dbType, String identifier, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(
            dbType, VbdiStatus.ISSUED, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (port 4 tab theo ngày — dxl {@code view_vanbandi.dxl}, xem javadoc
     * {@link VbdiDocumentRepository#findIssuedByCreatorOrUnitIdentifier}): {@code homqua}/
     * {@code vwCreator_DocumentOut_ToDay}/{@code trongtuan}/{@code trongthang} có formula VIẾT
     * CHẾT ngày cụ thể ({@code @Date(2026;8;20)}...) — sửa lại bởi 1 agent/thao tác định kỳ mỗi ngày
     * (bằng chứng: {@code <modified>} của mỗi view trong dxl luôn SÁT ngày hiện tại lúc export, xem
     * {@code docs/views_domino/view_vanbandi.dxl} dòng ~499/2030/2061/2107) — ý định THẬT là tương
     * đối theo ngày hệ thống, KHÔNG PHẢI 1 ngày cố định. Port ĐÚNG Ý ĐỊNH: tính {@link LocalDate#now()}
     * tại thời điểm query (cùng quyết định đã áp dụng cho vbden — xem javadoc
     * {@code VbdenOrgViewFilter#minCreatedOn}), không copy nguyên literal ngày trong dxl (sẽ luôn SAI
     * ngay khi qua ngày khác). "Trong tuần"/"Trong tháng" dùng tuần/tháng ISO (Thứ 2 → Chủ nhật,
     * ngày 1 → ngày cuối tháng) — khớp khoảng dxl mẫu (8/17-8/23 là đúng 1 tuần Thứ 2→CN chứa
     * 8/17/2026, 8/1-8/31 là đúng tháng 8).
     */
    /**
     * 11/09/2026 (port "vbdi/thungrac/thungrac" — SỬA LẠI, xem javadoc {@link #VIEW_VBDI_TRASH} và
     * {@link VbdiDocumentRepository#findTrashedByUsernameOrDeleter}): danh sách văn bản đi đã xoá
     * mềm, khoá theo view Domino thật {@code vwCreator_DocumentOut_Delete} — categorized theo
     * {@code user_creator + ";" + unit_creator_identifier + ";" + delete_people} (dxl
     * {@code view_vanbandi.dxl} dòng 843-845, @Explode), FE gửi {@code keys=[username hiện tại]}
     * ({@code ItemsList.jsx#chobanhanh} dòng ~200-212, {@code actiontype=="thungrac" -> key =
     * currentUser.username}) — tức lọc theo văn bản mà user hiện tại là NGƯỜI TẠO
     * ({@code user_creator}) HOẶC NGƯỜI ĐÃ XOÁ ({@code delete_people}) (nhánh
     * {@code unit_creator_identifier} so khớp username hầu như không bao giờ đúng — identifier tổ
     * chức khác định dạng username, giữ nguyên OR đó cho khớp field categorized gốc dù hiếm khi có
     * tác dụng thực tế, không tự ý bỏ bớt).
     *
     * <p>Server-authoritative: tự resolve username/deletedByUserId từ user đang đăng nhập qua
     * {@link CurrentUserProvider} (không tin {@code request.getKeys()} client gửi — cùng nguyên tắc
     * {@link #queryVbdiWaitingForRegistration}).</p>
     */
    private PaginationResponse<Map<String, Object>> queryVbdiTrash(
            String dbType, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        UUID deletedByUserId = appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(AppUser::getId)
            .orElse(null);
        Page<VbdiDocument> page = vbdiDocumentRepository.findTrashedByUsernameOrDeleter(
            dbType, username.trim(), deletedByUserId, keyword, keywordColumns, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    private PaginationResponse<Map<String, Object>> queryVbdiDocumentOutByDateRange(
            String dbType, String identifier, String viewname, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        LocalDate today = LocalDate.now();
        LocalDate from;
        LocalDate to;
        switch (viewname) {
            case VIEW_VBDI_DOCUMENTOUT_YESTERDAY -> {
                from = today.minusDays(1);
                to = from;
            }
            case VIEW_VBDI_DOCUMENTOUT_THISWEEK -> {
                from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                to = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            }
            case VIEW_VBDI_DOCUMENTOUT_THISMONTH -> {
                from = today.with(TemporalAdjusters.firstDayOfMonth());
                to = today.with(TemporalAdjusters.lastDayOfMonth());
            }
            default -> { // VIEW_VBDI_DOCUMENTOUT_TODAY
                from = today;
                to = today;
            }
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .promulgationDateFrom(from)
            .promulgationDateTo(to)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
            dbType, VbdiStatus.ISSUED, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (port 4 tab riêng cơ quan H05.13 — dxl {@code view_vanbandi.dxl} dòng
     * ~2244-2341/2609-2639): CÙNG khoá identifier với {@link #queryVbdiDocumentOutByDateRange} (OR
     * phẳng 3 field, xem {@link VbdiDocumentRepository#findIssuedByCreatorOrUnitIdentifier}), thêm
     * 1 trong 4 công thức SNV cố định ({@code snvFilterSql} — hằng số Java, xem javadoc
     * {@link VbdiDocumentOutFilter#extraPredicateSql}).
     */
    private PaginationResponse<Map<String, Object>> queryVbdiDocumentOutBySnvCategory(
            String dbType, String identifier, String snvFilterSql, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .extraPredicateSql(snvFilterSql)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
            dbType, VbdiStatus.ISSUED, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (port "chọn sổ văn bản" — dxl {@code vwCreator_DocumentOut_ByTextBook}, dòng
     * ~2571-2607): view categorized 2 CỘT — cột $8 = khoá identifier CÙNG công thức @If-fallback với
     * {@code vwCreator_DocumentOut_TextBook} ("Toàn bộ", xem javadoc
     * {@link VbdiDocumentRepository#findIssuedByPublishOrCreatorIdentifier}), cột $7 =
     * {@code @LowerCase(@Trim(document_register_id))}. FE gửi ĐÚNG 2 khoá theo thứ tự này
     * ({@code ItemsList.jsx} dòng ~291: {@code keys: [key, idsvb.trim().toLowerCase()]}) — lấy
     * {@code keys.get(0)}/{@code keys.get(1)} theo đúng vị trí, KHÔNG dùng {@code firstKey()} (vốn
     * chỉ lấy 1 khoá, dùng cho mọi view khác trong lớp này).
     *
     * <p>{@code document_register_id} nhận CẢ UNID Domino LẪN UUID Java {@code document_registers}
     * (BL-116) — chỉ so khớp chuỗi (xem javadoc {@link VbdiDocumentOutFilter#documentRegisterId}),
     * nên dropdown "Chọn sổ văn bản" có thể trộn CẢ 2 nguồn mà không cần đổi gì ở tầng này.</p>
     */
    private PaginationResponse<Map<String, Object>> queryVbdiDocumentOutByTextBook(
            String dbType, List<String> keys, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        String identifier = keys != null && keys.size() > 0 ? keys.get(0) : null;
        String documentRegisterId = keys != null && keys.size() > 1 ? keys.get(1) : null;
        if (identifier == null || identifier.isBlank() || documentRegisterId == null || documentRegisterId.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .documentRegisterId(documentRegisterId)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(
            dbType, VbdiStatus.ISSUED, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (đợt 1 "port toàn bộ vbdi/xuly/*"): dùng CHUNG cho các view khoá theo cùng công thức
     * @If-fallback với "Toàn bộ" ({@code vwCreator_DocumentOut_WaitingToSend}/{@code _Sent}, dxl
     * dòng ~1493-1548/1682-1730) — CHỈ khác điều kiện phụ ({@code send_agent}/
     * {@code deletelistWaitingToSend}). Điều kiện gốc Domino {@code (send_error!="0" | send_error!=
     * "1")} LUÔN ĐÚNG (1 giá trị không thể vừa bằng "0" vừa bằng "1" cùng lúc — hằng đúng toán học,
     * khả năng cao là lỗi công thức gốc Domino, KHÔNG "sửa hộ" — chỉ đơn giản BỎ QUA vì thêm vào
     * không đổi kết quả).
     */
    private PaginationResponse<Map<String, Object>> queryVbdiDocumentOutTextBookLike(
            String dbType, String identifier, String status, String extraPredicateSql,
            String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .extraPredicateSql(extraPredicateSql)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(
            dbType, status, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (đợt 1 "port toàn bộ vbdi/xuly/*"): dùng chung cho các view khoá theo OR PHẲNG N
     * field động EQUALS {@code identifier} (2-4 field tuỳ view, xem
     * {@link VbdiDocumentRepository#findByAnyFieldEqualsIdentifier}) — {@code identifier} PHẢI là 1
     * trong các tổ tiên/định danh THẬT của user hiện tại ({@link #isOwnIdentifier}), cùng lý do bảo
     * mật đã áp dụng cho {@link #queryVbdiDocumentOutTextBook}.
     */
    private PaginationResponse<Map<String, Object>> queryVbdiByFieldsEquals(
            String dbType, String identifier, List<String> fieldNames, String status, String extraPredicateSql,
            String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        return queryVbdiByFieldsEquals(dbType, identifier, fieldNames, status, extraPredicateSql, null,
            items, keyword, keywordColumns, pageable);
    }

    /**
     * 09/09/2026 ("tìm kiếm nâng cao" — cutover {@code qlvb/tichhop/TimKiem?databasetype=vbdi}, xem
     * javadoc {@code VIEW_VBDI_DOCUMENTOUT_ALL}): overload có {@code advancedFilter} — GIỮ NGUYÊN
     * khung/an ninh của overload cũ (identifier vẫn PHẢI qua {@link #isOwnIdentifier}), chỉ hợp nhất
     * thêm {@code includeFieldValues}/{@code containsAnyFieldValues}/{@code promulgationDateFrom/To}
     * từ {@code advancedFilter} vào filter cuối cùng (không đổi hành vi các view khác đang gọi
     * overload cũ — {@code advancedFilter=null}).
     */
    private PaginationResponse<Map<String, Object>> queryVbdiByFieldsEquals(
            String dbType, String identifier, List<String> fieldNames, String status, String extraPredicateSql,
            VbdiDocumentOutFilter advancedFilter,
            String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter.VbdiDocumentOutFilterBuilder builder =
            advancedFilter != null ? advancedFilter.toBuilder() : VbdiDocumentOutFilter.builder();
        VbdiDocumentOutFilter filter = builder
            .extraPredicateSql(extraPredicateSql)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
            dbType, status, fieldNames, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 09/09/2026 (yêu cầu người dùng cutover route dashboard đang gọi Domino
     * {@code qlvb/tichhop/TimKiem?databasetype=vbdi} — bỏ qua Elasticsearch): chuyển đổi object thô
     * {@code search} FE gửi ({@code CollapseVBdi.jsx}, đúng cấu trúc {@code dataAdvanceSearchAll})
     * thành {@link VbdiDocumentOutFilter} bổ sung — CHỈ nhận diện ĐÚNG các field đã xác nhận qua form
     * THẬT đang hiển thị (return JSX cuối {@code CollapseVBdi.jsx}, KHÔNG phải mọi hàm
     * {@code field_*} định nghĩa trong file — nhiều hàm bị comment/không render): field lạ BỎ QUA.
     *
     * <ul>
     *   <li>{@code promulgation_date}: mảng {@code [dd/MM/yyyy, dd/MM/yyyy]} (RangePicker) →
     *   {@code promulgationDateFrom}/{@code promulgationDateTo} (field động đã có sẵn công thức parse
     *   ở {@link VbdiDocumentOutFilter}, TÁI DÙNG y nguyên).</li>
     *   <li>{@code document_type}, {@code sector}, {@code unit_publish_identifier} (cấp ban hành),
     *   {@code unit_in_charge_identifier} (đơn vị soạn), {@code user_signer} (người ký, username),
     *   {@code draftor} (người soạn/trình, tên hiển thị), {@code lien_thong} (hệ thống liên thông),
     *   {@code send_agent} (tình trạng phát hành): giá trị đơn, chọn từ danh mục/dropdown → EXACT match
     *   qua {@code includeFieldValues} — KHÁC {@code unit_process}/{@code unit_in_charge} của vbden
     *   (containment) vì các field vbdi này LUÔN lưu SCALAR (không phải mảng).</li>
     *   <li>{@code code_full} (số, ký hiệu), {@code mc_thutuc} (tên thủ tục hành chính): input tự do →
     *   {@code containsAnyFieldValues} (containment JSON-array-HOẶC-scalar-equality, cùng quy ước
     *   {@code unit_in_charge} của vbden cho field text tự do).</li>
     * </ul>
     *
     * <p><b>KHÔNG có {@code status}</b>: form THẬT không có dropdown trạng thái — FE luôn khoá cứng
     * {@code status="2"} (ISSUED) qua {@code advancedSearchData.status} riêng (không đi qua
     * {@code search}/{@code dataAdvanceSearchAll}), khớp ĐÚNG {@code status=VbdiStatus.ISSUED} đã
     * hard-code sẵn ở {@code VIEW_VBDI_DOCUMENTOUT_ALL} — không cần field này trong filter.</p>
     *
     * @return {@code null} nếu {@code search} rỗng/không có field nào nhận diện được.
     */
    private VbdiDocumentOutFilter buildVbdiAdvancedSearchFilter(Map<String, Object> search) {
        if (search == null || search.isEmpty()) {
            return null;
        }
        LocalDate minPromulgationDate = null;
        LocalDate maxPromulgationDate = null;
        Object promulgationDate = search.get("promulgation_date");
        if (promulgationDate instanceof List<?> range && range.size() == 2) {
            minPromulgationDate = parseAdvancedSearchDate(asString(range.get(0)));
            maxPromulgationDate = parseAdvancedSearchDate(asString(range.get(1)));
        }

        Map<String, List<String>> includeFieldValues = new LinkedHashMap<>();
        for (String field : List.of("document_type", "sector", "unit_publish_identifier",
                "unit_in_charge_identifier", "user_signer", "draftor", "lien_thong", "send_agent")) {
            putIfNonBlank(includeFieldValues, field, asString(search.get(field)));
        }
        Map<String, List<String>> containsAnyFieldValues = new LinkedHashMap<>();
        putIfNonBlank(containsAnyFieldValues, "code_full", asString(search.get("code_full")));
        putIfNonBlank(containsAnyFieldValues, "mc_thutuc", asString(search.get("mc_thutuc")));

        if (minPromulgationDate == null && maxPromulgationDate == null
                && includeFieldValues.isEmpty() && containsAnyFieldValues.isEmpty()) {
            return null;
        }
        return VbdiDocumentOutFilter.builder()
            .promulgationDateFrom(minPromulgationDate)
            .promulgationDateTo(maxPromulgationDate)
            .includeFieldValues(includeFieldValues.isEmpty() ? null : includeFieldValues)
            .containsAnyFieldValues(containsAnyFieldValues.isEmpty() ? null : containsAnyFieldValues)
            .build();
    }

    /**
     * 29/08/2026 (đợt 1 "port toàn bộ vbdi/xuly/*"): dùng chung cho các view "hàng đợi cá nhân" khoá
     * theo field động DẠNG DANH SÁCH username nối {@code ";"} (xem
     * {@link VbdiDocumentRepository#findByAnyFieldContainsMember}) — {@code member} ĐÃ được resolve
     * qua {@link #resolveUsernameToken} ở call site (nếu FE gửi macro {@code "$username$"}), method
     * này không tự resolve lại.
     */
    private PaginationResponse<Map<String, Object>> queryVbdiByFieldsContainsMember(
            String dbType, String member, List<String> fieldNames, String status, String extraPredicateSql,
            String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (member == null || member.isBlank()) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .extraPredicateSql(extraPredicateSql)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByAnyFieldContainsMember(
            dbType, status, fieldNames, member.trim(), filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026: {@code "$username$"} là MACRO THẬT của Domino (xem javadoc
     * {@link VbdiDocumentRepository#findByAnyFieldContainsMember}) — server LUÔN tự thay bằng
     * username phiên đăng nhập hiện tại ({@link CurrentUserProvider}, SERVER-AUTHORITATIVE), KHÔNG
     * BAO GIỜ dùng chuỗi này làm giá trị thật dù client gửi lên y hệt. Giá trị khác "$username$" giữ
     * nguyên (vd {@code cn_toanbo} gửi thẳng username thật, cùng mức tin cậy các view cá nhân khác
     * của {@code ChoBanHanh}/{@code ItemsList.jsx} đã dùng từ trước — không siết chặt hơn tiền lệ).
     */
    private String resolveUsernameToken(String key) {
        if (USERNAME_TOKEN.equals(key)) {
            return currentUserProvider.currentUsername().orElse(null);
        }
        return key;
    }

    /**
     * 29/08/2026 (đợt 2 — {@code vwCreator_DocumentOut_SendNoResponse}, dxl dòng ~1632-1680, route
     * "chuaden"): điều kiện status THẬT {@code (status="5" | send_error="1") & send_agent="1"} — OR
     * giữa cột status TĨNH và field động, diễn đạt TOÀN BỘ qua {@code extraPredicateSql} (xem javadoc
     * {@link VbdiDocumentRepository#findByPublishOrCreatorIdentifier}). "5" CHƯA từng được Java gán
     * cho bất kỳ văn bản nào (chỉ 4 trạng thái {@link VbdiStatus} hiện có) — nhánh này ĐÚNG sẽ luôn
     * rỗng cho tới khi có trạng thái đó, không phải bịa dữ liệu.
     */
    private PaginationResponse<Map<String, Object>> queryVbdiSendNoResponse(
            String dbType, String identifier, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmedIdentifier = identifier.trim();
        if (!isOwnIdentifier(trimmedIdentifier)) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .extraPredicateSql("(LOWER(COALESCE(d.status, '')) = '5'"
                + " OR COALESCE(d.dynamic_fields->>'send_error', '') = '1')"
                + " AND COALESCE(d.dynamic_fields->>'send_agent', '') = '1'")
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByPublishOrCreatorIdentifier(
            dbType, trimmedIdentifier, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (đợt 2 — {@code vwDocumentOut_BaoCaoChuaXem}, dxl dòng ~1093-1123, route
     * "baocaochuaxem"): formula Domino gốc dùng {@code @V3UserName} (user đang xem) NGAY TRONG
     * selection ({@code !@Contains(@LowerCase(log_seen_document); @Text(@V3UserName))}) — khác mọi
     * view khác (selection luôn user-độc-lập) vì view API vốn đã phục vụ ĐÚNG user đang gọi, nên chỉ
     * cần thêm 1 điều kiện NOT-CONTAINS-MEMBER qua {@link VbdiDocumentOutFilter#excludeMemberFieldName}
     * (tái dùng CHÍNH {@code member} đã bind cho khoá CONTAINS-MEMBER chính, xem javadoc field đó).
     */
    private PaginationResponse<Map<String, Object>> queryVbdiBaoCaoChuaXem(
            String dbType, String username, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .extraPredicateSql("COALESCE(d.dynamic_fields->>'document_type', '') = 'Báo cáo'")
            .excludeMemberFieldName("log_seen_document")
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByAnyFieldContainsMember(
            dbType, VbdiStatus.ISSUED,
            List.of("unit_publish_identifier", "user_creator", "userchuyenxuly", "coordinator_hide_rtext"),
            username.trim(), filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (đợt 2 — {@code vwPrivateImportant}, dxl dòng ~678-726, route "quantrong"): field
     * {@code MarkImportant} không đi kèm điều kiện status trong formula gốc — xem javadoc
     * {@link VbdiDocumentRepository#findByFieldContainsMemberAnySeparator}.
     */
    private PaginationResponse<Map<String, Object>> queryVbdiPrivateImportant(
            String dbType, String username, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByFieldContainsMemberAnySeparator(
            dbType, null, "MarkImportant", username.trim(), filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 29/08/2026 (đợt 2 — {@code vwGroup_Reader_by_Signer}/{@code vwGroup_by_Draftor}, dxl dòng
     * ~1318-1386/182-246, route "nguoiky"/"nguoisoan"): khoá theo {@code user_signer}/
     * {@code user_draft} — field ĐỊNH DANH NGƯỜI (username hiện tại, KHÔNG PHẢI identifier tổ chức),
     * cùng cách {@code ChoBanHanh/ItemsList.jsx} gửi thẳng {@code currentUser.username} cho các view
     * cá nhân KHÁC đã có từ trước (vd "quantrong"/"nhandebiet" — không qua xác thực bổ sung). KHÔNG
     * dùng {@link #isOwnIdentifier} (method đó kiểm tra chuỗi tổ tiên TỔ CHỨC — {@code username}
     * không nằm trong chuỗi đó, validate sẽ LUÔN THẤT BẠI sai) — giữ đúng mức tin cậy tiền lệ, không
     * siết chặt hơn.
     */
    private PaginationResponse<Map<String, Object>> queryVbdiByFieldsEqualsUnvalidated(
            String dbType, String identifier, List<String> fieldNames, String status, String extraPredicateSql,
            String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .extraPredicateSql(extraPredicateSql)
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
            dbType, status, fieldNames, identifier.trim(), filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * Yêu cầu người dùng 28/08/2026 ("kiểm trùng gọi tới view vwDocumentOut_kiemtrungvb, port sang
     * JAVA" — {@code ControllModal.jsx#kiemtrungvanban}, gọi TRƯỚC khi lưu "Thêm mới"): lọc văn bản
     * đi ĐÃ BAN HÀNH (status=ISSUED, Domino {@code status="2"}) khớp key categorized của view gốc
     * (dxl {@code view_vanbandi.dxl} dòng ~2395-2402): {@code document_register_id + "|" +
     * document_number [+ "|" + number_other nếu khác rỗng] + "|" + năm(promulgation_date)} — xem
     * {@link VbdiDocumentRepository#findByKiemTrungKey}.
     *
     * <p>KHÔNG tự loại trừ theo id (khác {@link #queryVbdiDocumentOutTextBook} không cần bước
     * này) — FE ({@code kiemtrungvanban}) tự lọc loại chính văn bản đang sửa SAU khi nhận kết quả
     * (đúng hành vi cũ, {@code .some(doc => doc.id !== id && ...)}) — giữ nguyên logic đó không đổi
     * khi port, chỉ đổi nguồn dữ liệu.</p>
     */
    private PaginationResponse<Map<String, Object>> queryVbdiKiemTrungVanBan(
            String dbType, String compositeKey, String items, Pageable pageable) {
        if (compositeKey == null || compositeKey.isBlank()) {
            return emptyPage(pageable);
        }
        Page<VbdiDocument> page = vbdiDocumentRepository.findByKiemTrungKey(
            dbType, VbdiStatus.ISSUED, compositeKey, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 16/09/2026 (xem javadoc {@link #VIEW_VBDI_BY_AUTHOR}/{@link #VIEW_VBDI_BY_USER_DRAFT}): dùng
     * chung cho 2 view CHỈ khoá theo 1 field DẠNG DANH SÁCH (member containment), KHÔNG lọc status —
     * ({@code findByFieldContainsMemberAnySeparator}, status=null, đã có sẵn cho
     * {@code vwPrivateImportant}). KHÔNG cần {@link #isOwnIdentifier} (cùng mức tin cậy
     * {@link #queryVbdiByFieldsEqualsUnvalidated} — value FE gửi luôn là chính identifier/username
     * của user hiện tại, không phải giá trị tuỳ ý client chọn).
     */
    private PaginationResponse<Map<String, Object>> queryVbdiFieldContainsNoStatus(
            String dbType, String fieldName, String value, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        if (value == null || value.isBlank()) {
            return emptyPage(pageable);
        }
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<VbdiDocument> page = vbdiDocumentRepository.findByFieldContainsMemberAnySeparator(
            dbType, null, fieldName, value.trim(), filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * True nếu {@code identifier} là 1 trong các tổ tiên THẬT của user hiện tại (chuỗi
     * {@code identifiers}, LÁ→GỐC, {@link GetUserOrganizationProfileUseCase}) — xem javadoc
     * {@link #queryVbdiDocumentOutTextBook} về lý do cần validate thay vì tự resolve 1 giá trị cố
     * định như {@link #resolveCurrentIdentifier()}.
     */
    private boolean isOwnIdentifier(String identifier) {
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null) {
            return false;
        }
        Optional<AppUser> userOpt = appUserRepository.findByUsernameAndDeletedOnIsNull(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        String identifiers = getUserOrganizationProfileUseCase.buildProfileForUser(userOpt.get()).getIdentifiers();
        if (identifiers == null || identifiers.isBlank()) {
            return false;
        }
        String trimmed = identifiers.endsWith(";") ? identifiers.substring(0, identifiers.length() - 1) : identifiers;
        for (String part : trimmed.split(";")) {
            if (identifier.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * BL-240 (HoSoCV Core) — DoD (3): truy vấn tổng quát theo 1 field động dạng chuỗi đơn, dùng
     * chung cơ chế {@link DocumentRepository#findByFieldValue} đã có từ BL-250 — tránh thêm 1
     * phương thức repository riêng chỉ để phục vụ 1 view.
     */
    private PaginationResponse<Map<String, Object>> queryDocumentsByFieldValue(
            String dbType, String fieldName, String fieldValue, String items, Pageable pageable) {
        if (fieldValue == null || fieldValue.isBlank()) {
            return emptyPage(pageable);
        }

        Page<BusinessDocument> page = documentRepository.findByFieldValue(dbType, fieldName, fieldValue.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * N03/N13 (VBDen — hàng đợi/hạn xử lý theo tổ chức). An ninh: KHÔNG dùng {@code request.getKeys()}
     * do client gửi (Domino thật kiểm tra user có thuộc identifier đó không trước khi tin — Java
     * hiện chưa có cơ chế org-membership tương đương, xem {@code CurrentUserProvider}); thay vào đó
     * luôn tự resolve {@code identifier} từ user đang đăng nhập, đúng pattern
     * {@code KiemTrungSoDenUseCase} — an toàn hơn (không cho spoof identifier) dù đơn giản hơn (chỉ
     * 1 identifier phẳng, chưa có ancestor chain như Domino's {@code GetKeyValue()}).
     */
    private PaginationResponse<Map<String, Object>> queryVbdenOrgView(
            String dbType, VbdenOrgViewFilter filter, String items, String keyword, List<String> keywordColumns,
            Pageable pageable) {
        String identifier = resolveCurrentIdentifier();
        if (identifier == null) {
            return emptyPage(pageable);
        }
        filter.setOrgValue(identifier);
        // Slice 7: keyword/keywordColumns song vao filter - findByVbdenOrgView tu no-op neu rong,
        // khong can nhanh if/else nhu findPendingForUser/findByAnyFieldContains (khong co overload).
        filter.setKeyword(keyword);
        filter.setKeywordColumns(keywordColumns);

        Page<BusinessDocument> page = documentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Xem javadoc VIEW_VBDEN_TRASH. Hardcode "vbden_so" (bo qua dbType tham so cua execute(), luon
    // = "vbden" tu VbdenController#getView) - ca 3 view thung rac song tren ban ghi "so", khong phai
    // noi dung dung chung. "filter" da co san cac dieu kien rieng cua tung view (deletedOnly hoac
    // reply/is_revoke, xem 3 filter builder ben duoi) - method nay chi lo phan CHUNG (resolve
    // identifier, keyword, sort, join voi van ban goc).
    private PaginationResponse<Map<String, Object>> queryVbdenTrash(
            VbdenOrgViewFilter filter, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        String identifier = resolveCurrentIdentifier();
        if (identifier == null) {
            return emptyPage(pageable);
        }
        filter.setOrgValue(identifier);
        filter.setKeyword(keyword);
        filter.setKeywordColumns(keywordColumns);

        Page<BusinessDocument> page = documentRepository.findByVbdenOrgView(VBDEN_SO_DOCUMENT_TYPE, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(entry -> joinVbdenTrashEntryWithParent(entry, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // dxl 67-91: @Contains(Form;"Deleted") & isrevoke!="1" - "Deleted" that = soft-delete CHUAN qua
    // XoaVanBanDenUseCase/PhucHoiVanBanDenUseCase (deleted_on). Bugfix 14/09/2026 (2): sua ten field
    // tu "isrevoke" (SAI, chep nguyen van DXL) sang "is_revoke" (DUNG, xem VbdiRevokeTaskHandler) -
    // xem javadoc VIEW_VBDEN_TRASH muc "Bugfix ngay trong lan port truoc do".
    private VbdenOrgViewFilter vbdenTrashBixoaFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(VBDEN_TRASH_IDENTIFIER_CREATOR_FIELD))
            .excludeFieldValues(Map.of("is_revoke", List.of("1")))
            .deletedOnly(true)
            .primarySortField("document_number")
            .primarySortType("integer")
            .sortDescending(true)
            .build();
    }

    // dxl 327-351: @Contains(Form;"Deleted") & isrevoke="1" - "Bi thu hoi" (nut "Thu hoi"/"Lay lai"
    // qua giao thuc vbdi CHINH THUC, KHONG PHAI qua "Xoa van ban den") - xem javadoc VIEW_VBDEN_TRASH:
    // VbdiRevokeTaskHandler#revokeSoVanBanEntries KHONG dung deleted_on, chi UPDATE dynamic_fields
    // (reply='1', is_revoke='1') qua JDBC thuan - PHAI loc field dong, KHONG dung deletedOnly.
    private VbdenOrgViewFilter vbdenTrashBithuhoiFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(VBDEN_TRASH_IDENTIFIER_CREATOR_FIELD))
            .includeFieldValues(Map.of("reply", List.of("1"), "is_revoke", List.of("1")))
            .primarySortField("document_number")
            .primarySortType("integer")
            .sortDescending(true)
            .build();
    }

    // dxl 219-243: @Contains(Form;"Deleted") & request_form = "true" - xem javadoc VIEW_VBDEN_TRASH
    // muc "sovanban_phieuyeucau_bixoa": CHUA co use case nao set "request_form" (tinh nang tao "phieu
    // yeu cau" chua duoc port) - danh sach nay se LUON RONG cho toi khi tinh nang do duoc port, KHONG
    // phai loi cua lan port nay (route/menu van dung THAT, chi la chua co du lieu).
    private VbdenOrgViewFilter vbdenTrashRequestFormFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(VBDEN_TRASH_IDENTIFIER_CREATOR_FIELD))
            .includeFieldValues(Map.of("request_form", List.of("true")))
            .deletedOnly(true)
            .primarySortField("document_number")
            .primarySortType("integer")
            .sortDescending(true)
            .build();
    }

    // Tuong duong GetSoVanBanDenUseCase#joinWithParentDocument (ban ghi "so" khong tu mang subject/
    // unit_in_charge/code - phai doc LIVE tu van ban goc qua parentid). Van ban goc co the da bi xoa
    // CUNG (chu so huu, xem XoaVanBanDenUseCase) - khong throw, tra field cua rieng ban ghi so.
    private Map<String, Object> joinVbdenTrashEntryWithParent(BusinessDocument entry, String items) {
        Map<String, Object> entryFields = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());

        Map<String, Object> merged = new LinkedHashMap<>();
        UUID parentId = parseUuid(asString(entryFields.get(VBDEN_TRASH_PARENT_ID_FIELD)));
        BusinessDocument parent = parentId == null ? null : documentRepository.findById(parentId).orElse(null);
        if (parent != null) {
            merged.put("title", parent.getTitle());
            merged.put("status", parent.getStatus());
            merged.put("created_on", parent.getCreatedOn());
            merged.put("last_modified_on", parent.getLastModifiedOn());
            merged.putAll(dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
            merged.putIfAbsent("subject", parent.getTitle());
            if (wantsField(items, "files")) {
                List<Map<String, Object>> files = new ArrayList<>();
                listDocumentFilesUseCase.execute(parent.getId()).getItems().forEach(fileDto -> files.add(toFileMap(fileDto)));
                merged.put("files", files);
            }
        }
        merged.putAll(entryFields);
        merged.put("id", entry.getId());
        merged.put("databasetype", "vbden");
        return dynamicDocumentFieldsService.filterItems(merged, items);
    }

    /**
     * Slice 5 (N06 biến thể/N07/N10/N11 — hàng đợi cá nhân đơn giản, containment 1 field). An ninh:
     * TRUST {@code username} do client gửi — xem javadoc {@link #VIEW_VBDEN_CHOPHANPHOI} về lý do
     * khác quyết định của {@link #queryVbdenOrgView}. Tái dùng {@link DocumentRepository#findByAnyFieldContains}
     * (đã có từ N03) với danh sách 1 field — không cần method repository mới.
     */
    private PaginationResponse<Map<String, Object>> queryVbdenPersonalQueue(
            String dbType, String fieldName, String username, String items, String keyword,
            List<String> keywordColumns, Pageable pageable) {
        return queryVbdenPersonalQueue(dbType, List.of(fieldName), username, items, keyword, keywordColumns, pageable);
    }

    // "cuacanhan" (dxl 1220-1247): khoa la UNION 9 field ca nhan (khong phai 1 field don) - overload
    // nhan List<String> thay vi 1 fieldName, tai dung findByAnyFieldContains (da ho tro nhieu field).
    private PaginationResponse<Map<String, Object>> queryVbdenPersonalQueue(
            String dbType, List<String> fieldNames, String username, String items, String keyword,
            List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        Page<BusinessDocument> page = hasKeyword(keyword, keywordColumns)
            ? documentRepository.findByAnyFieldContains(
                dbType, fieldNames, username.trim(), keyword.trim(), keywordColumns, pageable)
            : documentRepository.findByAnyFieldContains(dbType, fieldNames, username.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // D.44 (01/09/2026, "tim kiem nang cao" theodoi/datheodoi): overload co advancedFilter - null (FE
    // khong mo popover nang cao/khong chon gi) UY QUYEN LAI cho overload cu, KHONG doi hanh vi. Non-null
    // di qua findByAnyFieldContains(..., VbdenOrgViewFilter, ...) - van GIU created_on ASC (xem javadoc
    // repository).
    private PaginationResponse<Map<String, Object>> queryVbdenPersonalQueue(
            String dbType, String fieldName, String username, String items, String keyword,
            List<String> keywordColumns, VbdenOrgViewFilter advancedFilter, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        if (advancedFilter == null) {
            return queryVbdenPersonalQueue(dbType, List.of(fieldName), username, items, keyword, keywordColumns, pageable);
        }
        VbdenOrgViewFilter effectiveFilter = advancedFilter.toBuilder()
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<BusinessDocument> page = documentRepository.findByAnyFieldContains(
            dbType, List.of(fieldName), username.trim(), effectiveFilter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * D.44 (01/09/2026, "tìm kiếm nâng cao" — {@code CollapseVBden.jsx}, xem javadoc
     * {@link ViewQueryRequest#getSearch()}), mở rộng 09/09/2026 (yêu cầu người dùng cutover route
     * dashboard đang gọi Domino {@code qlvb/tichhop/TimKiemVanBanDenEs} — port ĐỦ bộ tiêu chí của
     * path mặc định/dashboard trong {@code CollapseVBden.jsx}, bỏ qua Elasticsearch): chuyển đổi
     * object thô {@code search} FE gửi (đúng cấu trúc {@code dataAdvanceSearchAll}) thành
     * {@link VbdenOrgViewFilter}. Nhận diện 8 field (field lạ khác vẫn BỎ QUA — vd
     * {@code document_internet}/{@code delivery_method}/{@code document_register_id} chỉ dùng ở các
     * path báo cáo khác của CÙNG component, chưa có bằng chứng cần cho dashboard/theodoi/datheodoi):
     *
     * <ul>
     *   <li>{@code received_date}: mảng {@code [dd/MM/yyyy, dd/MM/yyyy]} (RangePicker,
     *   {@code onDateChange}) → {@code minReceivedDate}/{@code maxReceivedDate} (field động, khác
     *   {@code status} — dùng công thức parse đã có sẵn từ D.36, dd/MM/yyyy KHÔNG phải ISO).</li>
     *   <li>{@code status}: giá trị đơn (dropdown, đã map "5"→"1"/"6"→"3" ở FE) → cột TĨNH
     *   {@code documents.status} qua {@code includeFieldValues} (repository tự biết đọc cột tĩnh khi
     *   key = {@code "status"}).</li>
     *   <li>{@code unit_in_charge}, {@code unit_process}: giá trị đơn (text/tree select) →
     *   {@code containsAnyFieldValues} (field động, containment — AN TOÀN cho cả field scalar lẫn
     *   mảng, xem javadoc field đó).</li>
     *   <li>{@code user_process}: chuỗi {@code ";"}-nối nhiều username (modal chọn nhiều người) →
     *   {@code containsAnyFieldValues} — OR giữa các username (khớp ĐÚNG ý nghĩa "đang được giao cho
     *   MỘT TRONG những người này", không phải "giao cho TẤT CẢ").</li>
     *   <li>{@code document_type}, {@code priority}, {@code sector}: giá trị đơn (dropdown, danh mục
     *   dùng chung {@code danhmuc_by_form}) → {@code includeFieldValues} (field động, EXACT match —
     *   FE gửi đúng {@code name} danh mục, không phải mã, nên so khớp scalar là đủ, KHÔNG cần
     *   containment/mảng như {@code unit_process}).</li>
     * </ul>
     *
     * @return {@code null} nếu {@code search} rỗng/không có field nào nhận diện được — giữ nguyên
     * hành vi hiện tại (không lọc thêm), không tạo filter rỗng vô nghĩa.
     */
    private VbdenOrgViewFilter buildAdvancedSearchFilter(Map<String, Object> search) {
        if (search == null || search.isEmpty()) {
            return null;
        }
        LocalDate minReceivedDate = null;
        LocalDate maxReceivedDate = null;
        Map<String, List<String>> includeFieldValues = new LinkedHashMap<>();
        Map<String, List<String>> containsAnyFieldValues = new LinkedHashMap<>();

        Object receivedDate = search.get("received_date");
        if (receivedDate instanceof List<?> range && range.size() == 2) {
            minReceivedDate = parseAdvancedSearchDate(asString(range.get(0)));
            maxReceivedDate = parseAdvancedSearchDate(asString(range.get(1)));
        }
        putIfNonBlank(includeFieldValues, "status", asString(search.get("status")));
        putIfNonBlank(includeFieldValues, "document_type", asString(search.get("document_type")));
        putIfNonBlank(includeFieldValues, "priority", asString(search.get("priority")));
        putIfNonBlank(includeFieldValues, "sector", asString(search.get("sector")));
        putIfNonBlank(containsAnyFieldValues, "unit_in_charge", asString(search.get("unit_in_charge")));
        putIfNonBlank(containsAnyFieldValues, "unit_process", asString(search.get("unit_process")));
        String userProcess = asString(search.get("user_process"));
        if (userProcess != null && !userProcess.isBlank()) {
            List<String> usernames = List.of(userProcess.split(";")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
            if (!usernames.isEmpty()) {
                containsAnyFieldValues.put("user_process", usernames);
            }
        }

        if (minReceivedDate == null && maxReceivedDate == null
                && includeFieldValues.isEmpty() && containsAnyFieldValues.isEmpty()) {
            return null;
        }
        return VbdenOrgViewFilter.builder()
            .minReceivedDate(minReceivedDate)
            .maxReceivedDate(maxReceivedDate)
            .includeFieldValues(includeFieldValues.isEmpty() ? null : includeFieldValues)
            .containsAnyFieldValues(containsAnyFieldValues.isEmpty() ? null : containsAnyFieldValues)
            .build();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private void putIfNonBlank(Map<String, List<String>> target, String field, String value) {
        if (value != null && !value.isBlank()) {
            target.put(field, List.of(value));
        }
    }

    // Cung dinh dang dd/MM/yyyy voi RangePicker cua CollapseVBden.jsx (date_format="DD/MM/YYYY") -
    // KHAC ISO, cung bugfix D.35/D.36 (VBDEN_DD_MM_YYYY_FORMATTER da co san, tai dung).
    private LocalDate parseAdvancedSearchDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), VBDEN_DD_MM_YYYY_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    // "sapdenhan_cuacanhan" (dxl 2504-2530): giong saptoihanCuatochucFilter (cung dieu kien ngay/loai
    // VB) nhung khoa la field CA NHAN (username, TRUST client - xem javadoc VIEW_VBDEN_CUACANHAN),
    // khong phai identifier to chuc tu resolve nhu queryVbdenOrgView - nen khong tai dung duoc thang
    // method do, phai tu build VbdenOrgViewFilter (cung khuon mau queryVbdenPersonalQueueWithStatus).
    private PaginationResponse<Map<String, Object>> querySapDenHanCuaCaNhan(
            String dbType, String username, String items, String keyword, List<String> keywordColumns,
            Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_SAPDENHAN_CUACANHAN_FIELDS)
            .orgValue(username.trim())
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY),
                "document_type", List.of(GIAY_MOI_DOCUMENT_TYPE)))
            .minDeadline(LocalDate.now())
            .maxDeadline(LocalDate.now().plusDays(2))
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<BusinessDocument> page = documentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // N08 (31/08/2026) - xem javadoc XINYKIEN_DOCUMENT_TYPE. Giong khuon mau querySapDenHanCuaCaNhan
    // (tu build VbdenOrgViewFilter, khong qua queryVbdenPersonalQueue vi can THEM dieu kien co
    // opinionFlagField - queryVbdenPersonalQueue khong ho tro includeFieldValues).
    // Cap nhat 31/08/2026 (yeu cau nguoi dung "tao bang rieng"): doi tu documentRepository (bang
    // "documents" dung chung) sang xinYKienDocumentRepository (bang vat ly doc lap
    // vbden_xinykien_documents) - KHONG con dung dbType tham so nua (bang moi CHI co 2
    // document_type, khong can cach ly bang discriminator nhu truoc).
    private PaginationResponse<Map<String, Object>> queryXinYkienQueue(
            String fieldName, String opinionFlagField, String username, String items, String keyword,
            List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        var filterBuilder = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(fieldName))
            .orgValue(username.trim())
            .keyword(keyword)
            .keywordColumns(keywordColumns);
        if (opinionFlagField != null) {
            filterBuilder.includeFieldValues(Map.of(opinionFlagField, OPINION_FLAG_TRUE_VALUES));
        }
        Page<XinYKienDocument> page =
            xinYKienDocumentRepository.findByVbdenOrgView(XINYKIEN_DOCUMENT_TYPE, filterBuilder.build(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * Slice 5: giống {@link #queryVbdenPersonalQueue} nhưng thêm điều kiện {@code status IN (...)}
     * (vd. {@code dangxuly}: {@code (status="1"|status="2")}). Tái dùng {@link VbdenOrgViewFilter}/
     * {@link DocumentRepository#findByVbdenOrgView} — repository không quan tâm {@code orgValue} là
     * identifier hay username, chỉ là 1 chuỗi containment, nên tái dùng được nguyên vẹn.
     */
    private PaginationResponse<Map<String, Object>> queryVbdenPersonalQueueWithStatus(
            String dbType, String fieldName, List<String> statuses, String username, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(fieldName))
            .orgValue(username.trim())
            .includeFieldValues(Map.of("status", statuses))
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<BusinessDocument> page = documentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Cum du thao (24/08/2026, yeu cau nguoi dung "tao bang rieng, de doc lap du lieu"): CUNG cong
    // thuc voi queryVbdenPersonalQueue nhung doc/ghi qua DuThaoDocumentRepository (bang doc lap
    // duthao_documents) - KHONG dung lai duoc method tren vi no hardcode documentRepository (bang
    // "documents" dung chung). dbType van can truyen (duthao_documents chua CA "duthao" VA
    // "draft_process" - phai loc dung document_type, khong phai suy dien tu ten bang).
    private PaginationResponse<Map<String, Object>> queryDuThaoPersonalQueue(
            String dbType, String fieldName, String username, String items, String keyword,
            List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        Page<DuThaoDocument> page = hasKeyword(keyword, keywordColumns)
            ? duThaoDocumentRepository.findByAnyFieldContains(
                dbType, List.of(fieldName), username.trim(), keyword.trim(), keywordColumns, pageable)
            : duThaoDocumentRepository.findByAnyFieldContains(dbType, List.of(fieldName), username.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Cum du thao - tuong duong queryVbdenPersonalQueueWithStatus, doc qua DuThaoDocumentRepository.
    // Overload 1 field - giu tuong thich nguoc voi cac case da co (vd VIEW_DUTHAO_CREATOR_DRAFTING),
    // tai dung ban List<String> ben duoi (cung khuon single/multi cua queryVbdenPersonalQueue).
    private PaginationResponse<Map<String, Object>> queryDuThaoPersonalQueueWithStatus(
            String dbType, String fieldName, List<String> statuses, String username, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        return queryDuThaoPersonalQueueWithStatus(
            dbType, List.of(fieldName), statuses, username, items, keyword, keywordColumns, pageable);
    }

    // "vwUserCreator_Process_Handler_Submit" (25/08/2026): khoa la UNION 2 field
    // (user_process_handler/user_other_process_handler, xem javadoc VIEW_DUTHAO_CREATOR_PROCESS_HANDLER_SUBMIT)
    // - overload nhan List<String> thay vi 1 fieldName.
    private PaginationResponse<Map<String, Object>> queryDuThaoPersonalQueueWithStatus(
            String dbType, List<String> fieldNames, List<String> statuses, String username, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(fieldNames)
            .orgValue(username.trim())
            .includeFieldValues(Map.of("status", statuses))
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        Page<DuThaoDocument> page = duThaoDocumentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Executor chung cho cac view hang doi du thao (DT-002 loat 2): nhan filter da build san,
    // goi repository + chieu field + dong goi phan trang - dung lai nguyen khuon cua cac method
    // query* ben tren, chi bo phan build filter ra khoi de tung case tu dinh nghia ro rang.
    private PaginationResponse<Map<String, Object>> queryDuThaoQueueByFilter(
            String dbType, VbdenOrgViewFilter filter, String items, Pageable pageable) {
        Page<DuThaoDocument> page = duThaoDocumentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Wrapper co guard key rong (dong nhat voi cac helper queryDuThao* da co: khong co username thi
    // tra trang KHONG query) + gan orgValue/keyword vao filter dac thu cua tung case.
    private PaginationResponse<Map<String, Object>> queryDuThaoPersonalQueueByFilter(
            String dbType, VbdenOrgViewFilter partialFilter, String username, String items,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = partialFilter.toBuilder()
            .orgValue(username.trim())
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        return queryDuThaoQueueByFilter(dbType, filter, items, pageable);
    }

    // "vwUserCreator_Process_Handler_Submit" (port lai 25/08/2026 theo XML export moi - xem javadoc
    // VIEW_DUTHAO_USERCREATOR_PROCESS_HANDLER_SUBMIT): khoa la thanh vien cua chuoi ";"-joined
    // user_other_processed_handler -> di qua capability orgListFieldNames, KHONG dung orgFieldNames
    // (containment/equality khong khop thanh vien trong chuoi scalar).
    private PaginationResponse<Map<String, Object>> queryDuThaoCreatorProcessHandlerSubmit(
            String dbType, String username, String items, String keyword,
            List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgListFieldNames(List.of(DUTHAO_USER_OTHER_PROCESSED_HANDLER_FIELD))
            .orgValue(username.trim())
            .includeFieldValues(Map.of("status", DUTHAO_PROCESS_HANDLER_STATUSES))
            .keyword(keyword)
            .keywordColumns(keywordColumns)
            .build();
        return queryDuThaoQueueByFilter(dbType, filter, items, pageable);
    }

    // "vwUser_Coordinated" (DT-002, 26/08/2026): OR-group giua 2 field/2 dieu kien status khac nhau
    // - VbdenOrgViewFilter khong bieu dien duoc, phai goi thang method repository rieng
    // (findByFieldOrFieldWithStatus) thay vi di qua queryDuThaoQueueByFilter/findByVbdenOrgView.
    private PaginationResponse<Map<String, Object>> queryDuThaoCoordinatedQueue(
            String dbType, String username, String items, String keyword,
            List<String> keywordColumns, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        Page<DuThaoDocument> page = duThaoDocumentRepository.findByFieldOrFieldWithStatus(
            dbType, DUTHAO_USER_COORDINATED_FIELD, DUTHAO_USER_COORDINATOR_FIELD,
            DUTHAO_COORDINATOR_DONE_STATUSES, username.trim(), keyword, keywordColumns, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Mo rong BL-240 - cum ho so cong viec (hosocv_documents, bang vat ly doc lap): CUNG cong thuc
    // voi queryDuThaoPersonalQueue nhung doc/ghi qua HoSoCVDocumentRepository - KHONG dung lai duoc
    // (hardcode ban repository khac). fieldName don (scalar): user_creator/user_process_handler/
    // user_approved_by/user_leader_in_charge/category.
    private PaginationResponse<Map<String, Object>> queryHoSoCVPersonalQueue(
            String dbType, String fieldName, String username, String items, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        Page<HoSoCVDocument> page =
            hoSoCVDocumentRepository.findByAnyFieldContains(dbType, List.of(fieldName), username.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 09/09/2026 ("tìm kiếm nâng cao" hồ sơ công việc — cutover {@code qlvb/search?databasetype=hosocv},
     * bỏ qua Elasticsearch): biến thể của {@link #queryHoSoCVPersonalQueue} đi qua
     * {@link HoSoCVDocumentRepository#findByVbdenOrgView} (đã hỗ trợ sẵn
     * {@code includeFieldValues}/{@code containsAnyFieldValues}/khoảng ngày, xem
     * {@code PostgresHoSoCVDocumentRepository}) thay vì {@code findByAnyFieldContains} (chỉ khoá 1
     * field, không lọc thêm được) — khoá cá nhân ({@code fieldName=user_creator}, {@code username})
     * vẫn giữ NGUYÊN ngữ nghĩa "chỉ hồ sơ do chính user tạo" (xác nhận qua điều tra BE_THANHHOA — xem
     * javadoc {@code VIEW_HOSOCV_TOAN_BO}), chỉ hợp nhất thêm {@code advancedFilter} vào CÙNG 1 filter.
     */
    private PaginationResponse<Map<String, Object>> queryHoSoCVOrgView(
            String dbType, String fieldName, String username, VbdenOrgViewFilter advancedFilter,
            String items, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = advancedFilter.toBuilder()
            .orgFieldNames(List.of(fieldName))
            .orgValue(username.trim())
            .build();
        Page<HoSoCVDocument> page = hoSoCVDocumentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * 09/09/2026 (yêu cầu người dùng cutover route dashboard đang gọi Domino
     * {@code qlvb/search?databasetype=hosocv} — bỏ qua Elasticsearch): chuyển đổi object thô
     * {@code search}/{@code dataAdvanceSearchAll} FE gửi ({@code CollapseHoSoCV.jsx}, path mặc định
     * {@code buildtimkiemnangcao()}) thành {@link VbdenOrgViewFilter} bổ sung — CHỈ nhận diện ĐÚNG
     * field đã xác nhận qua form THẬT đang hiển thị (13 field, trong đó 2 field bị comment ẩn
     * KHÔNG tính: {@code coordinated_status}/{@code leader_in_charge}/{@code category — có, xem dưới}):
     *
     * <ul>
     *   <li>{@code create_date}: mảng {@code [dd/MM/yyyy, dd/MM/yyyy]} → khớp NGỮ NGHĨA cột tĩnh
     *   {@code created_on} (AuditableEntity) qua {@code minCreatedOn}/{@code maxCreatedOn} (đã có sẵn
     *   từ Slice 9, dùng cho {@code homqua}/{@code trongngay}...) — "Ngày tạo" hồ sơ CHÍNH LÀ thời
     *   điểm bản ghi được tạo, không phải field nghiệp vụ riêng.</li>
     *   <li>{@code end_date}, {@code storaged_date}: mảng ngày tương tự → field ĐỘNG riêng (KHÁC
     *   {@code created_on}) qua {@code minEndDate}/{@code maxEndDate},
     *   {@code minStoragedDate}/{@code maxStoragedDate} (mới thêm).</li>
     *   <li>{@code process_handler}, {@code approved_by}: giá trị đơn (TDSelect, value=tên hiển thị —
     *   xem {@code ChiTietPage.jsx} dòng ~199-268: {@code process_handler}/{@code approved_by} là
     *   field MIRROR TÊN HIỂN THỊ, TÁCH BIỆT {@code user_process_handler}/{@code user_approved_by}
     *   MIRROR USERNAME dùng cho khoá cá nhân — KHÔNG được nhầm 2 cặp field này) →
     *   {@code includeFieldValues} EXACT match trên field TÊN (không phải field username).</li>
     *   <li>{@code status}, {@code trinhlanhdaoduyet}, {@code storaged}, {@code category}: giá trị đơn
     *   (dropdown) → {@code includeFieldValues} EXACT match ({@code status} tự động route cột tĩnh
     *   qua {@code PostgresHoSoCVDocumentRepository#appendStatusPredicate}).</li>
     *   <li>{@code name} (ký hiệu hồ sơ), {@code title} (tiêu đề), {@code first_topic} (đề mục lớn),
     *   {@code second_topic} (đề mục nhỏ): input tự do → {@code containsAnyFieldValues} (cùng quy ước
     *   {@code unit_in_charge} vbden/{@code code_full} vbdi cho field text tự do — EXACT match, KHÔNG
     *   phải LIKE/substring, xem javadoc các field đó).</li>
     * </ul>
     *
     * @return {@code null} nếu {@code search} rỗng/không có field nào nhận diện được.
     */
    private VbdenOrgViewFilter buildHoSoCVAdvancedSearchFilter(Map<String, Object> search) {
        if (search == null || search.isEmpty()) {
            return null;
        }
        LocalDate minCreatedOn = null;
        LocalDate maxCreatedOn = null;
        Object createDate = search.get("create_date");
        if (createDate instanceof List<?> range && range.size() == 2) {
            minCreatedOn = parseAdvancedSearchDate(asString(range.get(0)));
            maxCreatedOn = parseAdvancedSearchDate(asString(range.get(1)));
        }
        LocalDate minEndDate = null;
        LocalDate maxEndDate = null;
        Object endDate = search.get("end_date");
        if (endDate instanceof List<?> range && range.size() == 2) {
            minEndDate = parseAdvancedSearchDate(asString(range.get(0)));
            maxEndDate = parseAdvancedSearchDate(asString(range.get(1)));
        }
        LocalDate minStoragedDate = null;
        LocalDate maxStoragedDate = null;
        Object storagedDate = search.get("storaged_date");
        if (storagedDate instanceof List<?> range && range.size() == 2) {
            minStoragedDate = parseAdvancedSearchDate(asString(range.get(0)));
            maxStoragedDate = parseAdvancedSearchDate(asString(range.get(1)));
        }

        Map<String, List<String>> includeFieldValues = new LinkedHashMap<>();
        for (String field : List.of("process_handler", "approved_by", "status",
                "trinhlanhdaoduyet", "storaged", "category")) {
            putIfNonBlank(includeFieldValues, field, asString(search.get(field)));
        }
        Map<String, List<String>> containsAnyFieldValues = new LinkedHashMap<>();
        for (String field : List.of("name", "title", "first_topic", "second_topic")) {
            putIfNonBlank(containsAnyFieldValues, field, asString(search.get(field)));
        }

        if (minCreatedOn == null && maxCreatedOn == null && minEndDate == null && maxEndDate == null
                && minStoragedDate == null && maxStoragedDate == null
                && includeFieldValues.isEmpty() && containsAnyFieldValues.isEmpty()) {
            return null;
        }
        return VbdenOrgViewFilter.builder()
            .minCreatedOn(minCreatedOn)
            .maxCreatedOn(maxCreatedOn)
            .minEndDate(minEndDate)
            .maxEndDate(maxEndDate)
            .minStoragedDate(minStoragedDate)
            .maxStoragedDate(maxStoragedDate)
            .includeFieldValues(includeFieldValues.isEmpty() ? null : includeFieldValues)
            .containsAnyFieldValues(containsAnyFieldValues.isEmpty() ? null : containsAnyFieldValues)
            .build();
    }

    /**
     * 09/09/2026 (yêu cầu người dùng cutover route dashboard đang gọi Domino
     * {@code qlvb/tichhop/TimKiem?databasetype=duthao} — bỏ qua Elasticsearch): chuyển đổi object
     * thô {@code search}/{@code dataAdvanceSearchAll} FE gửi ({@code CollapseVBduthao.jsx}, path mặc
     * định {@code action_path.lastIndexOf("thongketheo") <= 0}) thành {@link VbdenOrgViewFilter} bổ
     * sung — nhận diện ĐÚNG 8 field của form THẬT đang hiển thị:
     *
     * <ul>
     *   <li>{@code create_date}: mảng {@code [dd/MM/yyyy, dd/MM/yyyy]} → khớp NGỮ NGHĨA cột tĩnh
     *   {@code created_on} qua {@code minCreatedOn}/{@code maxCreatedOn} — cùng quyết định đã áp
     *   dụng cho hồ sơ (xem javadoc {@code buildHoSoCVAdvancedSearchFilter}).</li>
     *   <li>{@code document_type}, {@code sector}, {@code unit_publish}, {@code unit_in_charge},
     *   {@code creator}, {@code signer}, {@code status}: giá trị đơn — TẤT CẢ đều là {@code TDSelect}
     *   (chọn từ danh mục/danh sách, KHÔNG có input tự do nào trong form mặc định — khác vbden/vbdi/
     *   hosocv) → {@code includeFieldValues} EXACT match cho CẢ 7 field (không cần
     *   {@code containsAnyFieldValues} ở đây).</li>
     * </ul>
     *
     * @return {@code null} nếu {@code search} rỗng/không có field nào nhận diện được.
     */
    private VbdenOrgViewFilter buildDuThaoAdvancedSearchFilter(Map<String, Object> search) {
        if (search == null || search.isEmpty()) {
            return null;
        }
        LocalDate minCreatedOn = null;
        LocalDate maxCreatedOn = null;
        Object createDate = search.get("create_date");
        if (createDate instanceof List<?> range && range.size() == 2) {
            minCreatedOn = parseAdvancedSearchDate(asString(range.get(0)));
            maxCreatedOn = parseAdvancedSearchDate(asString(range.get(1)));
        }

        Map<String, List<String>> includeFieldValues = new LinkedHashMap<>();
        for (String field : List.of("document_type", "sector", "unit_publish",
                "unit_in_charge", "creator", "signer", "status")) {
            putIfNonBlank(includeFieldValues, field, asString(search.get(field)));
        }

        if (minCreatedOn == null && maxCreatedOn == null && includeFieldValues.isEmpty()) {
            return null;
        }
        return VbdenOrgViewFilter.builder()
            .minCreatedOn(minCreatedOn)
            .maxCreatedOn(maxCreatedOn)
            .includeFieldValues(includeFieldValues.isEmpty() ? null : includeFieldValues)
            .build();
    }

    // Ket hop khoa ca nhan + 1 dieu kien bang-chinh-xac (status TINH hoac field dong nhu
    // trinhlanhdaoduyet/storaged) - dung cho hoso_chutri_dangxuly/_daxuly, cac view
    // Storaged_*/Trinh_*, vwUser_Approved_By_Trinh_2.
    private PaginationResponse<Map<String, Object>> queryHoSoCVPersonalQueueWithStatus(
            String dbType, String fieldName, String statusFieldName, List<String> statusValues,
            String username, String items, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of(fieldName))
            .orgValue(username.trim())
            .includeFieldValues(Map.of(statusFieldName, statusValues))
            .build();
        Page<HoSoCVDocument> page = hoSoCVDocumentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // "vwUser_Coordinator"/"vwBy_Coordinator": field luu dang chuoi ";"-joined (KHONG phai JSON
    // array) - phai so khop THEO THANH VIEN (LIKE neo ";"), khac containment/scalar-equality cua
    // queryHoSoCVPersonalQueue.
    private PaginationResponse<Map<String, Object>> queryHoSoCVListMember(
            String dbType, String fieldName, String username, String items, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgListFieldNames(List.of(fieldName))
            .orgValue(username.trim())
            .build();
        Page<HoSoCVDocument> page = hoSoCVDocumentRepository.findByVbdenOrgView(dbType, filter, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // "vwUser_Coordinated_Status": khoa FE la "<username>_chua"/"<username>_da" (khong phai
    // username thuan - xem docs/analysis/08 muc 3, bang route). "_da" = thanh vien user_coordinated;
    // "_chua" = thanh vien user_coordinator NHUNG KHONG la thanh vien user_coordinated.
    private PaginationResponse<Map<String, Object>> queryHoSoCVCoordinatedStatus(
            String dbType, String key, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        String trimmed = key.trim();
        String lower = trimmed.toLowerCase();
        boolean isDa = lower.endsWith("_da");
        boolean isChua = !isDa && lower.endsWith("_chua");
        if (!isDa && !isChua) {
            return emptyPage(pageable);
        }
        String username = trimmed.substring(0, trimmed.length() - (isDa ? 3 : 5));
        if (username.isBlank()) {
            return emptyPage(pageable);
        }
        Page<HoSoCVDocument> page = isDa
            ? hoSoCVDocumentRepository.findByListFieldMemberExcludingOther(
                dbType, HOSOCV_USER_COORDINATED_FIELD, null, username, pageable)
            : hoSoCVDocumentRepository.findByListFieldMemberExcludingOther(
                dbType, HOSOCV_USER_COORDINATOR_FIELD, HOSOCV_USER_COORDINATED_FIELD, username, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // "vwTrash": ho so da xoa mem.
    private PaginationResponse<Map<String, Object>> queryHoSoCVTrash(String dbType, String items, Pageable pageable) {
        Page<HoSoCVDocument> page = hoSoCVDocumentRepository.findDeleted(dbType, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // "vwUser_Process_Handler_Den_Han_Nop_Luu_Tru"/"vwOverdue_Processing"/"vwUser_Overdue_Processing"
    // - DON GIAN HOA CO CHU Y (xem javadoc hang so view): keyFieldName/keyValue null/rong = bo qua
    // khoa ca nhan (bien the "toan co quan"). KHONG guard blank-key tra empty page (khac cac helper
    // ca nhan khac) vi bien the thong ke hop le khi khong co khoa.
    private PaginationResponse<Map<String, Object>> queryHoSoCVDueDate(
            String dbType, String keyFieldName, String keyValue, String dateFieldName,
            boolean strictlyBefore, Map<String, String> extraEquals, String items, Pageable pageable) {
        Page<HoSoCVDocument> page = hoSoCVDocumentRepository.findByFieldValueDueDate(
            dbType, keyFieldName, keyValue, dateFieldName, strictlyBefore, extraEquals, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Port module "Ho so luu tru" (hosoluutru) - CUNG cong thuc voi queryHoSoCVPersonalQueue/
    // queryHoSoCVTrash nhung doc/ghi qua HoSoLuuTruDocumentRepository (bang rieng hosoluutru_documents)
    // - xem docs/analysis/16-...md muc 8.5. "identifier" la field scalar (KHONG phai list nhu
    // user_coordinator cua hosocv) - dung findByCriteria (matchAnyFieldNames=["identifier"]).
    // Port module "Phieu trinh tham dinh" (phieutrinhtd), 09/09/2026 - xem
    // docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md muc 6.3.
    private PaginationResponse<Map<String, Object>> queryPhieuTrinhTdByAnyField(
            List<String> fieldNames, String key, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<PhieuTrinhTdDocument> page = phieuTrinhTdDocumentRepository.findByAnyFieldContainsMember(
            fieldNames, key.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    private PaginationResponse<Map<String, Object>> queryPhieuTrinhTdByAnyFieldAndStatusIn(
            List<String> fieldNames, String key, List<String> statuses, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<PhieuTrinhTdDocument> page = phieuTrinhTdDocumentRepository.findByAnyFieldContainsMemberAndStatusIn(
            fieldNames, key.trim(), statuses, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // "Cho duyet": (lanhdao1 chua key AND status='5') OR (lanhdao2 chua key AND status='25') - khong
    // dien dat duoc bang 2 method tren (moi field can khop 1 status RIENG) - xem javadoc
    // PhieuTrinhTdDocumentRepository#findApproverWaiting.
    private PaginationResponse<Map<String, Object>> queryPhieuTrinhTdApproverWaiting(
            String key, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<PhieuTrinhTdDocument> page = phieuTrinhTdDocumentRepository.findApproverWaiting(key.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    private PaginationResponse<Map<String, Object>> queryPhieuTrinhTdThamKhao(
            String key, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<PhieuTrinhTdDocument> page = phieuTrinhTdDocumentRepository.findThamKhaoChoYKien(key.trim(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    private PaginationResponse<Map<String, Object>> queryPhieuTrinhTdTrash(String items, Pageable pageable) {
        Page<PhieuTrinhTdDocument> page = phieuTrinhTdDocumentRepository.findTrash(pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    private PaginationResponse<Map<String, Object>> queryHoSoLuuTruByIdentifier(
            String identifier, Map<String, List<String>> fieldValueIn, String items, Pageable pageable) {
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        Page<HoSoLuuTruDocument> page = hoSoLuuTruDocumentRepository.findByCriteria(
            hosoLuuTruDocumentType(), List.of(HOSOLUUTRU_IDENTIFIER_FIELD), identifier.trim(),
            fieldValueIn, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Tranh phu thuoc vong (import truc tiep hang so tu package hosoluutru) - gia tri co dinh
    // "hosoluutru", dung khuon HOSOCV_DOCUMENT_TYPE_CONSTANT da dung cho cum hosocv.
    private String hosoLuuTruDocumentType() {
        return "hosoluutru";
    }

    private PaginationResponse<Map<String, Object>> queryHoSoLuuTruTrash(String items, Pageable pageable) {
        Page<HoSoLuuTruDocument> page = hoSoLuuTruDocumentRepository.findDeleted(
            hosoLuuTruDocumentType(), pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Phieu yeu cau muon/tra: khoa ca nhan la identifier (kho xem theo don vi) HOAC user_creator
    // (nguoi muon xem cua chinh minh) - DON GIAN HOA CO CHU Y gop 2 truong hop lam 1 OR-match (Java
    // chua replicate day du phan biet quyen "edit"/khac cua PhieuYeuCauModal.jsx - xem tai lieu 16
    // muc 2.3).
    private PaginationResponse<Map<String, Object>> queryPhieuYeuCau(
            String key, Map<String, List<String>> fieldValueIn, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<HoSoLuuTruPhieuYeuCauDocument> page = hoSoLuuTruPhieuYeuCauDocumentRepository.findByCriteria(
            List.of(HOSOLUUTRU_IDENTIFIER_FIELD, HOSOLUUTRU_USER_CREATOR_FIELD), key.trim(), fieldValueIn, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // "phieuyeucau_quahan_by_identifier": dang muon (status=1, status1 rong/"0") VA return_date < hom
    // nay - loc them o tang Java (list nho, khong can predicate SQL rieng) vi
    // HoSoLuuTruPhieuYeuCauDocumentRepository chua ho tro so sanh ngay.
    private PaginationResponse<Map<String, Object>> queryPhieuYeuCauQuaHan(String key, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<HoSoLuuTruPhieuYeuCauDocument> page = hoSoLuuTruPhieuYeuCauDocumentRepository.findByCriteria(
            List.of(HOSOLUUTRU_IDENTIFIER_FIELD, HOSOLUUTRU_USER_CREATOR_FIELD), key.trim(),
            Map.of("status", List.of(HoSoLuuTruLifecycleStatus.PHIEU_CHAP_NHAN),
                HOSOLUUTRU_STATUS1_FIELD, List.of("", HoSoLuuTruLifecycleStatus.PHIEU_DANG_MUON)),
            Pageable.unpaged());
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<HoSoLuuTruPhieuYeuCauDocument> overdue = page.getContent().stream()
            .filter(document -> {
                Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
                Object returnDate = fields.get(HOSOLUUTRU_RETURN_DATE_FIELD);
                if (!(returnDate instanceof String dateStr) || !dateStr.matches("^[0-9]{2}/[0-9]{2}/[0-9]{4}$")) {
                    return false;
                }
                try {
                    return LocalDate.parse(dateStr, formatter).isBefore(today);
                } catch (Exception ex) {
                    return false;
                }
            })
            .toList();
        int start = (int) Math.min(pageable.getOffset(), overdue.size());
        int end = (int) Math.min(start + pageable.getPageSize(), overdue.size());
        List<HoSoLuuTruPhieuYeuCauDocument> pageSlice = overdue.subList(start, end);
        Page<HoSoLuuTruPhieuYeuCauDocument> resultPage = new PageImpl<>(pageSlice, pageable, overdue.size());
        List<Map<String, Object>> content = pageSlice.stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, resultPage);
    }

    private static final DateTimeFormatter DUTHAO_PROCESS_CREATED_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // "vwGroup_process_by_draft_id" (DT-007, 26/08/2026): khong guard blank-key rong tra empty page
    // nhu cac helper hang doi ca nhan khac (key o day la id du thao, khong phai username - repository
    // tu tra rong neu parentId khong hop le/khong khop dong nao, khong can guard rieng).
    private PaginationResponse<Map<String, Object>> queryDraftProcessHistory(
            String draftId, String items, Pageable pageable) {
        Page<DraftProcessDocument> page = draftProcessDocumentRepository.findByParentId(draftId, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredProcessFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // FE duthaofile.jsx (khong sua trong lan port nay) doc cot "Ngay gio" qua key "created" (render
    // truc tiep, khong qua formatter) - Domino GetView von tra chuoi da dinh dang san cho field nay;
    // Java format server-side de giu dung UX cu thay vi render ISO-8601 tho.
    private Map<String, Object> toFilteredProcessFields(DraftProcessDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.put("created", document.getCreatedOn() == null
            ? null : document.getCreatedOn().format(DUTHAO_PROCESS_CREATED_FORMATTER));
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // Bugfix 27/08/2026 — LAN 2, dao lai quyet dinh luc sang cung ngay (yeu cau nguoi dung
        // "quan ly tep dinh kem theo id cha, draft_process van the hien dung lan gan kem"): tep
        // KHONG con luu theo id vet nua - luu theo document_id = id du thao CHA (khop moi module
        // khac), gan them process_id = id vet nay khi upload (xem GanKemTepDuThaoUseCase/FE
        // ChiTietPage.jsx#onFinish). O day CHI can loc lai theo cap (parentDraftId, processId) qua
        // ListDocumentFilesByProcessUseCase - khong doi cach luu tru, chi doi dieu kien truy van.
        Object parentIdValue = allFields.get("parentid");
        allFields.put("files", collectProcessFiles(parentIdValue, document.getId()));
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    private List<Map<String, Object>> collectProcessFiles(Object parentIdValue, UUID processId) {
        UUID parentDraftId = parseUuidOrNull(parentIdValue);
        if (parentDraftId == null) {
            // Vet du lieu cu/hong khong co parentid hop le - khong co cach nao tra ve dung file,
            // tra rong thay vi loi (dong bo voi cac nhanh guard khac cua ViewQueryService).
            return List.of();
        }
        List<Map<String, Object>> files = new ArrayList<>();
        var request = ListFilesByProcessRequest.builder()
            .documentId(parentDraftId)
            .processId(processId)
            .build();
        PaginationResponse<FileDto> processFiles = listDocumentFilesByProcessUseCase.execute(request);
        processFiles.getItems().forEach(fileDto -> files.add(toFileMap(fileDto)));
        return files;
    }

    private UUID parseUuidOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // Khop dung shape {name, size, type} FE dang doc (Files/index.jsx, iconlistduthao.jsx) - cung
    // logic GetDuThaoDetailUseCase#toFileMap (khac module, khong tai dung truc tiep duoc).
    //
    // Bugfix (bao loi nguoi dung "thao tac vao tep dinh kem thi chua xem duoc"): them "file_id" -
    // xem chu thich day du o GetDuThaoDetailUseCase#toFileMap (cung nguyen nhan: FE van goi API
    // Domino cu de xem/tai tep, tep tai qua Java/MinIO khong ton tai o Domino nen phai co file_id de
    // FE tu chuyen sang duong dan Java that).
    private Map<String, Object> toFileMap(FileDto fileDto) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileDto.getOriginalFileName());
        file.put("size", fileDto.getFileSize());
        file.put("type", extractFileExtension(fileDto.getOriginalFileName()));
        file.put("file_id", fileDto.getFileId());
        return file;
    }

    private String extractFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * Slice 10 (dxl 2319-2348 chutri_group + đối chiếu {@code GetViewGroup.java}): nhóm văn bản
     * "chờ xử lý" của user hiện tại theo {@code unit_in_charge}, giảm dần theo tên nhóm. Tái dùng
     * {@link DocumentRepository#findPendingForUser} (đã có từ BL-102) — không cần query/hook mới,
     * chỉ group trong tầng application sau khi lấy hết pending docs của user (giới hạn
     * {@link #CHUTRI_GROUP_MAX_DOCUMENTS} — hàng đợi cá nhân, không phải toàn hệ thống).
     */
    private PaginationResponse<Map<String, Object>> queryChuTriGroup(
            String dbType, String items, String keyword, List<String> keywordColumns, Pageable pageable) {
        Optional<String> username = currentUserProvider.currentUsername();
        if (username.isEmpty()) {
            return emptyPage(pageable);
        }

        Page<BusinessDocument> allPending = hasKeyword(keyword, keywordColumns)
            ? documentRepository.findPendingForUser(
                dbType, username.get().trim(), keyword.trim(), keywordColumns,
                PageRequest.of(0, CHUTRI_GROUP_MAX_DOCUMENTS))
            : documentRepository.findPendingForUser(
                dbType, username.get().trim(), PageRequest.of(0, CHUTRI_GROUP_MAX_DOCUMENTS));

        Map<String, List<BusinessDocument>> byUnit = new TreeMap<>(Comparator.reverseOrder());
        for (BusinessDocument document : allPending.getContent()) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
            Object unitInCharge = fields.get(UNIT_IN_CHARGE_FIELD);
            String groupName = unitInCharge == null ? "" : String.valueOf(unitInCharge);
            byUnit.computeIfAbsent(groupName, key -> new ArrayList<>()).add(document);
        }

        List<Map<String, Object>> groups = byUnit.entrySet().stream()
            .map(entry -> {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("name", entry.getKey());
                group.put("key", entry.getKey());
                group.put("count", entry.getValue().size());
                group.put("documents", entry.getValue().stream()
                    .map(document -> toFilteredDocumentFields(document, items))
                    .toList());
                return group;
            })
            .toList();

        int totalGroups = groups.size();
        int fromIndex = Math.min((int) pageable.getOffset(), totalGroups);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), totalGroups);
        List<Map<String, Object>> pageContent = groups.subList(fromIndex, toIndex);

        return new PaginationResponse<>(
            pageContent, pageable.getPageNumber() + 1, pageable.getPageSize(), totalGroups,
            (int) Math.ceil((double) totalGroups / pageable.getPageSize()),
            fromIndex == 0, toIndex >= totalGroups);
    }

    // dxl 1041-1075: Form="DocumentIn" & status!="3" & status!="4" & (unit_process!="" |
    // unit_coordinate!="" | unit_to_read!="") & Internal!="1". Khoa = union 3 field (kiem tra !="" tuong
    // duong voi containment theo identifier vi view categorized dung chinh 3 field nay).
    private VbdenOrgViewFilter captrengiaoFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_CAPTRENGIAO_ORG_FIELDS)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY),
                "internal", List.of("1")))
            .build();
    }

    // dxl 2103-2136: Form="DocumentIn" & status!="3" & status!="4" & unit_process!="" & internal="1".
    // Khoa CHI unit_process (khong union 3 field nhu captrengiao).
    private VbdenOrgViewFilter dennoiboFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_DENNOIBO_ORG_FIELDS)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY)))
            .includeFieldValues(Map.of("internal", List.of("1")))
            .build();
    }

    // Bugfix 31/08/2026 (phat hien khi doi chieu du lieu mau xinykien - field "Reply" viet hoa chu
    // R): DynamicDocumentFieldsService.buildDynamicFieldsJson LUON lowercase ten field truoc khi luu
    // (entry.getKey().toLowerCase()) nen JSONB key THAT SU la "reply" - dynamic_fields->>'Reply' (chu
    // R hoa) se KHONG BAO GIO khop (Postgres JSONB key phan biet hoa/thuong), lam dieu kien loai tru
    // "Reply!=1" tro thanh vo tac dung (luon coi nhu rong -> luon thoa dieu kien loai tru, kha nang
    // van bo lot van ban that su co Reply="1"). Sua ca 4 view dung field nay (quamangnoibo/lienthong/
    // dendientu/denquamang) sang chu thuong - KHONG doi dieu kien nghiep vu, chi sua dung ten key.
    // dxl 1468-1501: Form="DocumentIn" & contact_unit_identifier!="" & Reply!="1" & document_internet="1".
    private VbdenOrgViewFilter quamangnoiboFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_CONTACT_UNIT_ORG_FIELDS)
            .excludeFieldValues(Map.of("reply", List.of("1")))
            .includeFieldValues(Map.of("document_internet", List.of("1")))
            .build();
    }

    // dxl 1430-1466: giong quamangnoibo nhung document_internet="2".
    private VbdenOrgViewFilter lienthongFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_CONTACT_UNIT_ORG_FIELDS)
            .excludeFieldValues(Map.of("reply", List.of("1")))
            .includeFieldValues(Map.of("document_internet", List.of("2")))
            .build();
    }

    // dxl 2434-2470: hop cua quamangnoibo va lienthong - document_internet IN ("1","2").
    private VbdenOrgViewFilter dendientuFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_CONTACT_UNIT_ORG_FIELDS)
            .excludeFieldValues(Map.of("reply", List.of("1")))
            .includeFieldValues(Map.of("document_internet", List.of("1", "2")))
            .build();
    }

    // dxl 1339-1372: Form="DocumentIn" & (contact_unit_identifier!="" | unit_process!="" |
    // unit_coordinate!="" | unit_to_read!="") & Reply!="1". KHONG dieu kien document_internet/status
    // (khac 3 view anh em phia tren).
    private VbdenOrgViewFilter denquamangFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_DENQUAMANG_ORG_FIELDS)
            .excludeFieldValues(Map.of("reply", List.of("1")))
            .build();
    }

    // dxl 828-858: Form="DocumentIn"|"DocumentIn_Deleted" & status="5" & is_document_refuse="1".
    // Khoa = 2-field union (contact_unit_identifier;identifier) - xem javadoc VIEW_VBDEN_TUCHOIDENQUAMANG.
    private VbdenOrgViewFilter tuChoiDenQuaMangFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_TUCHOIDENQUAMANG_ORG_FIELDS)
            .includeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_TU_CHOI_TIEP_NHAN),
                "is_document_refuse", List.of("1")))
            .build();
    }

    // dxl 860-892: Form="DocumentIn" & identifier_creator!="" - KHONG dieu kien status/ngay nao khac
    // trong CHINH formula view. Bo loc "khoang ngay nhan van ban" la tinh nang RangePicker FE tu them
    // (khong co trong DXL) - ap dung qua minReceivedDate/maxReceivedDate NEU request co gui, khong bat
    // buoc. Parse loi/rong -> bo qua dau do (khong throw, khong doan gia tri) - dung tinh than
    // ChuyenXuLyVbdenUseCase#parseDate.
    //
    // 09/09/2026 (yeu cau nguoi dung cutover route dashboard tu Domino qlvb/tichhop/TimKiemVanBanDenEs,
    // bo qua Elasticsearch): "toanbovanban" CHINH LA view man dashboard dung khi tim kiem nang cao
    // (Domino TimKiemVanBanDenEs mac dinh cung tim tren pham vi "toan bo van ban cua to chuc" nguoi
    // dung, chi khac o cho co them dieu kien loc) - nen ghep them buildAdvancedSearchFilter(request.
    // getSearch()) NGAY o day thay vi tao 1 VIEW_XXX/case rieng - request.search rong (man "Toan bo
    // van ban" thuong, khong bam tim kiem nang cao) => giu nguyen hanh vi cu (chi loc theo khoang ngay
    // nhan). request.search co du lieu (dashboard bam "Tim kiem nang cao") => hop nhat: cac dieu kien
    // nang cao (status/document_type/priority/sector/unit_in_charge/unit_process/user_process) tu
    // buildAdvancedSearchFilter, con receivedDateFrom/receivedDateTo (RangePicker o man thuong) VAN
    // duoc dung khi search.received_date KHONG duoc gui (2 nguon ngay nhan doc lap, uu tien search.
    // received_date neu FE gui ca 2 - dashboard luon gui qua search).
    private VbdenOrgViewFilter toanbovanbanFilter(ViewQueryRequest request) {
        VbdenOrgViewFilter advancedFilter = buildAdvancedSearchFilter(request.getSearch());
        LocalDate fallbackMinReceivedDate = parseDdMmYyyy(request.getReceivedDateFrom());
        LocalDate fallbackMaxReceivedDate = parseDdMmYyyy(request.getReceivedDateTo());
        if (advancedFilter == null) {
            return VbdenOrgViewFilter.builder()
                .orgFieldNames(VBDEN_TOANBOVANBAN_ORG_FIELDS)
                .minReceivedDate(fallbackMinReceivedDate)
                .maxReceivedDate(fallbackMaxReceivedDate)
                .build();
        }
        return advancedFilter.toBuilder()
            .orgFieldNames(VBDEN_TOANBOVANBAN_ORG_FIELDS)
            .minReceivedDate(advancedFilter.getMinReceivedDate() != null
                ? advancedFilter.getMinReceivedDate() : fallbackMinReceivedDate)
            .maxReceivedDate(advancedFilter.getMaxReceivedDate() != null
                ? advancedFilter.getMaxReceivedDate() : fallbackMaxReceivedDate)
            .build();
    }

    private LocalDate parseDdMmYyyy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), VBDEN_DD_MM_YYYY_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    // dxl 894-921: Form="DocumentIn" & status!="3" & status!="4" & (deadline_date!="" | documentreply="1").
    // Khoa = 8-field union theo "identifier" (khac identifier_creator - xem 2 filter daxuly_* ben duoi).
    private VbdenOrgViewFilter phaixulyCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY)))
            .requireDeadlineOrDocumentReply(true)
            .build();
    }

    // dxl 923-949: Form="DocumentIn" & status!="3" & status!="4" & deadline_date < today &
    // document_type!="Giấy mời". KHONG dua vao status="1" nhu suy doan truoc day - deadline that su
    // qua han (< hom nay), tinh song song voi status con dang xu ly.
    private VbdenOrgViewFilter quahanCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY),
                "document_type", List.of(GIAY_MOI_DOCUMENT_TYPE)))
            .maxDeadline(LocalDate.now().minusDays(1))
            .build();
    }

    // dxl 1009-1039: 0 <= (deadline_date-today) <= 2 (KHONG phai <=3 nhu suy doan Slice 3) &
    // document_type!="Giấy mời".
    private VbdenOrgViewFilter saptoihanCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY),
                "document_type", List.of(GIAY_MOI_DOCUMENT_TYPE)))
            .minDeadline(LocalDate.now())
            .maxDeadline(LocalDate.now().plusDays(2))
            .build();
    }

    // dxl 3232-3263: deadline_date = today (khong co dieu kien document_type).
    private VbdenOrgViewFilter denhanCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY)))
            .minDeadline(LocalDate.now())
            .maxDeadline(LocalDate.now())
            .build();
    }

    // dxl 980-1007: status="3". Khoa = 8-field union theo "identifier_creator" (khac 4 view tren).
    private VbdenOrgViewFilter daxulyQuahanCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER_CREATOR)
            .includeFieldValues(Map.of("status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY)))
            .build();
    }

    // dxl 951-978: status="4". Khoa = 8-field union theo "identifier_creator".
    private VbdenOrgViewFilter daxulyDunghanCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER_CREATOR)
            .includeFieldValues(Map.of("status", List.of(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY)))
            .build();
    }

    // dxl 253-278: Form="DocumentIn" & user_process="" & identifier_creator!="" & status!="3" &
    // status!="4". Khoa CHI identifier_creator (khong union 8 field nhu nhom han xu ly).
    private VbdenOrgViewFilter chophanxulyFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_CHOPHANXULY_ORG_FIELDS)
            .excludeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY)))
            .requireEmptyFieldName("user_process")
            .build();
    }

    // dxl 1249-1276: Form="DocumentIn" (khong dieu kien status/ngay nao khac - "toan bo van ban cua
    // to chuc"). Khoa = union 8 field theo identifier_creator (giong daxuly_quahan/dunghan_cuatochuc).
    private VbdenOrgViewFilter cuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER_CREATOR)
            .build();
    }

    // dxl 1278-1305: Form="DocumentIn" & (status="3"|status="4"). Khoa = union 8 field theo
    // "identifier" (khac cuatochuc dung identifier_creator).
    private VbdenOrgViewFilter daxulyCuatochucFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_HAN_XL_ORG_FIELDS_BY_IDENTIFIER)
            .includeFieldValues(Map.of(
                "status", List.of(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY, VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY)))
            .build();
    }

    // dxl 1503-1536: Form="DocumentIn" & unit_discard!="". Khoa 1 field to chuc, cung khuon mau captrengiao.
    private VbdenOrgViewFilter khongvaosoFilter() {
        return VbdenOrgViewFilter.builder()
            .orgFieldNames(VBDEN_KHONGVAOSO_ORG_FIELDS)
            .build();
    }

    // Dung chung cho moi view org-scoped cua vbden chua co org-membership check day du (N03/N05/
    // N12/N13/N22) - xem javadoc queryVbdenOrgView ve quyet dinh an ninh.
    //
    // BUGFIX 21/08/2026: TRUOC DAY dung AppUser.getIdentifier() (field phang don le) - SAI voi user
    // co cay to chuc nhieu cap, vi document_register_id/identifier_creator luc tao (TiepNhanVbdenUseCase)
    // duoc gan theo DocumentRegister chon bang getIdentifier_by_level(currentUser) (FE) - 1 gia tri
    // KHAC field phang. Doi sang ResolveIdentifierByLevelUseCase (dung DUNG thuat toan do server-side)
    // - xem javadoc lop do va D.14/D.15 trong docs/analysis/01-...vbden-fe-sang-java.md.
    private String resolveCurrentIdentifier() {
        return resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
    }

    private PaginationResponse<Map<String, Object>> queryUserByUsername(String username, String items, Pageable pageable) {
        if (username == null || username.isBlank()) {
            return emptyPage(pageable);
        }

        Optional<AppUser> userOptional = appUserRepository.findByUsernameAndDeletedOnIsNull(username.trim());
        if (userOptional.isEmpty()) {
            return emptyPage(pageable);
        }

        Map<String, Object> fields = dynamicDocumentFieldsService.filterItems(toUserFields(userOptional.get()), items);
        return new PaginationResponse<>(List.of(fields), 1, Math.max(1, pageable.getPageSize()), 1L, 1, true, true);
    }

    private PaginationResponse<Map<String, Object>> queryUsersByParentId(
            String parentId, String items, String keyword, Pageable pageable) {
        UUID organizationId = parseUuid(parentId);
        if (organizationId == null) {
            return emptyPage(pageable);
        }

        SearchAppUsersRequest searchRequest = new SearchAppUsersRequest();
        searchRequest.setOrganizationId(organizationId);
        // ItemsActiveList.jsx (25/08/2026): o tim kiem "Nhap tu khoa tim kiem" - PostgresAppUserRepository
        // da co san keywordFilter (username/fullName/email), chi can forward, khong can hook moi.
        if (keyword != null && !keyword.isBlank()) {
            searchRequest.setKeyword(keyword.trim());
        }
        Page<AppUser> page = appUserRepository.search(searchRequest, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(user -> ItemsFilter.filter(toUserFieldsWithOrgProfile(user), items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * "Người ký" ({@code vwuserleaderbyparentid}) — dùng bởi dropdown chọn người ký ở nhiều màn
     * soạn thảo (duthao/vbcddh/vbdi/phieutrinhTD) SAU khi đã chọn "Cấp ban hành". Chỉ trả về những
     * {@link AppUser} thuộc tổ chức {@code parentId} VÀ có {@link AppUser#getCapLanhDao()} khác
     * rỗng/{@code "khong"} — tương đương điều kiện {@code caplanhdao != "" & caplanhdao != "khong"}
     * đã dùng ở {@code publish.java#nguoiduyet} (Domino thật, cùng khái niệm "lãnh đạo").
     *
     * <p>Lọc/phân trang thủ công thay vì đẩy filter xuống {@link SearchAppUsersRequest} (chưa hỗ
     * trợ lọc theo {@code capLanhDao}) — chấp nhận được vì số lãnh đạo/1 tổ chức luôn nhỏ (dùng
     * {@code pageSize} tối đa 200 của {@code search()} để lấy đủ 1 tổ chức trong 1 lượt gọi).</p>
     */
    private PaginationResponse<Map<String, Object>> queryUserLeadersByParentId(String parentId, String items, Pageable pageable) {
        UUID organizationId = parseUuid(parentId);
        if (organizationId == null) {
            return emptyPage(pageable);
        }

        SearchAppUsersRequest searchRequest = new SearchAppUsersRequest();
        searchRequest.setOrganizationId(organizationId);
        searchRequest.setIsActive(true);
        Page<AppUser> orgUsers = appUserRepository.search(searchRequest, PageRequest.of(0, 200));

        List<AppUser> leaders = orgUsers.getContent().stream()
            .filter(this::isLeader)
            .sorted(Comparator.comparingInt(AppUser::getSortOrder))
            .toList();

        int pageSize = Math.max(1, pageable.getPageSize());
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int fromIndex = Math.min(pageNumber * pageSize, leaders.size());
        int toIndex = Math.min(fromIndex + pageSize, leaders.size());
        List<AppUser> pageContent = leaders.subList(fromIndex, toIndex);

        List<Map<String, Object>> content = pageContent.stream()
            .map(user -> ItemsFilter.filter(toLeaderFields(user), items))
            .toList();
        Page<AppUser> leaderPage = new PageImpl<>(pageContent, PageRequest.of(pageNumber, pageSize), leaders.size());
        return toPaginationResponse(content, leaderPage);
    }

    private boolean isLeader(AppUser user) {
        String capLanhDao = user.getCapLanhDao();
        return capLanhDao != null && !capLanhDao.isBlank() && !NOT_A_LEADER_VALUE.equalsIgnoreCase(capLanhDao.trim());
    }

    // Hinh dang FE dang doc that (xem ChiTietPage.jsx: items="name;username;sort_order;position;
    // caplanhdao;delegation_obj"). "position"/"delegation_obj" chua co cot tuong minh - doc tu
    // AppUser.dynamicFieldsJson (dung muc dich da ghi trong javadoc GetUserOrganizationProfileUseCase
    // ve field "position").
    private Map<String, Object> toLeaderFields(AppUser user) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(user.getDynamicFieldsJson());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", user.getId());
        fields.put("name", user.getFullName());
        fields.put("username", user.getUsername());
        fields.put("sort_order", user.getSortOrder());
        fields.put("position", dynamicFields.getOrDefault("position", ""));
        fields.put("caplanhdao", user.getCapLanhDao());
        fields.put("delegation_obj", dynamicFields.getOrDefault("delegation_obj", ""));
        return fields;
    }

    private Map<String, Object> toFilteredDocumentFields(BusinessDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        // Bugfix 24/08/2026: doi ten key sang "databasetype" - xem javadoc GetDynamicDocumentUseCase
        // (cung ly do, cung ngay) de tranh dung ten voi field nghiep vu "document_type" thuc.
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // Bugfix 22/08/2026 (bao loi nguoi dung: cot "trich yeu" trong danh sach vbden trong -
        // cung nguyen nhan GetVbdenDetailUseCase/GetSoVanBanDenUseCase da sua): TiepNhanVbdenUseCase
        // luu "trich yeu" vao COT entity "title", khong vao dynamic_fields.subject - van ban tao qua
        // Java khong co "subject". Chi ap dung cho vbden (field nay la khai niem RIENG cua module
        // vbden, xem javadoc - cac document_type khac khong dung "subject" nen khong dong cham).
        if ("vbden".equalsIgnoreCase(document.getDocumentType())) {
            allFields.putIfAbsent("subject", document.getTitle());
            // Bo sung 04/09/2026 (yeu cau nguoi dung: "khi gan tep dinh kem luu luon thong tin files
            // vao dynamic_fields de khong phai doc lai DB de map, neu khong co moi doc lai (van ban
            // di da lam nhu vay)"): VbdenFilesJsonbSync (td-infrastructure) da ghi san "files" vao
            // dynamic_fields moi lan upload/xoa/doi ten tep - putAll o tren DA TU DONG lay duoc key
            // nay neu co, khong can code them. Chi khi document CU (tao truoc khi co sync nay, hoac
            // dong bo thieu vi ly do nao do) MOI can doc lai DB qua listDocumentFilesUseCase - cung
            // "chi truy van khi that su can" (wantsField) da dung o toFilteredDocumentFields(DuThaoDocument,...).
            if (wantsField(items, "files") && !allFields.containsKey("files")) {
                allFields.put("files", collectVbdenListFiles(document.getId()));
            }
        }
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    // Fallback DB khi dynamic_fields.files chua co (xem chu thich o toFilteredDocumentFields(BusinessDocument,...)
    // nhanh "vbden") - cung shape {name,size,type} voi collectDuThaoListFiles/VbdenFilesJsonbSync#toFileMap.
    private List<Map<String, Object>> collectVbdenListFiles(UUID documentId) {
        List<Map<String, Object>> files = new ArrayList<>();
        listDocumentFilesUseCase.execute(documentId).getItems().forEach(fileDto -> files.add(toFileMap(fileDto)));
        return files;
    }

    // Cum du thao (24/08/2026): DuThaoDocument KHONG cung kieu voi BusinessDocument (2 bang vat ly
    // doc lap - xem javadoc DuThaoDocument), can overload rieng - CUNG cong thuc field nhung khong
    // co nhanh "vbden" (khong lien quan cum du thao).
    private Map<String, Object> toFilteredDocumentFields(DuThaoDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        // Bugfix 26/08/2026 - đồng bộ với GetDuThaoDetailUseCase: expose đủ 4 cột audit.
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("last_modified_by", document.getLastModifiedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // Bugfix (bao loi nguoi dung "Luu subject=title, chua thay luu vao json"): cung nguyen nhan
        // da sua o toFilteredDocumentFields(BusinessDocument,...)/(VbdiDocument,...) - LuuDuThaoUseCase
        // dong bo "subject" nguoi dung nhap vao COT title, KHONG phai vao dynamic_fields (chi co neu
        // dung Luu() sau nay va request co gui "subject" - xem javadoc LuuDuThaoUseCase). putIfAbsent
        // (KHONG de) de van ban da tung duoc Luu() voi "subject" rieng (co the lech title) giu dung
        // gia tri da luu.
        allFields.putIfAbsent("subject", document.getTitle());
        // Bugfix (bao loi nguoi dung "danh sach chua hien thi duoc tep dinh kem (tepbanhanh)"): cot
        // "File" (ListIconFiles, tableconfig.jsx) doc "record.files" (shape {name,size,type}) - field
        // nay KHONG nam trong dynamic_fields (luu o bang file_metadata rieng) nen chua bao gio co mat
        // o day, khac han "tepbanhanh" (field dong, chi can them vao "items" ma FE gui). Chi truy van
        // khi "items" thuc su can den (getall hoac co liet ke "files") de khong doi query moi dong cho
        // moi danh sach khong hien thi cot nay.
        if (wantsField(items, "files")) {
            allFields.put("files", collectDuThaoListFiles(document.getId()));
        }
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    // Mo rong BL-240 - hosocv_documents (bang vat ly doc lap): HoSoCVDocument KHONG cung kieu voi
    // BusinessDocument/DuThaoDocument, can overload rieng - CUNG cong thuc field, khong co nhanh
    // "vbden"/"subject" (khong lien quan hosocv).
    private Map<String, Object> toFilteredDocumentFields(HoSoCVDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("last_modified_by", document.getLastModifiedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        if (wantsField(items, "files")) {
            List<Map<String, Object>> files = new ArrayList<>();
            listDocumentFilesUseCase.execute(document.getId()).getItems().forEach(fileDto -> files.add(toFileMap(fileDto)));
            allFields.put("files", files);
        }
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    // Port module "Ho so luu tru" - CUNG cong thuc voi toFilteredDocumentFields(HoSoCVDocument,...)
    // nhung khong co cot "files" rieng (chua co bang chung FE can cot nay o danh sach hosoluutru).
    private Map<String, Object> toFilteredDocumentFields(HoSoLuuTruDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("form", document.getStatus());
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("last_modified_by", document.getLastModifiedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    private Map<String, Object> toFilteredDocumentFields(HoSoLuuTruPhieuYeuCauDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("created_by", document.getCreatedBy());
        allFields.put("created_on", document.getCreatedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    private boolean wantsField(String items, String field) {
        if (items == null || items.isBlank() || "getall".equalsIgnoreCase(items.trim())) {
            return true;
        }
        return ItemsFilter.splitSemicolonList(items).contains(field);
    }

    // Tai dung toFileMap()/extractFileExtension() da co san trong CHINH file nay (dung cho nhanh
    // "Quan trinh xu ly" - xem toFilteredProcessFields phia tren) - cung shape {name,size,type}.
    private List<Map<String, Object>> collectDuThaoListFiles(UUID draftId) {
        List<Map<String, Object>> files = new ArrayList<>();
        listDocumentFilesUseCase.execute(draftId).getItems().forEach(fileDto -> files.add(toFileMap(fileDto)));
        return files;
    }

    // Cum van ban di (tach bang rieng): VbdiDocument KHONG cung kieu voi BusinessDocument (2 bang vat
    // ly doc lap - xem javadoc VbdiDocument), can overload rieng - CUNG cong thuc field.
    //
    // Bugfix 28/08/2026 (nguoi dung bao "chua thay hien thi du lieu" o vwCreator_Drafting, dieu tra
    // ra that ra la list RONG do van ban di test bi thu hoi het - nhung tien the phat hien them 1
    // bug that: cot "Trich yeu" luon rong cho van ban di tao qua ChuyenThanhVanBanDiUseCase). Cung
    // nguyen nhan/cung fix nhu GetVbdiDetailUseCase (dong 69) + toFilteredDocumentFields(BusinessDocument,...)
    // nhanh "vbden" o tren: dU thao KHONG luu "subject" vao dynamic_fields (LuuDuThaoUseCase dong bo
    // subject -> COT title, khong vao dynamic_fields) - ChuyenThanhVanBanDiUseCase copy nguyen dynamic
    // fields cua du thao sang vbdi nen "subject" luon vang mat tren van ban di duoc tao ra, du
    // createVbdiDocument da set dung title (chi thieu o BUOC DOC/hien thi danh sach). KHAC ban
    // BusinessDocument o tren (chi ap dung rieng vbden) - o day ap dung LUON cho MOI van ban di, vi
    // overload nay CHi phuc vu documentType=vbdi (khong co documentType nao khac di qua day).
    // BL-250 (VBCDDH Core), bo sung 08/09/2026 (2) - xem javadoc cac hang so VIEW_VBCDDH_* (dxl that,
    // view_vbcddh.dxl). extraPredicateSql = null nghia khong loc them (view "Toan bo" bare).
    private PaginationResponse<Map<String, Object>> queryVbcddhByDvthFields(
            String form, List<String> fieldNames, String member, String extraPredicateSql,
            String items, Pageable pageable) {
        if (member == null || member.isBlank()) {
            return emptyPage(pageable);
        }
        Page<VbcddhDocument> page = vbcddhDocumentRepository.findByFormAndAnyFieldContainsMember(
            VBCDDH_DOCUMENT_TYPE, form, fieldNames, member, extraPredicateSql, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Nhom 1 (vwNhiem_Vu_By_Donvithuchienhide*) - khoa UNION donvithuchienhide+chuyenvienthuchienhide.
    private PaginationResponse<Map<String, Object>> queryVbcddhByDvth(
            String member, String extraPredicateSql, String items, Pageable pageable) {
        return queryVbcddhByDvthFields(VBCDDH_FORM_NHIEMVU, VBCDDH_DVTH_FIELDS, member, extraPredicateSql, items, pageable);
    }

    // Nhom 3 (vwVan_Ban_Ca_Nhan_Co_Quan*) - form=vanban, cung khoa UNION 6 field voi Nhom 2, KHONG loai.
    private PaginationResponse<Map<String, Object>> queryVbcddhVanBan(
            String member, String extraPredicateSql, String items, Pageable pageable) {
        return queryVbcddhByDvthFields(
            VBCDDH_FORM_VANBAN, VBCDDH_IDENTITY_UNION_FIELDS, member, extraPredicateSql, items, pageable);
    }

    // Nhom 2 (vwNhiem_Vu_Ca_Nhan_Co_Quan*) - khoa 2 CAP: keys.get(0)=typeLoaiNhiemVu (nvtuqlvb/
    // nvtheochidao/all), keys.get(1)=danh tinh (UNION 6 field) - dung dung thu tu FE gui
    // (NhiemVuItemsList.jsx dong ~188: viewkey = [typeLoaiNhiemVu, coquanid?.toUpperCase()]), KHONG
    // dung firstKey() (chi lay 1 khoa).
    private PaginationResponse<Map<String, Object>> queryVbcddhCaNhanCoQuan(
            List<String> keys, String extraPredicateSql, String items, Pageable pageable) {
        String loaiNhiemVu = keys != null && keys.size() > 0 ? keys.get(0) : null;
        String identifier = keys != null && keys.size() > 1 ? keys.get(1) : null;
        if (identifier == null || identifier.isBlank()) {
            return emptyPage(pageable);
        }
        Page<VbcddhDocument> page = vbcddhDocumentRepository.findByFormAndAnyFieldContainsMemberAndLoaiNhiemVu(
            VBCDDH_DOCUMENT_TYPE, VBCDDH_FORM_NHIEMVU, VBCDDH_IDENTITY_UNION_FIELDS, identifier,
            (loaiNhiemVu == null || loaiNhiemVu.isBlank()) ? "all" : loaiNhiemVu, extraPredicateSql, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // vwNhiem_Vu_Da_Xoa/vwVan_Ban_Da_Xoa (thung rac) - chi loc form, khong co khoa nao khac (dxl xac nhan).
    private PaginationResponse<Map<String, Object>> queryVbcddhByForm(
            String form, String extraPredicateSql, String items, Pageable pageable) {
        Page<VbcddhDocument> page =
            vbcddhDocumentRepository.findByForm(VBCDDH_DOCUMENT_TYPE, form, extraPredicateSql, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // Bugfix 16/09/2026 (port tab "Danh sach nhiem vu" o chi tiet vbden, xem javadoc
    // VbcddhDocumentRepository#findByAnyFieldContainsMember): vwNhiem_Vu_By_Idvanban khoa la UNION 4
    // field (idvanban/idvbdi/idvbden/idduthao) - thay the queryVbcddhByFieldValue cu (chi khop
    // idvanban, bo sot nhiem vu phat sinh tu vbden/vbdi/duthao).
    private PaginationResponse<Map<String, Object>> queryVbcddhByAnyFieldValue(
            List<String> fieldNames, String key, String items, Pageable pageable) {
        if (key == null || key.isBlank()) {
            return emptyPage(pageable);
        }
        Page<VbcddhDocument> page =
            vbcddhDocumentRepository.findByAnyFieldContainsMember(VBCDDH_DOCUMENT_TYPE, fieldNames, key, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    /**
     * vwTien_Do_Nhiem_Vu_By_Idnhiemvu (dxl: {@code Form = "tiendonhiemvu" & idnhiemvu != ""}, đóng
     * gap G4 "lịch sử tiến độ"). <b>Phát hiện đáng chú ý</b>: view này nằm TRONG DXL của
     * {@code vbcddh.nsf} (không phải {@code vbcddh_res.nsf}, vốn chỉ export view mặc định
     * {@code SELECT @All}) — mâu thuẫn với bằng chứng FE ({@code TienDoNhiemVuItemsList.jsx} gọi
     * {@code qlvb/view/query?databasetype=vbcddh_res}) và với chính {@code CapNhatTienDoUseCase} hiện
     * tại (ghi {@code document_type=vbcddh_res}, dựa trên đọc code {@code LuuVet()} trong
     * {@code ChuyenXuLyVBCDDH.java}). Có thể do {@code databasetype=vbcddh_res} là ALIAS trỏ về CÙNG
     * .nsf vật lý (tên "databasetype" không nhất thiết trùng tên file), hoặc design 2 database bị lệch
     * đồng bộ — KHÔNG đủ bằng chứng để đảo ngược quyết định {@code documentType=vbcddh_res} đã áp
     * dụng nhất quán từ BL-250 gốc. Giữ NGUYÊN {@code documentType="vbcddh_res"} (đúng hợp đồng FE
     * {@code databasetype} — cái FE/BE thực sự thoả thuận qua API, bất kể Domino nội bộ dùng .nsf
     * nào), chỉ port ĐÚNG công thức lọc ({@code form="tiendonhiemvu"} — CHỈ tiến độ, KHÔNG bao gồm
     * {@code ketquaxulynhiemvu}; view cho "kết quả" không có trong DXL, chưa port).
     */
    private PaginationResponse<Map<String, Object>> queryVbcddhTienDoByIdNhiemVu(
            String idNhiemVu, String items, Pageable pageable) {
        if (idNhiemVu == null || idNhiemVu.isBlank()) {
            return emptyPage(pageable);
        }
        Page<VbcddhDocument> page = vbcddhDocumentRepository.findByFormAndFieldValue(
            CapNhatTienDoUseCase.CHILD_DOCUMENT_TYPE, "tiendonhiemvu", "idnhiemvu", idNhiemVu, pageable);
        List<Map<String, Object>> content = page.getContent().stream()
            .map(document -> toFilteredDocumentFields(document, items))
            .toList();
        return toPaginationResponse(content, page);
    }

    // BL-250 (VBCDDH Core), bo sung 08/09/2026 - bang vat ly doc lap vbcddh_documents, cung cong thuc
    // field voi toFilteredDocumentFields(VbdiDocument,...)/(XinYKienDocument,...) - khong co nhanh
    // "putIfAbsent subject" (nhiemvu/tiendonhiemvu khong co khai niem "subject" nhu van ban di).
    // Port module "Phieu trinh tham dinh" (phieutrinhtd), 09/09/2026 - cung cong thuc field voi
    // toFilteredDocumentFields(VbcddhDocument,...).
    private Map<String, Object> toFilteredDocumentFields(PhieuTrinhTdDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    private Map<String, Object> toFilteredDocumentFields(VbcddhDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    private Map<String, Object> toFilteredDocumentFields(VbdiDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        allFields.putIfAbsent("subject", document.getTitle());
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    // N08 (31/08/2026) - cung khuon toFilteredDocumentFields(VbdiDocument,...) (bang vat ly doc lap
    // rieng, khong co "subject" trong dynamic_fields khi vua tao qua GuiXinYKienUseCase - xem javadoc
    // use case do - nen putIfAbsent ve title, dung tien le da co).
    private Map<String, Object> toFilteredDocumentFields(XinYKienDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        allFields.putIfAbsent("subject", document.getTitle());
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    // BL-210 DoD (4), yeu cau nguoi dung 05/09/2026 ("tach vbden_res sang bang rieng"): VbdenResDocument
    // KHONG cung kieu voi BusinessDocument (bang vat ly doc lap - xem javadoc VbdenResDocument), can
    // overload rieng - CUNG cong thuc field voi toFilteredDocumentFields(XinYKienDocument,...)/
    // (VbdiDocument,...), KHONG co nhanh "vbden" (nhanh do chi ap dung document_type='vbden' - vbden_res
    // khong bao gio khop, dung y het hanh vi cu qua toFilteredDocumentFields(BusinessDocument,...)).
    private Map<String, Object> toFilteredDocumentFields(VbdenResDocument document, String items) {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return dynamicDocumentFieldsService.filterItems(allFields, items);
    }

    private Map<String, Object> toUserFields(AppUser user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", user.getId());
        fields.put("username", user.getUsername());
        fields.put("full_name", user.getFullName());
        fields.put("email", user.getEmail());
        fields.put("organization_id", user.getOrganizationId());
        fields.put("position_id", user.getPositionId());
        fields.put("is_active", user.isActive());
        return fields;
    }

    // vwuserbyparentid (25/08/2026, ItemsActiveList.jsx - "Danh sách người dùng" theo tổ chức đã
    // chọn trong TreeComponent): FE đọc field kiểu Domino cũ (name/position/unit/department/agency/
    // agency_level/caplanhdao/level, KHÔNG phải full_name/organization_id/position_id thô của
    // toUserFields) - tái dùng NGUYÊN VẸN GetUserOrganizationProfileUseCase#buildProfileForUser
    // (đã có, đã dùng cho qlvb/profile) thay vì tự suy luận lại breadcrumb tổ chức lần 2. Giữ
    // NGUYÊN toUserFields() không đổi (queryUserByUsername/vwuserbyusername vẫn cần đúng shape cũ,
    // xem test ViewQueryServiceTest) - đây là method RIÊNG, chỉ dùng cho case này.
    //
    // KHÔNG port "parentids_obj"/level==="1" (field Domino cũ dùng để lấy agencyname/mailin/
    // identifier/dbpath khi user thuộc thẳng 1 agency) - đã xác nhận qua rà soát
    // ItemsActiveList.jsx: output nhánh đó KHÔNG có nơi tiêu thụ nào ở luồng ModalNguoiNhanANTD.jsx,
    // VÀ "level" của Java (độ sâu tính từ gốc, UserOrganizationProfileDto#getLevel) khác hẳn ngữ
    // nghĩa Domino cũ ("1" = tự thân là agency) - giữ lại check cũ sẽ SAI ý nghĩa, không phải chỉ
    // thiếu field.
    private Map<String, Object> toUserFieldsWithOrgProfile(AppUser user) {
        Map<String, Object> fields = new LinkedHashMap<>(toUserFields(user));
        UserOrganizationProfileDto profile = getUserOrganizationProfileUseCase.buildProfileForUser(user);
        fields.put("name", user.getFullName());
        fields.put("position", profile.getPosition());
        fields.put("caplanhdao", profile.getCapLanhDao());
        fields.put("level", profile.getLevel());
        fields.put("agency_level", profile.getAgencyLevelName());
        fields.put("agency_level_id", profile.getAgencyLevelId());
        fields.put("agency", profile.getAgencyName());
        fields.put("agency_id", profile.getAgencyId());
        fields.put("unit", profile.getUnitName());
        fields.put("unit_id", profile.getUnitId());
        fields.put("department", profile.getDepartmentName());
        fields.put("department_id", profile.getDepartmentId());
        return fields;
    }

    private <T> PaginationResponse<T> toPaginationResponse(List<T> content, Page<?> page) {
        return new PaginationResponse<>(
            content,
            page.getNumber() + 1,
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }

    private <T> PaginationResponse<T> emptyPage(Pageable pageable) {
        return new PaginationResponse<>(List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0L, 0, true, true);
    }

    /**
     * BL-102/BL-210: {@code pageNumber} là tham số bên ngoài 1-based (khớp đúng quy ước legacy
     * Domino — xem {@code TDUlties.java}: {@code startIdx = (pageNumber - 1) * pageSize} — và
     * hợp đồng đã ghi trong {@code docs/migration/06-cross-reference-fe-be-dynamic.md}: "FE không
     * gửi pageNumber/pageSize → BE mặc định 1/20"), chuyển sang page index 0-based nội bộ cho
     * Spring Data {@code PageRequest}.
     */
    private Pageable buildPageable(ViewQueryRequest request) {
        int pageNumber = Math.max(1, request.getPageNumber()) - 1;
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 200);
        return PageRequest.of(pageNumber, pageSize);
    }

    private String firstKey(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        return keys.get(0);
    }

    private String normalizeViewname(String viewname) {
        return viewname == null ? "" : viewname.trim().toLowerCase();
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
