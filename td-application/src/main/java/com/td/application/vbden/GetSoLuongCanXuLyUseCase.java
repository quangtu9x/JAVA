package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Số lượng văn bản/hồ sơ cần xử lý CỦA NGƯỜI DÙNG ĐANG ĐĂNG NHẬP — dùng để hiển thị badge số trên
 * menu (xem {@code SidebarMenuSystemXulyCongViec.jsx}/{@code SidebarMenuSystemHoSoCV.jsx}) và tự
 * làm mới cache phía FE (chuông thông báo). Port từ Domino {@code SoLuongCanXuLy.java} + bảng cấu
 * hình {@code MapsSoLuongCanXuLy.java} (tương thích {@code qlvb/vbden/SoLuongCanXuLy}).
 *
 * <p><b>Cập nhật 09/09/2026 (2) — đối chiếu lại toàn bộ với DXL, port ĐỦ 44/44 field cá nhân</b>
 * (đợt port đầu chỉ có 38/44 — 6 field còn thiếu, xem lịch sử javadoc trước; người dùng bổ sung
 * DXL {@code view_vanbanden_dennoibo_choxuly.dxl} và yêu cầu đối chiếu lại):
 * <ul>
 *   <li>{@code VBDEN} (15 field — 14/14 khớp {@code Maps.tsx} + 1 bổ sung {@code user_manage} chưa
 *       có trong bản mirror FE, xem {@link #buildVbdenPersonal}): thêm {@code dennoibo_choxuly}
 *       (dxl mới {@code view_vanbanden_dennoibo_choxuly.dxl} — {@code Form="DocumentIn"}, khoá field
 *       {@code user_receiver}, CÙNG công thức cá nhân như {@code chophanphoi}) và
 *       {@code phutrach_chuaxem} (dxl {@code view_vanbanden.dxl} dòng 2559-2584 — khoá field
 *       {@code user_manage}). Cả 2 field đã xác nhận field Java THẬT đang ghi (không phải field
 *       chết) — xem {@code ChuyenXuLyVbdenUseCase}/{@code ThuHoiVanBanUseCase}/
 *       {@code GetVbdenDetailUseCase}.</li>
 *   <li>{@code DUTHAO} (12/12 — xem {@link #buildDuThaoPersonal}):
 *     <ul>
 *       <li><b>Sửa lỗi ánh xạ (đợt port đầu SAI)</b>: {@code drafting_creator_documentout_submited}
 *           trước đây trỏ nhầm sang view {@code vwCreator_DocumentOut_Submited} (dxl 334-369, status
 *           "4" — KHÔNG lọc {@code xoavanbandi}) — do đoán theo tên trong bản mirror FE
 *           ({@code Maps.tsx}). Đối chiếu lại bảng CHUẨN (Domino {@code MapsSoLuongCanXuLy.java})
 *           xác nhận field JSON này thật ra ứng với view {@code vwDocumentOut_Submited} (KHÔNG có
 *           "Creator_", dxl 1608-1641 — CÙNG field khoá/status nhưng THÊM điều kiện
 *           {@code xoavanbandi=""}) — 2 view Domino THẬT SỰ khác nhau (không phải 1 view bị đổi tên).
 *           Đã sửa sang {@link com.td.application.viewquery.ViewQueryService}
 *           {@code VIEW_DUTHAO_DOCUMENTOUT_SUBMITED} ("vwdocumentout_submited", view MỚI thêm cùng
 *           đợt sửa này) — theo dữ liệu Java hiện tại 2 view cho CÙNG kết quả (status="4" và
 *           {@code xoavanbandi} khác rỗng loại trừ lẫn nhau, xem {@code DuThaoVanThuKhongBanHanhUseCase})
 *           nhưng port đúng theo dxl để an toàn nếu sau này có luồng khác phá vỡ ràng buộc đó.</li>
 *       <li><b>Thêm 2 field</b> {@code vwChoDuyetKyBanHanh}/{@code vwChoDuyetKyBanHanhMC} (dxl
 *           1806-1872) — JSON key TRÙNG tên view (Domino {@code MapsSoLuongCanXuLy.java} tự ánh xạ
 *           key==value, KHÔNG phải field chết/chưa hoàn thiện như từng nhận định SAI ở đợt port đầu —
 *           đây là 2 field THẬT, chỉ là không dịch nghĩa sang snake_case như các field khác). Mỗi
 *           field là OR-group giữa 2 field/2 điều kiện status khác nhau
 *           ({@code (user_signer!="" & status∈{1,3}) | (user_other_process_handler!="" & status="9")}),
 *           cộng thêm điều kiện {@code mc_mahs} rỗng/khác rỗng phân biệt 2 view —
 *           {@link com.td.application.documents.VbdenOrgViewFilter} (1 nhóm AND) không biểu diễn được
 *           OR 2-field-2-status trong 1 query; vì 2 nhánh loại trừ lẫn nhau trên status (không văn
 *           bản nào vừa status∈{1,3} vừa status="9"), TỔNG 2 query riêng (mỗi nhánh là 1 nhóm AND đơn
 *           giản) cho ĐÚNG số lượng — cùng nguyên tắc đã dùng cho {@code pt_tralai} ở
 *           {@code GetKiemNhiemPendingCountUseCase}. 4 viewname nội bộ
 *           ({@code VIEW_DUTHAO_CHODUYETKYBANHANH*_SIGNER/_HANDLER}) CHỈ dùng cho use case này, không
 *           phải tên view Domino thật, không có FE nào gọi trực tiếp.</li>
 *     </ul>
 *   </li>
 *   <li>{@code HOSOCV} (7/7 — xem {@link #buildHoSoCvPersonal}): đầy đủ từ đợt port đầu.</li>
 *   <li>{@code PHIEUTRINHTD} (7/7 — xem {@link #buildPhieuTrinhTdPersonal}): 06/10/2026 thêm
 *       {@code phieutrinh_chuaphoihop} (= view {@code vwUser_Coordinator_chua}, cutover FE phiếu trình).</li>
 *   <li>{@code VBDI} (4/4 — xem {@link #buildVbdiPersonal}): thêm {@code dakybanhanh}/
 *       {@code dakychuabanhanh} (dxl 2814-2884 — biến thể "Signer", khoá SCALAR field
 *       {@code user_signer}, KHÁC hẳn 2 view "Creator" đã port trước đó dùng khoá tổ chức
 *       {@code unit_publish_identifier}/{@code unit_creator_identifier} qua công thức @If-fallback —
 *       xem {@code VIEW_VBDI_WAITING_TO_SEND}/{@code VIEW_VBDI_SENT}, KHÔNG tái dùng nhầm). Tái dùng
 *       {@code queryVbdiByFieldsEqualsUnvalidated} sẵn có (đã dùng cho {@code VIEW_VBDI_GROUP_BY_SIGNER}),
 *       chỉ thêm điều kiện phụ {@code send_agent}.</li>
 * </ul>
 *
 * <p><b>Cập nhật 09/09/2026 (3) — port số liệu CẤP CƠ QUAN</b> (Domino {@code handleUserRoles()}):
 * chỉ tính THÊM khi user đang đăng nhập có ÍT NHẤT 1 trong 5 role QLVB
 * {@code QCapNhatVBDen}/{@code QCapNhatVBDi}/{@code QPhanCong}/{@code QPhanPhoi}/{@code QKTTTTH}
 * (xem {@link #ORG_LEVEL_ELIGIBLE_ROLES} — giá trị y hệt {@code QlvbRoles} ở {@code td-infrastructure},
 * lặp lại tại đây vì {@code td-application} không được phụ thuộc ngược) VÀ
 * {@link ResolveIdentifierByLevelUseCase#resolveForCurrentUser()} trả về identifier khác rỗng — xem
 * {@link #buildCoQuan}:
 * <ul>
 *   <li>{@code VBDEN_COQUAN} (11/11 — xem {@link #buildVbdenCoQuan}): CẢ 11 view (captrengiao/
 *       dennoibo/quamangnoibo/lienthong/dendientu/phaixuly_cuatochuc/quahan_cuatochuc/
 *       daxuly_quahan_cuatochuc/daxuly_dunghan_cuatochuc/saptoihan_cuatochuc/denhan_cuatochuc) ĐÃ CÓ
 *       SẴN trong {@link ViewQueryService} (N03/N13, dùng chung {@code queryVbdenOrgView}) — các view
 *       này BỎ QUA hoàn toàn {@code request.getKeys()}, TỰ resolve identifier của user đang đăng
 *       nhập qua {@link ResolveIdentifierByLevelUseCase} phía server (xem
 *       {@code ViewQueryService#resolveCurrentIdentifier}) nên gọi y hệt các view cá nhân (key nào
 *       cũng bị bỏ qua) là port ĐÚNG, không cần đổi cách gọi {@link #countByView}.</li>
 *   <li>{@code homthucong} (1 field, {@code VBDEN_HOMTHUCONG_COQUAN}) — KHÔNG port: Domino gốc đọc
 *       database KHÁC hẳn ({@code thudientu}, {@code SoLuongCanXuLy.java} dòng 386-401 —
 *       {@code VBdenHomThuCong}), không phải {@code vbden}/{@code view_vanbanden.dxl} — module
 *       "thư điện tử" này KHÔNG có DXL/bảng Java tương ứng, không có gì để đối chiếu.</li>
 *   <li>{@code PHIEUTRINH_COQUAN} (1 field, {@code pt_chocapso}) — KHÔNG port: module {@code phieutrinh}
 *       (không "TD") không tồn tại phía Java (giống lý do đã ghi ở nhóm cá nhân).</li>
 *   <li>{@code VBDI_COQUAN} (6/6 — xem {@link #buildVbdiCoQuan}): 4/6 view ĐÃ CÓ SẴN, khớp CHÍNH XÁC
 *       theo dxl ({@code vwCreator_Drafting} tự resolve identifier — {@code VIEW_DUTHAO_CREATOR_DRAFTING};
 *       {@code vwCreator_Drafting_mc}/{@code vwCreator_DocumentOut_WaitingToSend}/
 *       {@code vwCreator_DocumentOut_SendNoResponse} nhận identifier + validate
 *       {@code isOwnIdentifier}). <b>Phát hiện quan trọng</b>: {@code send_reject}/{@code send_returned}
 *       KHÔNG thể tái dùng {@code VIEW_VBDI_REPLY_REFUSE}/{@code VIEW_VBDI_REPLY_DRAFTOR} đã port
 *       trước đó dù tên rất giống — đối chiếu {@code docs/views_domino/view_vanbandi.dxl} xác nhận
 *       {@code vwCreator_DocumentOut_Refuse}/{@code vwCreator_DocumentOut_Reply} là 2 view THẬT KHÁC
 *       (công thức selection khác hẳn) — đã thêm 2 view case mới
 *       {@code VIEW_VBDI_CREATOR_DOCUMENTOUT_REFUSE}/{@code _REPLY} port ĐÚNG theo dxl, không tái
 *       dùng nhầm.</li>
 *   <li>{@code HOSOLUUTRU_COQUAN} (7/7 — xem {@link #buildHoSoLuuTruCoQuan}): CẢ 7 field trong
 *       {@code Maps.tsx} (3 field xác nhận DXL {@code MapsSoLuongCanXuLy.java} + 4 field
 *       "phieuyeucau*" khác — DXL gốc {@code view_hosoluutru.dxl} chỉ export {@code SELECT @All}
 *       (không có formula thật), 7 view case Java hiện có đều SUY LUẬN từ tên view + field lọc code
 *       Java thật, KHÔNG phải copy nguyên văn DXL — chấp nhận vì đã là hạ tầng ĐANG CHẠY, không phải
 *       suy đoán mới của lượt port này) đều nhận identifier trực tiếp làm khoá, KHÔNG có
 *       {@code isOwnIdentifier} validate (khác vbden/vbdi — hạn chế bảo mật đã có từ trước, không
 *       phải lượt port này gây ra).</li>
 * </ul>
 *
 * <p><b>Điểm CHƯA xác nhận được (ghi nhận, không chặn port)</b>: Domino gốc gán
 * {@code identifier = connection.user_token.identifier} (field phẳng lưu sẵn trên hồ sơ user tại
 * lúc đăng nhập), còn {@link ResolveIdentifierByLevelUseCase#resolveForCurrentUser()} tính LẠI theo
 * {@code level} của user tại thời điểm gọi — 2 cơ chế có thể KHÔNG trả cùng giá trị cho mọi user có
 * 1 trong 5 role trên (nhóm role "cấp cơ quan" có thể khác nhóm user thông thường mà cơ chế
 * {@code ResolveIdentifierByLevelUseCase} đã được xác nhận đúng, xem N03/N13). Chấp nhận rủi ro này
 * vì ĐÂY LÀ CƠ CHẾ DUY NHẤT hiện có ở Java để tính identifier "cấp cơ quan" của user hiện tại (đã
 * dùng cho toàn bộ 11 view {@code VBDEN_COQUAN} từ trước) — nhất quán với những gì các view liệt kê
 * ở trên ĐANG trả về cho màn hình liệt kê thật (đếm sai thì màn liệt kê cũng sai theo, không phải
 * rủi ro RIÊNG của tính năng badge này).</p>
 *
 * <p><b>Vẫn CHƯA port</b>: số liệu "nhiệm vụ chỉ đạo điều hành" theo cơ quan/đơn vị/phòng ban/cá
 * nhân/phân công ({@code VBCDDH_*}, chỉ tính khi cấu hình {@code menu_vbcddh} của user bật) — chưa
 * xác nhận cách Java biểu diễn khái niệm {@code menu_vbcddh}, tránh trả sai dữ liệu cho user không
 * bật cấu hình đó.</p>
 *
 * <p><b>KHÔNG dùng cache Redis TTL 10 phút như Domino gốc</b> — cùng lý do đã ghi ở
 * {@code GetKiemNhiemPendingCountUseCase} (quy mô nhỏ, JPA tự COUNT nhanh, chưa có bằng chứng cần
 * cache ngay ở lượt port đầu tiên). Hệ quả: tham số {@code action} ({@code reset}/{@code update})
 * của Domino gốc KHÔNG còn ý nghĩa (không có cache để xoá/vá) — mọi lượt gọi đều tính lại đầy đủ,
 * controller không cần nhận/xử lý {@code action}.</p>
 *
 * <p><b>Hình dạng response KHÁC Domino gốc</b> (đã thống nhất từ khi port
 * {@code SoLuongCanXuLyKiemNhiem} — xem comment {@code Notifications.tsx#getNotificationsCountOther}):
 * Domino trả {@code data} là MẢNG 1-3 object phẳng; ở đây trả THẲNG 1 {@link Map} phẳng duy nhất
 * (FE tự gộp mảng thành 1 object phẳng nên không mất thông tin gì khi bỏ cấu trúc mảng) — cutover FE
 * tại {@code getNotificationsCount()}/{@code resetCount()} (Notifications.tsx).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetSoLuongCanXuLyUseCase {

    private static final String DB_VBDEN = "vbden";
    private static final String DB_DUTHAO = "duthao";
    private static final String DB_HOSOCV = "hosocv";
    private static final String DB_PHIEUTRINHTD = "phieutrinhtd";
    private static final String DB_VBDI = "vbdi";
    private static final String DB_HOSOLUUTRU = "hosoluutru";

    // Gia tri Y HET QlvbRoles (td-infrastructure) - lap lai vi td-application khong duoc phu thuoc
    // nguoc vao td-infrastructure (xem javadoc CurrentUserProvider#currentRoles).
    private static final Set<String> ORG_LEVEL_ELIGIBLE_ROLES = Set.of(
        "QCAPNHATVBDEN", "QCAPNHATVBDI", "QPHANCONG", "QPHANPHOI", "QKTTTTH");

    private final ViewQueryService viewQueryService;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;

    public Map<String, Object> execute() {
        Map<String, Object> counts = new LinkedHashMap<>();
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return counts;
        }

        buildVbdenPersonal(counts, username);
        buildDuThaoPersonal(counts, username);
        buildHoSoCvPersonal(counts, username);
        buildPhieuTrinhTdPersonal(counts, username);
        buildVbdiPersonal(counts, username);
        buildCoQuan(counts);
        return counts;
    }

    // Domino handleUserRoles(): chi tinh so lieu cap co quan neu user co >=1 trong 5 role tren VA co
    // identifier - xem javadoc lop.
    private void buildCoQuan(Map<String, Object> counts) {
        if (currentUserProvider.currentRoles().stream().noneMatch(ORG_LEVEL_ELIGIBLE_ROLES::contains)) {
            return;
        }
        String identifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        buildVbdenCoQuan(counts, identifier);
        buildVbdiCoQuan(counts, identifier);
        buildHoSoLuuTruCoQuan(counts, identifier);
    }

    // MapsSoLuongCanXuLy.VBDEN_COQUAN (11/11 - day du, xem javadoc lop). CAC VIEW NAY TU RESOLVE
    // identifier phia server (queryVbdenOrgView), khong dung "identifier" truyen vao lam key - van
    // goi qua countByView (identifier chi la tham so hinh thuc, bi bo qua) de dung chung 1 helper.
    private void buildVbdenCoQuan(Map<String, Object> counts, String identifier) {
        counts.put("captrengiao", countByView(DB_VBDEN, "captrengiao", identifier));
        counts.put("lienthong", countByView(DB_VBDEN, "lienthong", identifier));
        counts.put("dendientu", countByView(DB_VBDEN, "dendientu", identifier));
        counts.put("quamangnoibo", countByView(DB_VBDEN, "quamangnoibo", identifier));
        counts.put("dennoibo", countByView(DB_VBDEN, "dennoibo", identifier));
        counts.put("phaixuly", countByView(DB_VBDEN, "phaixuly_cuatochuc", identifier));
        counts.put("quahan", countByView(DB_VBDEN, "quahan_cuatochuc", identifier));
        counts.put("daxulyquahan", countByView(DB_VBDEN, "daxuly_quahan_cuatochuc", identifier));
        counts.put("daxulydunghan", countByView(DB_VBDEN, "daxuly_dunghan_cuatochuc", identifier));
        counts.put("saptoihan", countByView(DB_VBDEN, "saptoihan_cuatochuc", identifier));
        counts.put("denhan", countByView(DB_VBDEN, "denhan_cuatochuc", identifier));
    }

    // MapsSoLuongCanXuLy.VBDI_COQUAN (6/6 - day du, xem javadoc lop). "vwcreator_drafting" tu resolve
    // identifier phia server (giong nhom vbden tren); 3 view con lai + 2 view moi (Refuse/Reply)
    // nhan "identifier" lam key va validate isOwnIdentifier - dung DUNG identifier cua chinh user
    // (tu resolveIdentifierByLevelUseCase) nen luon qua duoc validate.
    private void buildVbdiCoQuan(Map<String, Object> counts, String identifier) {
        counts.put("send_waiting_for_signature", countByView(DB_VBDI, "vwcreator_drafting", identifier));
        counts.put("send_waiting_for_signature_mc", countByView(DB_VBDI, "vwcreator_drafting_mc", identifier));
        counts.put("WaitingToSend", countByView(DB_VBDI, "vwcreator_documentout_waitingtosend", identifier));
        counts.put("SendNoResponse", countByView(DB_VBDI, "vwcreator_documentout_sendnoresponse", identifier));
        counts.put("send_reject", countByView(DB_VBDI, "vwcreator_documentout_refuse", identifier));
        counts.put("send_returned", countByView(DB_VBDI, "vwcreator_documentout_reply", identifier));
    }

    // MapsSoLuongCanXuLy.HOSOLUUTRU_COQUAN (7/7, gom 4 field bo sung tu Maps.tsx - xem javadoc lop).
    private void buildHoSoLuuTruCoQuan(Map<String, Object> counts, String identifier) {
        counts.put("hosoluutru_chotiepnhan", countByView(DB_HOSOLUUTRU, "hoso_chuaduyet_by_identifier", identifier));
        counts.put("hosoluutru_chochuyenluutru",
            countByView(DB_HOSOLUUTRU, "hoso_chochuyenluutru_by_identifier", identifier));
        counts.put("hosoluutru_dachuyenluutru",
            countByView(DB_HOSOLUUTRU, "hoso_dachuyenluutru_by_identifier", identifier));
        counts.put("hosoluutru_phieuyeucau0", countByView(DB_HOSOLUUTRU, "phieuyeucau0_by_identifier", identifier));
        counts.put("hosoluutru_phieuyeucau13", countByView(DB_HOSOLUUTRU, "phieuyeucau13_by_identifier", identifier));
        counts.put("hosoluutru_phieuyeucau_dangmuon",
            countByView(DB_HOSOLUUTRU, "phieuyeucau_dangmuon_by_identifier", identifier));
        counts.put("hosoluutru_phieuyeucau_quahan",
            countByView(DB_HOSOLUUTRU, "phieuyeucau_quahan_by_identifier", identifier));
    }

    // MapsSoLuongCanXuLy.VBDEN (14/14 - day du, xem javadoc lop).
    private void buildVbdenPersonal(Map<String, Object> counts, String username) {
        counts.put("user_distributor", countByView(DB_VBDEN, "chophanphoi", username));
        counts.put("user_process", countByView(DB_VBDEN, "chutri", username));
        counts.put("dennoibo_choxuly", countByView(DB_VBDEN, "dennoibo_choxuly", username));
        counts.put("user_process_over_due", countByView(DB_VBDEN, "chutriquahan", username));
        counts.put("user_coordinate", countByView(DB_VBDEN, "phoihop", username));
        counts.put("user_to_read", countByView(DB_VBDEN, "xemdebiet", username));
        counts.put("user_track", countByView(DB_VBDEN, "theodoi", username));
        counts.put("waiting_opinion", countByView(DB_VBDEN, "chochoykien", username));
        counts.put("waiting_opinion_noi_bo", countByView(DB_VBDEN, "chochoykien_noi_bo", username));
        counts.put("waiting_opinion_ttdu", countByView(DB_VBDEN, "chochoykienttdu", username));
        counts.put("waiting_opinion_btv", countByView(DB_VBDEN, "chochoykienbtv", username));
        counts.put("waiting_opinion_bch", countByView(DB_VBDEN, "chochoykienbch", username));
        counts.put("vbden_sapdenhan_cuacanhan", countByView(DB_VBDEN, "sapdenhan_cuacanhan", username));
        counts.put("vanbantralai", countByView(DB_VBDEN, "vanbantralai", username));
        counts.put("user_manage", countByView(DB_VBDEN, "phutrach_chuaxem", username));
    }

    // MapsSoLuongCanXuLy.DUTHAO (12/12 - day du, xem javadoc lop).
    private void buildDuThaoPersonal(Map<String, Object> counts, String username) {
        counts.put("drafting_creator_process_handler_submit",
            countByView(DB_DUTHAO, "vwcreator_process_handler_submit", username));
        // Bugfix 09/09/2026 (2): view DUNG la "vwdocumentout_submited" (KHONG phai
        // "vwcreator_documentout_submited") - xem javadoc lop.
        counts.put("drafting_creator_documentout_submited",
            countByView(DB_DUTHAO, "vwdocumentout_submited", username));
        counts.put("draft_creator_documentout_return",
            countByView(DB_DUTHAO, "vwcreator_documentout_return", username));
        counts.put("drafting_coordinator", countByView(DB_DUTHAO, "vwuser_coordinator", username));
        counts.put("drafting_user_signer_waiting", countByView(DB_DUTHAO, "vwsigner_submit", username));
        counts.put("drafting_user_process_handler_submit",
            countByView(DB_DUTHAO, "vwuser_process_handler_submit", username));
        counts.put("drafting_user_return_submiting", countByView(DB_DUTHAO, "vwuser_return_draft", username));
        counts.put("draft_signer_documentout_return",
            countByView(DB_DUTHAO, "vwsigner_documentout_return", username));
        // 01/10/2026: route gop "Van thu tra lai" (ca 3 nhanh lanh dao/chuyen vien/nguoi trinh) -
        // Java-only, xem ViewQueryService#VIEW_DUTHAO_VANTHU_TRALAI.
        counts.put("draft_vanthu_tralai", countByView(DB_DUTHAO, "vwvanthu_tralai", username));
        counts.put("drafting_user_other_process_handler",
            countByView(DB_DUTHAO, "vwuser_other_process_handler_submit", username));
        counts.put("drafting_user_processed_handler_submit",
            countByView(DB_DUTHAO, "vwusercreator_process_handler_submit", username));
        // "vwChoDuyetKyBanHanh"/"vwChoDuyetKyBanHanhMC" - JSON key TRUNG ten view (dung nguyen ban,
        // khong dich nghia - xem javadoc lop). OR-group 2 nhanh loai tru lan nhau tren status -> TONG.
        counts.put("vwChoDuyetKyBanHanh",
            countByView(DB_DUTHAO, "vwchoduyetkybanhanh_signer_internal", username)
                + countByView(DB_DUTHAO, "vwchoduyetkybanhanh_handler_internal", username));
        counts.put("vwChoDuyetKyBanHanhMC",
            countByView(DB_DUTHAO, "vwchoduyetkybanhanhmc_signer_internal", username)
                + countByView(DB_DUTHAO, "vwchoduyetkybanhanhmc_handler_internal", username));
    }

    // MapsSoLuongCanXuLy.HOSOCV (7/7 - day du).
    private void buildHoSoCvPersonal(Map<String, Object> counts, String username) {
        counts.put("hoso_chutri", countByView(DB_HOSOCV, "hoso_chutri_dangxuly", username));
        counts.put("hoso_phutrach", countByView(DB_HOSOCV, "vwuser_leader_in_charge", username));
        counts.put("hoso_phoihop", countByView(DB_HOSOCV, "vwuser_coordinated_status", username));
        counts.put("hoso_choduyet", countByView(DB_HOSOCV, "vwuser_approved_by_trinh_2", username));
        counts.put("hoso_daduocduyet", countByView(DB_HOSOCV, "vwuser_process_handler_trinh_1", username));
        counts.put("hoso_khongduocduyet", countByView(DB_HOSOCV, "vwuser_process_handler_trinh_3", username));
        counts.put("hoso_khongduocluutru", countByView(DB_HOSOCV, "vwuser_process_handler_storaged_3", username));
    }

    // MapsSoLuongCanXuLy.PHIEUTRINHTD (6/7 - anh xa lai theo y nghia, xem javadoc lop).
    private void buildPhieuTrinhTdPersonal(Map<String, Object> counts, String username) {
        counts.put("phieutrinh_khongthongqua", countByView(DB_PHIEUTRINHTD, "phieutrinhtd_khongthongqua", username));
        counts.put("phieutrinh_khongduyet", countByView(DB_PHIEUTRINHTD, "phieutrinhtd_khongduyet", username));
        counts.put("phieutrinh_dathongqua", countByView(DB_PHIEUTRINHTD, "phieutrinhtd_dathongqua", username));
        counts.put("phieutrinh_daduyet", countByView(DB_PHIEUTRINHTD, "phieutrinhtd_daduyet", username));
        counts.put("phieutrinh_chothongqua", countByView(DB_PHIEUTRINHTD, "phieutrinhtd_chothongqua", username));
        counts.put("phieutrinh_choduyet", countByView(DB_PHIEUTRINHTD, "phieutrinhtd_choduyet", username));
        // 06/10/2026 (cutover FE phieu trinh): "Cho cho y kien" (nguoi phoi hop chua gop y) - view Domino
        // vwUser_Coordinator_chua da co ben Java (PhieuTrinhTdViewQuery).
        counts.put("phieutrinh_chuaphoihop", countByView(DB_PHIEUTRINHTD, "vwuser_coordinator_chua", username));
    }

    // MapsSoLuongCanXuLy.VBDI (4/4 - day du, xem javadoc lop).
    private void buildVbdiPersonal(Map<String, Object> counts, String username) {
        counts.put("send_important", countByView(DB_VBDI, "vwprivateimportant", username));
        counts.put("vbdi_nhandebiet", countByView(DB_VBDI, "vwgroup_reader_by_nhandebiet", username));
        counts.put("dakybanhanh", countByView(DB_VBDI, "vwsigner_documentout_sent", username));
        counts.put("dakychuabanhanh", countByView(DB_VBDI, "vwsigner_documentout_waitingtosend", username));
    }

    // Bugfix 09/09/2026 (báo lỗi người dùng - ERR-8de8584a: 1 view lỗi runtime, cụ thể
    // "phieuyeucau_quahan_by_identifier" ném UnsupportedOperationException do
    // PostgresHoSoLuuTruPhieuYeuCauDocumentRepository chưa xử lý Pageable.unpaged - xem bugfix cùng
    // ngày ở đó, đã sửa gốc) làm SẬP TOÀN BỘ response, mất luôn số liệu cá nhân đã tính đúng ở các
    // field khác - bắt lỗi PER-FIELD (giống nguyên tắc "1 tệp lỗi không làm hỏng tệp khác" đã áp dụng
    // ở PrepareVbdiDeliveryFilesUseCaseImpl): field lỗi trả 0 + log cảnh báo, KHÔNG chặn các field còn
    // lại - badge thiếu 1 số vẫn hữu ích hơn báo lỗi toàn bộ.
    private long countByView(String dbType, String viewname, String key) {
        try {
            var request = new ViewQueryRequest();
            request.setViewname(viewname);
            request.setKeys(List.of(key));
            request.setPageNumber(1);
            request.setPageSize(1);
            return viewQueryService.execute(dbType, request).getTotalItems();
        } catch (Exception ex) {
            log.warn("Khong the dem view '{}' (dbType={}) cho SoLuongCanXuLy - tra 0, khong chan cac "
                    + "field con lai: {}",
                viewname, dbType, ex.getMessage());
            return 0L;
        }
    }
}
