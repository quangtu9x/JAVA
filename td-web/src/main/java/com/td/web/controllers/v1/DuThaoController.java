package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.PaginationResponse;
import com.td.application.duthao.AddDuThaoEditHistoryRequest;
import com.td.application.duthao.AddDuThaoSeenDocumentRequest;
import com.td.application.duthao.AddDuThaoSeenDocumentUseCase;
import com.td.application.duthao.AddDuThaoEditHistoryUseCase;
import com.td.application.duthao.ListDuThaoEditHistoryRequest;
import com.td.application.duthao.ListDuThaoEditHistoryUseCase;
import com.td.application.duthao.ChuyenThanhVanBanDiRequest;
import com.td.application.duthao.ChuyenThanhVanBanDiUseCase;
import com.td.application.duthao.DuThaoDaBanHanhRequest;
import com.td.application.duthao.DuThaoDaBanHanhUseCase;
import com.td.application.duthao.DuThaoVanThuKhongBanHanhRequest;
import com.td.application.duthao.DuThaoVanThuKhongBanHanhUseCase;
import com.td.application.duthao.TraLaiDuThaoRequest;
import com.td.application.duthao.TraLaiDuThaoChoLanhDaoUseCase;
import com.td.application.duthao.TraLaiDuThaoChoNguoiSoanThaoUseCase;
import com.td.application.duthao.TraLaiDuThaoChoNguoiTrinhUseCase;
import com.td.application.duthao.TraLoiVanBanDenDuThaoRequest;
import com.td.application.duthao.TraLoiVanBanDenDuThaoUseCase;
import com.td.application.duthao.XoaTraLoiVanBanDenDuThaoRequest;
import com.td.application.duthao.XoaTraLoiVanBanDenDuThaoUseCase;
import com.td.application.duthao.GanKemTepDuThaoRequest;
import com.td.application.duthao.GanKemTepDuThaoUseCase;
import com.td.application.duthao.GanVanBanLienQuanDuThaoRequest;
import com.td.application.duthao.GanVanBanLienQuanDuThaoUseCase;
import com.td.application.duthao.XoaVanBanLienQuanDuThaoRequest;
import com.td.application.duthao.XoaVanBanLienQuanDuThaoUseCase;
import com.td.application.duthao.GetDuThaoDetailRequest;
import com.td.application.duthao.GetDuThaoDetailUseCase;
import com.td.application.duthao.LuuDuThaoRequest;
import com.td.application.duthao.LuuDuThaoUseCase;
import com.td.application.duthao.TaoDuThaoRequest;
import com.td.application.duthao.TaoDuThaoUseCase;
import com.td.application.duthao.ThuHoiVanBanDiRequest;
import com.td.application.duthao.ThuHoiVanBanDiUseCase;
import com.td.application.duthao.XinYKienPhoiHopRequest;
import com.td.application.duthao.XinYKienPhoiHopUseCase;
import com.td.application.duthao.XuLyDuThaoRequest;
import com.td.application.duthao.XuLyDuThaoUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core): Tạo dự thảo / Xem chi tiết / Hiệu chỉnh (Slice 1, tài liệu phân tích 04
 * mục 13) / Xin ý kiến phối hợp / Chuyển thành văn bản đi / Thu hồi văn bản đi chờ ban hành. Cùng
 * kiến trúc BL-210/BL-220: tái dùng bảng {@code documents} chung ({@code document_type =
 * "duthao"}), KHÔNG tạo entity/bảng riêng — mọi field nghiệp vụ biến thiên nằm trong cột JSONB
 * {@code dynamic_fields} (BL-101 Dynamic Data Engine), giống {@code vbden}/{@code vbdi}.
 *
 * <p>Quyền: KHÔNG tìm thấy bất kỳ role-string/permission check nào trong toàn bộ 5 file Domino
 * thật đã đọc ({@code InitDraft.java}, {@code DraftObject.java}, {@code DraftProcesses.java},
 * {@code ChoYKienPhoiHop.java}, {@code DraftToDocumentOut.java}) — nên toàn bộ endpoint chỉ yêu
 * cầu đã đăng nhập, đúng nguyên tắc SEC-01 "không suy diễn quyền khi không có bằng chứng".
 */
@RestController
@RequestMapping("/api/v1/duthao")
@RequiredArgsConstructor
@Validated
@Tag(name = "DuThao", description = "Dự thảo — Core (BL-230)")
public class DuThaoController extends BaseController {

    private static final String DB_TYPE = "duthao";

    private final TaoDuThaoUseCase taoDuThaoUseCase;
    private final GetDuThaoDetailUseCase getDuThaoDetailUseCase;
    private final LuuDuThaoUseCase luuDuThaoUseCase;
    private final XinYKienPhoiHopUseCase xinYKienPhoiHopUseCase;
    private final ChuyenThanhVanBanDiUseCase chuyenThanhVanBanDiUseCase;
    private final ThuHoiVanBanDiUseCase thuHoiVanBanDiUseCase;
    private final DuThaoDaBanHanhUseCase duThaoDaBanHanhUseCase;
    private final DuThaoVanThuKhongBanHanhUseCase duThaoVanThuKhongBanHanhUseCase;
    private final TraLaiDuThaoChoLanhDaoUseCase traLaiDuThaoChoLanhDaoUseCase;
    private final TraLaiDuThaoChoNguoiSoanThaoUseCase traLaiDuThaoChoNguoiSoanThaoUseCase;
    private final TraLaiDuThaoChoNguoiTrinhUseCase traLaiDuThaoChoNguoiTrinhUseCase;
    private final XuLyDuThaoUseCase xuLyDuThaoUseCase;
    private final GanKemTepDuThaoUseCase ganKemTepDuThaoUseCase;
    private final TraLoiVanBanDenDuThaoUseCase traLoiVanBanDenDuThaoUseCase;
    private final XoaTraLoiVanBanDenDuThaoUseCase xoaTraLoiVanBanDenDuThaoUseCase;
    private final GanVanBanLienQuanDuThaoUseCase ganVanBanLienQuanDuThaoUseCase;
    private final XoaVanBanLienQuanDuThaoUseCase xoaVanBanLienQuanDuThaoUseCase;
    private final ViewQueryService viewQueryService;
    private final AddDuThaoEditHistoryUseCase addDuThaoEditHistoryUseCase;
    private final ListDuThaoEditHistoryUseCase listDuThaoEditHistoryUseCase;
    private final AddDuThaoSeenDocumentUseCase addDuThaoSeenDocumentUseCase;

    @PostMapping("/TaoDuThao")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo dự thảo từ văn bản đến/đi (BL-230 DoD 1)",
        description = "form-data field động (subject, unit_in_charge, ...) + meta "
            + "date_fields/number_fields/richtext_fields (BL-101). Trạng thái khởi tạo DRAFTING. "
            + "Lưu ý 26/08/2026: user_creator do SERVER tự gán theo tài khoản đang đăng nhập "
            + "(SERVER-AUTHORITATIVE) — giá trị client gửi lên bị bỏ qua")
    public ResponseEntity<ApiResponse<UUID>> taoDuThao(@RequestParam Map<String, String> params) {
        return created(taoDuThaoUseCase.execute(buildTaoDuThaoRequest(params)));
    }

    private TaoDuThaoRequest buildTaoDuThaoRequest(Map<String, String> params) {
        var request = new TaoDuThaoRequest();
        request.setSubject(firstNonBlank(params.get("subject"), params.get("title")));
        request.setStatus(params.get("status"));
        request.setDateFields(params.get("date_fields"));
        request.setNumberFields(params.get("number_fields"));
        request.setRichtextFields(params.get("richtext_fields"));

        // Bugfix 24/08/2026 (đồng bộ VbdenController): "document_type" là field nghiệp vụ thật của
        // form dự thảo ("Loại văn bản" — Công văn/Báo cáo/...), khác hoàn toàn documentType/
        // DOCUMENT_TYPE ở tầng entity (hardcode "duthao" trong TaoDuThaoUseCase, không đọc từ
        // params) — không được strip khỏi fields động nữa.
        Map<String, String> fields = new java.util.LinkedHashMap<>(params);
        for (String metaKey : List.of(
                "subject", "title", "status", "date_fields", "number_fields", "richtext_fields")) {
            fields.remove(metaKey);
        }
        request.setFields(fields);
        return request;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết dự thảo (Slice 1, tương thích qlvb/get?databasetype=duthao)",
        description = "items=\"getall\" trả về tất cả field (tĩnh + động), hoặc "
            + "items=\"field1;field2\" lọc theo danh sách field — dùng bởi ChiTietPage.jsx#fetchData")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(
            @RequestBody GetDuThaoDetailRequest request) {
        return ok(getDuThaoDetailUseCase.execute(request));
    }

    @PostMapping("/Luu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hiệu chỉnh dự thảo ĐÃ TỒN TẠI (Slice 1, tương thích 1 phần "
        + "qlvb/add?databasetype=duthao&id=... khi id có giá trị)",
        description = "Chỉ port: merge field vào dự thảo gốc (không replace toàn bộ) + đồng bộ lại "
            + "title nếu request gửi subject. CHƯA port: tạo/cập nhật quá trình dự thảo, tính lại "
            + "status, đồng bộ vbden/vbdi/hosocv liên kết — xem javadoc LuuDuThaoUseCase")
    public ResponseEntity<ApiResponse<UUID>> luu(@Valid @RequestBody LuuDuThaoRequest request) {
        return ok(luuDuThaoUseCase.execute(request));
    }

    @PostMapping("/XinYKienPhoiHop")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xin ý kiến phối hợp (BL-230 DoD 2)",
        description = "Đúng cơ chế thật ChoYKienPhoiHop.java: người dùng hiện tại trong danh sách "
            + "chờ (user_coordinator) phản hồi sẽ được chuyển sang danh sách đã cho ý kiến "
            + "(user_coordinated). nguoiPhoiHop (tuỳ chọn) thêm người vào danh sách chờ")
    public ResponseEntity<ApiResponse<UUID>> xinYKienPhoiHop(@Valid @RequestBody XinYKienPhoiHopRequest request) {
        return created(xinYKienPhoiHopUseCase.execute(request));
    }

    @PostMapping("/ChuyenThanhVanBanDi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển dự thảo thành văn bản đi (BL-230 DoD 3)",
        description = "Đúng cơ chế thật DraftToDocumentOut.CopyDraftToDocumentOut(): tạo document "
            + "vbdi mới (trạng thái DRAFT, đúng BL-220), copy field động từ dự thảo, liên kết 2 "
            + "chiều (draft_id/document_out_id). Idempotent — gọi lại nhiều lần chỉ tạo 1 văn bản "
            + "đi duy nhất")
    public ResponseEntity<ApiResponse<UUID>> chuyenThanhVanBanDi(@Valid @RequestBody ChuyenThanhVanBanDiRequest request) {
        return created(chuyenThanhVanBanDiUseCase.execute(request));
    }

    @PostMapping("/ThuHoiVanBanDi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thu hồi văn bản đi chờ ban hành (BL-230 DoD 4)",
        description = "Đúng cơ chế thật DraftToDocumentOut.ReturnDocumentOutToDraft(): xoá mềm văn "
            + "bản đi liên kết (tái dùng DeleteDocumentUseCase), ghi lịch sử return_document_out_id, "
            + "mở lại dự thảo (DRAFTING). Có guard CHỦ ĐỘNG (khác Domino thật, xem Javadoc use case): "
            + "chỉ cho phép khi văn bản đi CHƯA ban hành (ISSUED), đúng tên gọi DoD 'chờ ban hành'")
    public ResponseEntity<ApiResponse<UUID>> thuHoiVanBanDi(@Valid @RequestBody ThuHoiVanBanDiRequest request) {
        return ok(thuHoiVanBanDiUseCase.execute(request));
    }

    // Yeu cau nguoi dung 28/08/2026 ("port lai XuLyDuThao.java, class nay co cac chuc nang tuong
    // ung o button cua van ban di") - doi chieu truc tiep vn.com.tandan.xsp.services.vbdi.
    // XuLyDuThao#ChuyenTrangThaiDuThaoDaBanHanh() - xem javadoc DuThaoDaBanHanhUseCase. Tuong duong
    // qlvb/vbdi/dabanhanhduthao?databasetype=duthao&id=...&vbdiid=...&vbdicode=... (Domino).
    @PostMapping("/DaBanHanh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu dự thảo đã ban hành (văn bản đi sinh ra từ nó đã có số/ký hiệu)",
        description = "Set documentout_id/documentout_code/status_pre (field động) + status="
            + "DOCUMENT_OUT_FINALIZED (\"8\") trên dự thảo cha")
    public ResponseEntity<ApiResponse<UUID>> daBanHanh(@Valid @RequestBody DuThaoDaBanHanhRequest request) {
        return ok(duThaoDaBanHanhUseCase.execute(request));
    }

    // Doi chieu XuLyDuThao#ChuyenTrangThaiDuThaoVanThuXoa() - xem javadoc DuThaoVanThuKhongBanHanhUseCase.
    @PostMapping("/VanThuKhongBanHanh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Văn thư không ban hành văn bản đi sinh ra từ dự thảo",
        description = "Set xoavanbandi (field động, chuỗi cố định kèm thời điểm) + status="
            + "DOCUMENT_OUT_FINALIZED (\"8\") trên dự thảo cha")
    public ResponseEntity<ApiResponse<UUID>> vanThuKhongBanHanh(
            @Valid @RequestBody DuThaoVanThuKhongBanHanhRequest request) {
        return ok(duThaoVanThuKhongBanHanhUseCase.execute(request));
    }

    // Doi chieu XuLyDuThao#TraLaiDuThaoChoLanhDaoKyDuyet()/TraLaiDuThaoChoNguoiSoanThao() - xem
    // javadoc TraLaiDuThaoSupport (logic dung chung cho ca 2 dich).
    @PostMapping("/TraLaiChoLanhDao")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trả lại dự thảo cho lãnh đạo ký duyệt",
        description = "Cập nhật dự thảo (return_document_out_for=1, status=PENDING_SIGNATURE) + "
            + "ghi vết draft_process (action_type=vttralanhdao) + đánh dấu văn bản đi bị văn thư "
            + "trả lại (status=RETURNED_BY_CLERK, log_giveback)")
    public ResponseEntity<ApiResponse<UUID>> traLaiChoLanhDao(@Valid @RequestBody TraLaiDuThaoRequest request) {
        return ok(traLaiDuThaoChoLanhDaoUseCase.execute(request));
    }

    @PostMapping("/TraLaiChoNguoiSoanThao")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trả lại dự thảo cho người soạn thảo (chuyên viên) sửa lại",
        description = "Cập nhật dự thảo (return_document_out_for=2, status=RETURNED) + ghi vết "
            + "draft_process (action_type=vttranguoisoan) + đánh dấu văn bản đi bị văn thư trả lại "
            + "(status=RETURNED_BY_CLERK, log_giveback)")
    public ResponseEntity<ApiResponse<UUID>> traLaiChoNguoiSoanThao(@Valid @RequestBody TraLaiDuThaoRequest request) {
        return ok(traLaiDuThaoChoNguoiSoanThaoUseCase.execute(request));
    }

    @PostMapping("/TraLaiChoNguoiTrinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trả lại dự thảo cho người trình ký (01/10/2026)",
        description = "Người trình = người thực hiện vết chuyenkyduyet gần nhất. Cập nhật dự thảo "
            + "(return_document_out_for=3, status=RETURNED) + ghi vết draft_process "
            + "(action_type=vttranguoitrinh) + đánh dấu văn bản đi bị văn thư trả lại")
    public ResponseEntity<ApiResponse<UUID>> traLaiChoNguoiTrinh(@Valid @RequestBody TraLaiDuThaoRequest request) {
        return ok(traLaiDuThaoChoNguoiTrinhUseCase.execute(request));
    }

    @PostMapping("/XuLyDuThao")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xử lý chuyển trạng thái dự thảo (DT-001, tương thích qlvb/duthao/xulyduthao)",
        description = "Đúng cơ chế thật DraftProcesses.DraftProcessing(): chuyenduyet/chuyenkyduyet/"
            + "chuyenkynhay/kyduyet/kynhay/tralai/chonnguoitralai/thuhoi/chuyennguoikhac/chuyenphoihop/"
            + "ketthuc/danhgiaduthao. KHÔNG nhận status/oldstatus từ client — tự suy ra từ action, xem "
            + "javadoc XuLyDuThaoRequest/XuLyDuThaoUseCase. KHÔNG hỗ trợ chuyenvanthu/thuhoichuyenvanthu "
            + "(dùng ChuyenThanhVanBanDi/ThuHoiVanBanDi)")
    public ResponseEntity<ApiResponse<UUID>> xuLyDuThao(@Valid @RequestBody XuLyDuThaoRequest request) {
        return ok(xuLyDuThaoUseCase.execute(request));
    }

    @PostMapping("/GanKemTep")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đính kèm ý kiến/tệp khi Lưu dự thảo (DT-007, tương thích qlvb/add?"
        + "databasetype=duthao&parentid=...&action_type=gankemtep)",
        description = "Ghi 1 vết draft_process (action_type=gankemtep) mang ý kiến — creator do SERVER "
            + "tự gán theo tài khoản đang đăng nhập, KHÔNG nhận từ client. Trả về id vết vừa tạo — FE "
            + "gọi tiếp POST /api/v1/documents/{id}/files với id DỰ THẢO CHA (không phải id vết) kèm "
            + "processId = id vết này, để server đánh dấu tệp thuộc đúng lần đính kèm mà vẫn lưu "
            + "chung document_id với mọi tệp khác của dự thảo")
    public ResponseEntity<ApiResponse<UUID>> ganKemTep(@Valid @RequestBody GanKemTepDuThaoRequest request) {
        return created(ganKemTepDuThaoUseCase.execute(request));
    }

    // Yeu cau nguoi dung 16/09/2026 ("chi tiet du thao -> Tra loi cho van ban den -> Gan van ban den
    // -> Dong y dang goi API cu, port sang giong nhu van ban di da lam") - xem javadoc
    // TraLoiVanBanDenDuThaoUseCase. Tuong duong qlvb/add?id={draftId}&databasetype=duthao (luu
    // documentin_obj) + qlvb/vbden/CapNhatVanBanDen?action=ganduthaotraloi (cap nhat vbden cha +
    // tao vbden_res) - 2 loi goi Domino tuan tu cua DuThaoActionModal.jsx#btnActionTraLoiVanBanDen.
    @PostMapping("/TraLoiVanBanDen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trả lời cho văn bản đến (chi tiết dự thảo)",
        description = "Tương đương DuThaoActionModal.jsx#btnActionTraLoiVanBanDen — nối văn bản đến "
            + "đã chọn vào documentin_obj của dự thảo, đồng thời cập nhật user_process/user_processing "
            + "trên từng văn bản đến VÀ tạo 1 bản ghi vbden_res (action=ganduthaotraloi) cho mỗi văn "
            + "bản đến, đúng thuật toán đã port cho văn bản đi ở TraLoiVanBanDenUseCase")
    public ResponseEntity<ApiResponse<UUID>> traLoiVanBanDen(@Valid @RequestBody TraLoiVanBanDenDuThaoRequest request) {
        return ok(traLoiVanBanDenDuThaoUseCase.execute(request));
    }

    // Yeu cau nguoi dung 16/09/2026 ("nut xoa o danh sach chua port, dang goi api cu") - xem javadoc
    // XoaTraLoiVanBanDenDuThaoUseCase. Tuong duong TraLoiChoVanBanDen.jsx#handleDelete (nhanh
    // databasetype==="duthao", truoc day CHUA co Java tuong ung nen van roi ve qlvb/add cu).
    @PostMapping("/XoaTraLoiVanBanDen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa 1 văn bản đến khỏi danh sách 'Trả lời cho văn bản đến' (chi tiết dự thảo)",
        description = "Tương đương TraLoiChoVanBanDen.jsx#handleDelete — loại đúng 1 phần tử khỏi "
            + "documentin_obj theo documentId (server tự tìm, không tin nguyên mảng FE gửi lên). "
            + "KHÔNG hoàn tác side-effect đã tạo lúc 'Trả lời' (user_process/vbden_res) — đúng Domino gốc")
    public ResponseEntity<ApiResponse<UUID>> xoaTraLoiVanBanDen(@Valid @RequestBody XoaTraLoiVanBanDenDuThaoRequest request) {
        return ok(xoaTraLoiVanBanDenDuThaoUseCase.execute(request));
    }

    // Yeu cau nguoi dung 16/09/2026 ("Van ban kem theo -> tuong tu khi mo modal chon van ban va dong
    // y cung chua port") - xem javadoc GanVanBanLienQuanDuThaoUseCase. Dung chung cho ca 2 action
    // ganvanbanden/ganvanbandi cua DuThaoActionModal.jsx (cung field vanban_lienquan_obj, chi khac
    // database_type FE tu gan vao tung phan tu).
    @PostMapping("/GanVanBanLienQuan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gắn văn bản liên quan (Văn bản kèm theo — chi tiết dự thảo)",
        description = "Dùng chung cho DuThaoActionModal.jsx#btnActionGanVanBanDen/btnActionGanVanBanDi "
            + "— nối thêm (dedup theo id) các văn bản đến/đi được chọn vào field động "
            + "vanban_lienquan_obj trên chính dự thảo. KHÔNG có side-effect nào lên văn bản được tham "
            + "chiếu (khác TraLoiVanBanDen, đúng Domino gốc)")
    public ResponseEntity<ApiResponse<UUID>> ganVanBanLienQuan(@Valid @RequestBody GanVanBanLienQuanDuThaoRequest request) {
        return ok(ganVanBanLienQuanDuThaoUseCase.execute(request));
    }

    @PostMapping("/XoaVanBanLienQuan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa 1 văn bản khỏi danh sách 'Văn bản kèm theo' (chi tiết dự thảo)",
        description = "Tương đương VanBanLienQuanList.jsx#handleDelete — loại đúng 1 phần tử khỏi "
            + "vanban_lienquan_obj theo documentId (server tự tìm, không tin nguyên mảng FE gửi lên)")
    public ResponseEntity<ApiResponse<UUID>> xoaVanBanLienQuan(@Valid @RequestBody XoaVanBanLienQuanDuThaoRequest request) {
        return ok(xoaVanBanLienQuanDuThaoUseCase.execute(request));
    }

    @PostMapping("/LuuVetHieuChinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi vết hiệu chỉnh field của dự thảo (27/08/2026, port LogVariable "
        + "getChangedFieldDetails -> qlvb/add Domino)",
        description = "Mỗi lần Lưu có field đổi ghi 1 dòng {field:{oldvalue,newvalue}} vào bảng "
            + "duthao_edit_history_documents (bảng lưu vết RIÊNG). user_creator do SERVER tự gán theo "
            + "tài khoản đang đăng nhập (SERVER-AUTHORITATIVE); creator_name chỉ là tên hiển thị tuỳ "
            + "chọn. edit_obj bắt buộc khác rỗng — khớp Domino: không có thay đổi thì không sinh vết")
    public ResponseEntity<ApiResponse<UUID>> luuVetHieuChinh(@Valid @RequestBody AddDuThaoEditHistoryRequest request) {
        return created(addDuThaoEditHistoryUseCase.execute(request));
    }

    @PostMapping("/SeenDocument")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi nhật ký đã xem dự thảo (25/09/2026, mirror vbdi/SeenDocument)",
        description = "Người gọi (server-authoritative) được thêm vào log_seen_document và "
            + "log_seen_document_detail {username,name,unit,created,lastSeen} — FE LogSeenDocument.jsx")
    public ResponseEntity<ApiResponse<UUID>> seenDocument(@Valid @RequestBody AddDuThaoSeenDocumentRequest request) {
        return ok(addDuThaoSeenDocumentUseCase.execute(request));
    }

    @PostMapping("/VetHieuChinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử hiệu chỉnh theo dự thảo cha (tương thích qlvb/danhmuc/GetRes)",
        description = "Đúng khuôn item legacy {id,parentid,form=log,form_edit,edit_obj,creator,"
            + "user_creator,created} để VetHieuChinh.jsx render; thứ tự TĂNG DẦN theo tạo ra — khớp "
            + "nguồn Domino trước khi FE reverse để hiện mới nhất trên cùng")
    public ResponseEntity<ApiResponse<PaginationResponse<Map<String, Object>>>> vetHieuChinh(
            @Valid @RequestBody ListDuThaoEditHistoryRequest request) {
        PaginationResponse<Map<String, Object>> result = listDuThaoEditHistoryUseCase.execute(request);
        // Không dùng BaseController.ok(...): overload `ok(PaginationResponse)` trả
        // ApiResponse<List<T>>, trong khi FE đọc `data.items` — cần giữ ApiResponse<PaginationResponse>.
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách dự thảo theo view (DT-002, tương thích "
        + "qlvb/vanbanduthao/GetView?databasetype=duthao)",
        description = "Uỷ quyền cho ViewQueryService (BL-102) — hỗ trợ đủ 26 view hàng đợi cá nhân "
            + "của duthao (DT-002, chi tiết điều kiện từng view xem javadoc ViewQueryService khối "
            + "constants) + vwGroup_process_by_draft_id (DT-007, lịch sử xử lý theo id dự thảo, khác "
            + "hàng đợi cá nhân — khoá không phải username). Ngoại lệ vẫn đi Domino: "
            + "vwUser_Draft_Deleted (thùng rác), vwChoDuyetKyBanHanh/MC (OR-group)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }
}
