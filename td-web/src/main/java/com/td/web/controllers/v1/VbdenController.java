package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.PaginationResponse;
import com.td.application.vbden.AddVbdenEditHistoryRequest;
import com.td.application.vbden.AddVbdenEditHistoryUseCase;
import com.td.application.vbden.ListVbdenEditHistoryRequest;
import com.td.application.vbden.ListVbdenEditHistoryUseCase;
import com.td.application.vbden.AddVbdenUserActionLogRequest;
import com.td.application.vbden.AddVbdenUserActionLogUseCase;
import com.td.application.vbden.ListVbdenUserActionLogRequest;
import com.td.application.vbden.ListVbdenUserActionLogUseCase;
import com.td.application.vbden.CapNhatKetQuaXuLyRequest;
import com.td.application.vbden.CapNhatKetQuaXuLyUseCase;
import com.td.application.vbden.CapNhatPhoiHopRequest;
import com.td.application.vbden.CapNhatPhoiHopUseCase;
import com.td.application.vbden.CapNhatDaXemDeBietRequest;
import com.td.application.vbden.CapNhatDaXemDeBietUseCase;
import com.td.application.vbden.TheoDoiNhiemVuRequest;
import com.td.application.vbden.TheoDoiNhiemVuUseCase;
import com.td.application.vbden.TheoDoiVanBanDenRequest;
import com.td.application.vbden.TheoDoiVanBanDenUseCase;
import com.td.application.vbden.ChoYKienXinYKienRequest;
import com.td.application.vbden.ChoYKienXinYKienUseCase;
import com.td.application.vbden.GuiXinYKienRequest;
import com.td.application.vbden.GuiXinYKienUseCase;
import com.td.application.vbden.KetThucXuLyRequest;
import com.td.application.vbden.KetThucXuLyUseCase;
import com.td.application.vbden.LayLaiVanBanRequest;
import com.td.application.vbden.LayLaiVanBanUseCase;
import com.td.application.vbden.LichSuXuLyRequest;
import com.td.application.vbden.LichSuXuLyUseCase;
import com.td.application.vbden.ThuHoiVanBanRequest;
import com.td.application.vbden.ThuHoiVanBanUseCase;
import com.td.application.vbden.XuLyLaiRequest;
import com.td.application.vbden.XuLyLaiUseCase;
import com.td.application.vbden.ChuyenXuLyVbdenRequest;
import com.td.application.vbden.ChuyenXuLyVbdenUseCase;
import com.td.application.vbden.GetVbdenDetailRequest;
import com.td.application.vbden.GetVbdenDetailUseCase;
import com.td.application.vbden.GetNguoiTraLaiRequest;
import com.td.application.vbden.GetNguoiTraLaiUseCase;
import com.td.application.vbden.GetNguoiThuHoiRequest;
import com.td.application.vbden.GetNguoiThuHoiUseCase;
import com.td.application.vbden.GuiXinYKienTuVBdenRequest;
import com.td.application.vbden.GuiXinYKienTuVBdenUseCase;
import com.td.application.vbden.TraLaiVanBanRequest;
import com.td.application.vbden.TraLaiVanBanUseCase;
import com.td.application.vbden.SuaYKienVbdenRequest;
import com.td.application.vbden.SuaYKienVbdenUseCase;
import com.td.application.vbden.CapNhatYKienVbdenRequest;
import com.td.application.vbden.CapNhatYKienVbdenUseCase;
import com.td.application.vbden.ChuyenXuLyVbcddhNhiemVuRequest;
import com.td.application.vbden.ChuyenXuLyVbcddhNhiemVuUseCase;
import com.td.application.vbden.GetSoLuongCanXuLyUseCase;
import com.td.application.vbden.BaoCaoTheoPhongVbdenRequest;
import com.td.application.vbden.BaoCaoTheoPhongVbdenUseCase;
import com.td.application.vbden.GetSoVanBanDenRequest;
import com.td.application.vbden.GetSoVanBanDenUseCase;
import com.td.application.vbden.GuiSangVbcddhRequest;
import com.td.application.vbden.GuiSangVbcddhUseCase;
import com.td.application.vbden.KhongVaoSoVbdenRequest;
import com.td.application.vbden.KhongVaoSoVbdenUseCase;
import com.td.application.vbden.KiemTrungSoDenRequest;
import com.td.application.vbden.KiemTrungSoDenUseCase;
import com.td.application.vbden.KiemTrungVbdenRequest;
import com.td.application.vbden.KiemTrungVbdenUseCase;
import com.td.application.vbden.LuuVanBanDenRequest;
import com.td.application.vbden.LuuVanBanDenResultDto;
import com.td.application.vbden.LuuVanBanDenUseCase;
import com.td.application.vbden.TaoDuThaoVBdenRequest;
import com.td.application.vbden.TaoDuThaoVBdenUseCase;
import com.td.application.vbden.TiepNhanVbdenRequest;
import com.td.application.vbden.TiepNhanVbdenUseCase;
import com.td.application.vbden.TuChoiTiepNhanVbdenRequest;
import com.td.application.vbden.TuChoiTiepNhanVbdenUseCase;
import com.td.application.vbden.CapNhatVanBanThayTheThuHoiRequest;
import com.td.application.vbden.CapNhatVanBanThayTheThuHoiUseCase;
import com.td.application.vbden.XoaVanBanDenRequest;
import com.td.application.vbden.XoaVanBanDenUseCase;
import com.td.application.vbden.PhucHoiVanBanDenRequest;
import com.td.application.vbden.PhucHoiVanBanDenUseCase;
import com.td.application.vbden.VbdenDuplicateDto;
import com.td.application.vbden.VbdenStatisticsGroupRequest;
import com.td.application.vbden.VbdenQuaTrinhXuLyRequest;
import com.td.application.vbden.VBdenChuyenVanBanPhapQuyRequest;
import com.td.application.vbden.VBdenChuyenVanBanPhapQuyUseCase;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.application.common.models.Result;
import com.td.application.vbden.GanKemTepVbdenRequest;
import com.td.application.vbden.GanKemTepVbdenUseCase;
import com.td.application.vbden.KySoVbdenRequest;
import com.td.application.vbden.KySoVbdenUseCase;
import com.td.infrastructure.security.QlvbRoles;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-210 (VBDen Core): Tiếp nhận / Danh sách theo view / Xem chi tiết / Quá trình xử lý của văn
 * bản đến — tương thích API legacy {@code qlvb/vbden/*}. Toàn bộ nghiệp vụ dùng chung hạ tầng đã có
 * (BL-101 Dynamic Data, BL-102 View Query, BL-104 Numbering) trên bảng {@code documents} chung
 * ({@code document_type = "vbden"}), không tạo bảng/entity riêng cho VBDen.
 */
@RestController
@RequestMapping("/api/v1/vbden")
@RequiredArgsConstructor
@Validated
@Tag(name = "VBDen", description = "Văn bản đến — Core (BL-210), tương thích qlvb/vbden/*")
public class VbdenController extends BaseController {

    private static final String DB_TYPE = "vbden";

    // BL-210 DoD (4): "quá trình xử lý" đọc document con (dbType riêng "vbden_res", tương đương
    // Domino databasetype=vbden_res) theo field động parentid — xem VbdenQuaTrinhXuLyRequest.
    private static final String PROCESS_HISTORY_DB_TYPE = "vbden_res";
    private static final String PROCESS_HISTORY_VIEWNAME = "QTXL_by_parentid";

    // SEC-01: quyền "cập nhật/hiệu chỉnh văn bản đến" thật (docs/frontend/05-vbden-vbdi.md mục
    // "Quyền" — currentUser.roles.includes("QCapNhatVBDen")), cộng ADMIN làm lối thoát quản trị
    // (quy ước đã dùng chung toàn hệ thống). Đọc (XemChiTiet/GetView/QuaTrinhXuLy) không có bằng
    // chứng bị chặn theo role ở Domino thật (row-level ACL gần như không enforce trên luồng vbden —
    // xem 05-review-p0-p1-bl210-vs-domino.md) nên chỉ yêu cầu đã đăng nhập.
    private static final String CAP_NHAT_VBDEN_ROLES =
        "hasAnyRole('ADMIN', '" + QlvbRoles.CAP_NHAT_VBDEN + "', '" + QlvbRoles.CAP_NHAT_VBDEN_DV
            + "', '" + QlvbRoles.CAP_NHAT_VBDEN_PB + "', '" + QlvbRoles.QUAN_TRI + "')";

    // BL-211: quyền y hệt các role đã xác nhận gate hành vi phân công/chuyển tiếp trong
    // ChuyenXuLy.java thật (dòng ~436/480/486 — QPhanCong/QPhanPhoi/QCapNhatVBDen). Bổ sung
    // (24/09/2026, bugfix): thiếu 3 biến thể phạm vi đơn vị/phòng ban đã có sẵn trong catalog
    // app_permissions (QPhanCongDV/QPhanPhoiDV/QPhanCongPhong, V1.0.18) nhưng chưa từng được đưa
    // vào gate này — user chỉ được gán 1 trong 3 quyền phạm vi hẹp này (không có quyền
    // QPhanCong/QPhanPhoi toàn cơ quan) vẫn bị 403 khi gọi ChuyenXuLy.
    private static final String PHAN_CONG_CHUYEN_TIEP_ROLES =
        "hasAnyRole('ADMIN', '" + QlvbRoles.PHAN_CONG + "', '" + QlvbRoles.PHAN_PHOI + "', '"
            + QlvbRoles.PHAN_CONG_DV + "', '" + QlvbRoles.PHAN_PHOI_DV + "', '"
            + QlvbRoles.PHAN_CONG_PHONG + "', '"
            + QlvbRoles.CAP_NHAT_VBDEN + "', '" + QlvbRoles.CAP_NHAT_VBDEN_DV + "', '"
            + QlvbRoles.CAP_NHAT_VBDEN_PB + "', '" + QlvbRoles.QUAN_TRI + "')";

    // 09/10/2026 (bugfix 403 lần 2 — demo13): role ở trên HOẶC đang tham gia xử lý chính văn bản đó
    // (có tên trong field user_*/author) — chuyên viên nhận văn bản chuyển tiếp/ghi ý kiến không cần
    // quyền phân công, đúng Domino ChuyenXuLy.java (xem javadoc VbdenParticipantChecker).
    private static final String CHUYEN_XU_LY_ACCESS = PHAN_CONG_CHUYEN_TIEP_ROLES
        + " or @vbdenParticipantChecker.isCurrentUserParticipant(#request.parentid)";

    private final TiepNhanVbdenUseCase tiepNhanVbdenUseCase;
    private final GetVbdenDetailUseCase getVbdenDetailUseCase;
    private final ViewQueryService viewQueryService;
    private final ChuyenXuLyVbdenUseCase chuyenXuLyVbdenUseCase;
    private final GuiSangVbcddhUseCase guiSangVbcddhUseCase;
    private final ChuyenXuLyVbcddhNhiemVuUseCase chuyenXuLyVbcddhNhiemVuUseCase;
    private final KiemTrungVbdenUseCase kiemTrungVbdenUseCase;
    private final KiemTrungSoDenUseCase kiemTrungSoDenUseCase;
    private final GetSoVanBanDenUseCase getSoVanBanDenUseCase;
    private final LuuVanBanDenUseCase luuVanBanDenUseCase;
    private final ChoYKienXinYKienUseCase choYKienXinYKienUseCase;
    private final GuiXinYKienUseCase guiXinYKienUseCase;
    private final LichSuXuLyUseCase lichSuXuLyUseCase;
    private final XuLyLaiUseCase xuLyLaiUseCase;
    private final ThuHoiVanBanUseCase thuHoiVanBanUseCase;
    private final ProcessDiagramService processDiagramService;
    private final CapNhatKetQuaXuLyUseCase capNhatKetQuaXuLyUseCase;
    private final VBdenChuyenVanBanPhapQuyUseCase vbdenChuyenVanBanPhapQuyUseCase;
    private final KetThucXuLyUseCase ketThucXuLyUseCase;
    private final LayLaiVanBanUseCase layLaiVanBanUseCase;
    private final TaoDuThaoVBdenUseCase taoDuThaoVBdenUseCase;
    private final TuChoiTiepNhanVbdenUseCase tuChoiTiepNhanVbdenUseCase;
    private final CapNhatVanBanThayTheThuHoiUseCase capNhatVanBanThayTheThuHoiUseCase;
    private final KhongVaoSoVbdenUseCase khongVaoSoVbdenUseCase;
    private final CapNhatPhoiHopUseCase capNhatPhoiHopUseCase;
    private final CapNhatDaXemDeBietUseCase capNhatDaXemDeBietUseCase;
    private final TheoDoiNhiemVuUseCase theoDoiNhiemVuUseCase;
    private final TheoDoiVanBanDenUseCase theoDoiVanBanDenUseCase;
    private final GetSoLuongCanXuLyUseCase getSoLuongCanXuLyUseCase;
    private final BaoCaoTheoPhongVbdenUseCase baoCaoTheoPhongVbdenUseCase;
    private final AddVbdenEditHistoryUseCase addVbdenEditHistoryUseCase;
    private final ListVbdenEditHistoryUseCase listVbdenEditHistoryUseCase;
    private final com.td.application.vbden.ListVbdenUpdateHistoryUseCase listVbdenUpdateHistoryUseCase;
    private final XoaVanBanDenUseCase xoaVanBanDenUseCase;
    private final PhucHoiVanBanDenUseCase phucHoiVanBanDenUseCase;
    private final GetNguoiTraLaiUseCase getNguoiTraLaiUseCase;
    private final GetNguoiThuHoiUseCase getNguoiThuHoiUseCase;
    private final GuiXinYKienTuVBdenUseCase guiXinYKienTuVBdenUseCase;
    private final TraLaiVanBanUseCase traLaiVanBanUseCase;
    private final CapNhatYKienVbdenUseCase capNhatYKienVbdenUseCase;
    private final KySoVbdenUseCase kySoVbdenUseCase;
    private final GanKemTepVbdenUseCase ganKemTepVbdenUseCase;
    private final SuaYKienVbdenUseCase suaYKienVbdenUseCase;
    private final AddVbdenUserActionLogUseCase addVbdenUserActionLogUseCase;
    private final ListVbdenUserActionLogUseCase listVbdenUserActionLogUseCase;

    @PostMapping("/TiepNhan")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Tiếp nhận văn bản đến (BL-210, tương thích qlvb/add?databasetype=vbden)",
        description = "form-data field động (subject, unit_in_charge, received_date, ...) + meta "
            + "date_fields/number_fields/richtext_fields (BL-101). Số đến (document_number) được "
            + "sinh tự động theo cấu hình numbering đã có sẵn cho vbden (BL-104, format {seq}/{year})")
    public ResponseEntity<ApiResponse<UUID>> tiepNhan(@RequestParam Map<String, String> params) {
        return created(tiepNhanVbdenUseCase.execute(buildTiepNhanRequest(params)));
    }

    private TiepNhanVbdenRequest buildTiepNhanRequest(Map<String, String> params) {
        var request = new TiepNhanVbdenRequest();
        request.setSubject(firstNonBlank(params.get("subject"), params.get("title")));
        request.setStatus(params.get("status"));
        request.setDateFields(params.get("date_fields"));
        request.setNumberFields(params.get("number_fields"));
        request.setRichtextFields(params.get("richtext_fields"));
        // BL-210/211: "sổ văn bản" chọn khi Nhập mới (BL-116) — meta riêng, không phải field động.
        String documentRegisterId = params.get("document_register_id");
        if (documentRegisterId != null && !documentRegisterId.isBlank()) {
            try {
                request.setDocumentRegisterId(UUID.fromString(documentRegisterId.trim()));
            } catch (IllegalArgumentException ignored) {
                // document_register_id không hợp lệ (không phải UUID) — bỏ qua, coi như không chọn sổ
                // (giữ hành vi cũ, không chặn tạo văn bản chỉ vì meta này sai định dạng).
            }
        }

        // Bugfix 24/08/2026: "document_type" KHÔNG được strip khỏi fields động nữa. Đây là field
        // nghiệp vụ thật của form vbden — "Loại văn bản" (Công văn/Báo cáo/Quyết định..., xem
        // SubFormThongTinCoBan.jsx name="document_type", giá trị lấy từ danhmuc_by_form/document_type
        // — đối chiếu docs/views_domino/data_vbden_mau.txt: "document_type": "Công văn") — hoàn toàn
        // khác với documentType/DOCUMENT_TYPE ở tầng entity (module discriminator nội bộ, hardcode
        // "vbden" trong TiepNhanVbdenUseCase, KHÔNG đọc từ params). Trước fix này, giá trị "Loại văn
        // bản" người dùng chọn khi Tiếp nhận bị xoá âm thầm, không bao giờ lưu vào dynamic_fields.
        Map<String, String> fields = new LinkedHashMap<>(params);
        for (String metaKey : List.of(
                "subject", "title", "status", "date_fields", "number_fields", "richtext_fields",
                "document_register_id")) {
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
    @Operation(summary = "Xem chi tiết văn bản đến (BL-210, tương thích qlvb/vbden/XemChiTiet)",
        description = "items=\"getall\" trả về tất cả field (tĩnh + động), hoặc items=\"field1;field2\" lọc theo "
            + "danh sách field. idso (Slice 9, bugfix 21/08/2026): khi xem chi tiết 1 dòng trong \"Vào sổ văn bản "
            + "đến\", id của bản ghi sổ (documentType=vbden_so) — server tự join với văn bản gốc (idvbden)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(
            @RequestBody GetVbdenDetailRequest request) {
        return ok(getVbdenDetailUseCase.execute(request));
    }

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách văn bản đến theo view (BL-210, tương thích qlvb/vbden/GetView)",
        description = "Uỷ quyền cho ViewQueryService (BL-102) — vwDocument_ChoXuLy (chờ xử lý, "
            + "keys=[username]), vwDocument_DaXuLy (đã xử lý, keys=[username])")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }

    @PostMapping("/GetViewGroups")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cây thống kê văn bản đến theo người/đơn vị chủ trì",
        description = "Port trực tiếp hai Domino view theonguoichutri và theodonvichutri; "
            + "trả response tương thích GroupTableVbden cũ.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getViewGroups(
            @Valid @RequestBody VbdenStatisticsGroupRequest request) {
        var result = viewQueryService.executeVbdenStatisticsGroups(request);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", result.type());
        properties.put("total_group", result.totalGroup());
        return ResponseEntity.ok(ApiResponse.success(result.data(), result.total(), properties));
    }

    @PostMapping("/QuaTrinhXuLy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem quá trình xử lý văn bản đến (BL-210, tương thích qlvb/vbden/QuaTrinhXuLy)",
        description = "Đọc danh sách document con theo field động parentid (tương đương view "
            + "QTXL_by_parentid, databasetype=vbden_res phía Domino). Đây là khả năng ĐỌC — các "
            + "hành động ghi quá trình xử lý (phân công, chuyển tiếp) thuộc phạm vi BL-211")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> quaTrinhXuLy(
            @Valid @RequestBody VbdenQuaTrinhXuLyRequest request) {
        var viewQueryRequest = new ViewQueryRequest();
        viewQueryRequest.setViewname(PROCESS_HISTORY_VIEWNAME);
        viewQueryRequest.setKeys(List.of(request.getParentid().toString()));
        viewQueryRequest.setItems(request.getItems());
        viewQueryRequest.setPageNumber(request.getPageNumber());
        viewQueryRequest.setPageSize(request.getPageSize());

        return ok(viewQueryService.execute(PROCESS_HISTORY_DB_TYPE, viewQueryRequest));
    }

    @PostMapping("/SoDoXuLy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sơ đồ xử lý văn bản đến (R8, bổ sung 02/09/2026, tương thích 1 phần "
        + "qlvb/get?databasetype=vbden_res)",
        description = "Đọc cây sơ đồ xử lý (bảng process_diagrams — ProcessDiagramService, ghi bởi "
            + "ChuyenXuLyVbdenUseCase/ThuHoiVanBanUseCase, keyed theo documentId=parentid của văn bản "
            + "gốc + documentType=\"vbden\"). Trước đây có ghi (write) nhưng CHƯA có endpoint đọc — "
            + "xem tài liệu phân tích 03 mục 5. Trả {diagram_obj: <JSON string cây, hoặc null nếu văn "
            + "bản chưa có sơ đồ>} — giữ đúng shape key mà tree_model/sodoxuly.jsx (FE) đã đọc sẵn từ "
            + "res.data.diagram_obj, để cutover FE chỉ cần đổi URL/payload, không đổi logic parse")
    public ResponseEntity<ApiResponse<Map<String, Object>>> soDoXuLy(
            @Valid @RequestBody VbdenQuaTrinhXuLyRequest request) {
        String diagramJson = processDiagramService.getDiagramJson(request.getParentid(), DB_TYPE);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("diagram_obj", diagramJson);
        return ok(data);
    }

    @PostMapping("/LichSuXuLy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử xử lý cá nhân (BL-210 DoD 4 mở rộng, tương thích qlvb/vbden/LichSuXuLy)",
        description = "Toàn bộ bản ghi quá trình xử lý (vbden_res) mà người dùng ĐANG ĐĂNG NHẬP là "
            + "người tạo, xuyên suốt MỌI văn bản đến (khác QuaTrinhXuLy — chỉ giới hạn 1 parentid). "
            + "Username lấy từ token đăng nhập (KHÔNG nhận tham số từ FE) — xem javadoc LichSuXuLyUseCase")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> lichSuXuLy(
            @Valid @RequestBody LichSuXuLyRequest request) {
        return ok(lichSuXuLyUseCase.execute(request));
    }

    @PostMapping("/XuLyLai")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xử lý lại văn bản đến (BL-211 mở rộng, tương thích qlvb/vbden/XuLyLai)",
        description = "Lấy lại văn bản ĐÃ xử lý xong (username đang có trong user_processed) để xử "
            + "lý lại — loại khỏi user_processed, thêm lại vào user_process, tính lại status, tạo "
            + "bản ghi quá trình xử lý (action=xulylai). Không tìm thấy role gate riêng trong "
            + "XuLyLai.java thật nên chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> xuLyLai(@Valid @RequestBody XuLyLaiRequest request) {
        return created(xuLyLaiUseCase.execute(request));
    }

    // 18/09/2026 (cutover ModalThuHoiVanBan.jsx - man chi tiet van ban den, nhanh "Chon don vi/nguoi
    // de thu hoi") - xem javadoc GetNguoiThuHoiUseCase.
    @PostMapping("/GetNguoiThuHoi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách người/đơn vị có thể thu hồi (modal Thu hồi văn bản, bổ sung "
        + "18/09/2026, tương thích qlvb/vbden/GetNguoiThuHoi)",
        description = "Người/đơn vị do CHÍNH người đang thao tác đã giao qua các lần chuyển xử lý VÀ "
            + "hiện vẫn còn chưa xử lý (đối chiếu snapshot _obj của các bản ghi vbden_res với field "
            + "hiện tại của văn bản cha) — để FE hiển thị bảng chọn trước khi gọi ThuHoiVanBan với "
            + "type=select. Không tìm thấy role gate riêng trong GetNguoiThuHoi.java thật nên chỉ yêu "
            + "cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> getNguoiThuHoi(
            @Valid @RequestBody GetNguoiThuHoiRequest request) {
        return ok(getNguoiThuHoiUseCase.execute(request));
    }

    @PostMapping("/ThuHoiVanBan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thu hồi văn bản đến (BL-211 mở rộng, tương thích qlvb/vbden/ThuHoiVanBan)",
        description = "Thu hồi TOÀN BỘ người/đơn vị đã được giao qua các lần chuyển xử lý DO CHÍNH "
            + "người thực hiện thao tác này tạo ra (nhánh thuHoiToanBo, type mặc định/\"all\" — xem "
            + "javadoc ThuHoiVanBanUseCase), dựa trên snapshot _obj mà ChuyenXuLyVbdenUseCase ghi từ "
            + "01/09/2026. Bổ sung 18/09/2026: type=\"select\" + 4 field *Obj — thu hồi ĐÚNG danh "
            + "sách FE gửi (nhánh thuHoi() Domino gốc, dùng bởi ModalThuHoiVanBan.jsx). Không tìm "
            + "thấy role gate riêng trong ThuHoiVanBan.java thật nên chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> thuHoiVanBan(@Valid @RequestBody ThuHoiVanBanRequest request) {
        return created(thuHoiVanBanUseCase.execute(request));
    }

    @PostMapping("/LayLaiVanBan")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Lấy lại văn bản đến (N18, bổ sung 04/09/2026, tương thích 1 phần "
        + "qlvb/vbden/VanThuThuHoiVanBan)",
        description = "Thu hồi CẢ NHÁNH TỔ CHỨC dưới người thao tác (identifiers chứa identifier "
            + "người thao tác — khác ThuHoiVanBan chỉ thu hồi người do CHÍNH người này giao), reset "
            + "toàn bộ routing nếu văn bản được tạo trong CHÍNH tổ chức người thao tác. CHƯA port: "
            + "nhánh tra sơ đồ theo identifier (không còn dữ liệu để khớp với mô hình sơ đồ hiện tại), "
            + "gửi mailin liên-server cho đơn vị ngoài — xem javadoc LayLaiVanBanUseCase")
    public ResponseEntity<ApiResponse<UUID>> layLaiVanBan(@Valid @RequestBody LayLaiVanBanRequest request) {
        return created(layLaiVanBanUseCase.execute(request));
    }

    @PostMapping("/ChuyenXuLy")
    @PreAuthorize(CHUYEN_XU_LY_ACCESS)
    @Operation(summary = "Phân công / Chuyển tiếp / Ghi ý kiến xử lý văn bản đến (BL-211, tương thích qlvb/vbden/ChuyenXuLy)",
        description = "Một action dùng chung cho phân công xử lý (DoD 1), ghi ý kiến xử lý (DoD 2), "
            + "và chuyển tiếp (DoD 3) — đúng thiết kế Domino thật (ChuyenXuLy.java dùng 1 action, "
            + "phân biệt bởi tham số action, vd. phanxuly/chuyenxuly/phanphoi). Tạo bản ghi quá trình "
            + "xử lý mới trong vbden_res (đọc lại qua QuaTrinhXuLy — BL-210) và cập nhật user_process "
            + "trên văn bản gốc. Đính kèm file (phần còn lại của DoD 2): gọi tiếp "
            + "POST /api/v1/documents/{id}/files với id trả về từ endpoint này (tái dùng UploadFileUseCase "
            + "có sẵn, không cần API tải file riêng cho VBDen)")
    public ResponseEntity<ApiResponse<UUID>> chuyenXuLy(@Valid @RequestBody ChuyenXuLyVbdenRequest request) {
        return created(chuyenXuLyVbdenUseCase.execute(request));
    }

    @PostMapping("/CapNhatKetQuaXuLy")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Cập nhật kết quả xử lý (N15, bổ sung 03/09/2026, tương thích 1 phần "
        + "qlvb/vbden/CapNhatVanBanDen?action=vanthuketthucxuly)",
        description = "Văn thư kết thúc xử lý cho những người/đơn vị CÙNG đơn vị với văn thư (đối "
            + "chiếu chuỗi identifiers tổ chức) — loại khỏi user_process/user_processing/"
            + "user_coordinate/user_to_read/user_distributor, thêm vào user_processed, tính lại "
            + "status. CHƯA port: gắn văn bản trả lời (res_obj/processResObj) — xem javadoc "
            + "CapNhatKetQuaXuLyUseCase")
    public ResponseEntity<ApiResponse<UUID>> capNhatKetQuaXuLy(
            @Valid @RequestBody CapNhatKetQuaXuLyRequest request) {
        return created(capNhatKetQuaXuLyUseCase.execute(request));
    }

    // Yeu cau nguoi dung 14/09/2026 ("modal Cap nhat ket qua xu ly van ban -> Dong y chua cutoverFE
    // (goi qlvb/vbden/CapNhatVanBanDen cu)") - action con lai cua modal ChoYKienModal.jsx chua cutover
    // (nut "Cho y kien"/"Bo sung y kien") - xem javadoc CapNhatYKienVbdenUseCase.
    @PostMapping("/CapNhatYKien")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cho ý kiến / Bổ sung ý kiến (bổ sung 14/09/2026, tương thích 1 phần "
        + "qlvb/vbden/CapNhatVanBanDen?action=capnhatykien)",
        description = "Switch-case Domino cho action này bị comment out hoàn toàn (no-op nghiệp vụ) — "
            + "CHỈ tạo 1 bản ghi quá trình xử lý (audit, action=capnhatykien), KHÔNG đụng field nào "
            + "trên văn bản gốc, KHÔNG tính lại status. Không tìm thấy role gate riêng nên chỉ yêu cầu "
            + "đã đăng nhập — xem javadoc CapNhatYKienVbdenUseCase")
    public ResponseEntity<ApiResponse<UUID>> capNhatYKien(@Valid @RequestBody CapNhatYKienVbdenRequest request) {
        return created(capNhatYKienVbdenUseCase.execute(request));
    }

    // Yeu cau nguoi dung 15/09/2026 ("port ky so cho cac module khac nhu vbdi, vbden ... giu file
    // goc truoc khi ky, file ky luu moi") - cung thiet ke VbdiController#kySo (BL-221 DoD 1): backend
    // KHONG tu ky so, chi luu tep DA KY (danh dau isPrimary=true, tep cu tu dong bi demote khong bi
    // xoa - xem UploadFileUseCaseImpl#demoteExistingPrimary).
    @PostMapping(value = "/KySo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ký số văn bản đến (15/09/2026)",
        description = "Backend KHÔNG tự ký số — người dùng ký số cục bộ bằng USB token (eSign/"
            + "TDeSign) rồi upload file PDF ĐÃ KÝ tại đây; use case chỉ lưu file (đánh dấu "
            + "isPrimary=true, tệp cũ tự động bị demote — KHÔNG bị xoá)")
    public ResponseEntity<ApiResponse<UUID>> kySo(
            @Parameter(description = "Document ID (văn bản đến)", required = true) @RequestParam UUID id,
            @Parameter(description = "File PDF đã ký số", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestParam("file") MultipartFile file) {
        // try-with-resources: đóng stream sau khi upload xong — stream còn mở giữ khoá tệp tạm multipart
        // trên Windows, Tomcat không xoá được ("Failed to deleted temporary file used for part [file]").
        try (java.io.InputStream fileStream = file.getInputStream()) {
            var request = new KySoVbdenRequest();
            request.setId(id);
            request.setFileContent(fileStream);
            request.setFileName(file.getOriginalFilename());
            request.setMimeType(file.getContentType());
            request.setFileSize(file.getSize());
            return created(kySoVbdenUseCase.execute(request));
        } catch (Exception ex) {
            return badRequest(Result.failure("Lỗi xử lý tệp tin: " + ex.getMessage()));
        }
    }

    @PostMapping("/GanKemTep")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đính kèm ý kiến (vết quá trình xử lý) văn bản đến (15/09/2026)",
        description = "Ghi 1 vết vbden_res (action=gankemtep) mang ý kiến — creator do SERVER tự "
            + "gán theo tài khoản đang đăng nhập, KHÔNG nhận từ client. KHÔNG mutate field nào trên "
            + "văn bản đến cha, KHÔNG tính lại status. Dùng sau khi ký số qua POST "
            + "/api/v1/vbden/KySo để ghi lại lịch sử \"đã sửa/ký tệp\", hiện ở tab Quá trình xử lý.")
    public ResponseEntity<ApiResponse<UUID>> ganKemTep(@Valid @RequestBody GanKemTepVbdenRequest request) {
        return created(ganKemTepVbdenUseCase.execute(request));
    }

    @PostMapping("/KetThucXuLy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kết thúc xử lý (N17, bổ sung 04/09/2026, tương thích 1 phần "
        + "qlvb/vbden/CapNhatVanBanDen?action=ketthucxuly)",
        description = "Người đang xử lý tự kết thúc phần việc CỦA MÌNH — loại actingUsername khỏi "
            + "user_process/user_receiver/user_processing/user_coordinate/user_to_read/"
            + "user_distributor/user_returned (không điều kiện theo đơn vị, khác "
            + "CapNhatKetQuaXuLy), thêm vào user_processed, tính lại status, tạo bản ghi quá trình "
            + "xử lý (action=ketthucxuly). CHƯA port: gắn văn bản trả lời (res_obj/processResObj), "
            + "badge/counter thông báo (TDInit.InitGiam/setViewError) — xem javadoc KetThucXuLyUseCase. "
            + "Không tìm thấy role gate riêng trong CapNhatVanBanDen.java thật cho action này nên chỉ "
            + "yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> ketThucXuLy(@Valid @RequestBody KetThucXuLyRequest request) {
        return created(ketThucXuLyUseCase.execute(request));
    }

    @PostMapping("/TaoDuThao")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo mới văn bản dự thảo trả lời (N19, bổ sung 04/09/2026, tương thích 1 phần "
        + "qlvb/vbden/CapNhatVanBanDen?action=taoduthao)",
        description = "GHI CHÚ: KHÁC POST /api/v1/duthao/TaoDuThao (tạo bản thân tài liệu dự thảo — "
            + "gọi TRƯỚC endpoint này, xem ChiTietPage.jsx). Endpoint này đánh dấu người đang thao tác "
            + "chuyển từ 'chờ xử lý' (user_process) sang 'đang soạn dự thảo' (user_processing) trên "
            + "CHÍNH văn bản đến gốc, và ghi bản ghi quá trình xử lý mới (action=taoduthao, kèm "
            + "res_obj tham chiếu văn bản dự thảo vừa tạo). Chỉ port action=taoduthao — 3 action anh "
            + "em dùng chung hàm Domino (taophieutrinh/ganduthaotraloi/taohoso) CHƯA port — xem javadoc "
            + "TaoDuThaoVBdenUseCase")
    public ResponseEntity<ApiResponse<UUID>> taoDuThao(@Valid @RequestBody TaoDuThaoVBdenRequest request) {
        return created(taoDuThaoVBdenUseCase.execute(request));
    }

    @PostMapping("/ChuyenVanBanPhapQuy")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Chuyển văn bản đến sang hệ thống văn bản quy phạm pháp luật (N16, bổ sung "
        + "03/09/2026, tương thích 1 phần qlvb/tichhop/VBdenChuyenVanBanPhapQuy)",
        description = "Tạo bản ghi vbden_vbpq (map OutNum/OutSymbol/isuse/OutLinhVuc/OutDate/"
            + "OutSigner/OutSigner_Pos/OutType/Subject) + gửi email (đính kèm toàn bộ file văn bản) "
            + "tới địa chỉ 'mailin' do FE gửi (đọc từ cfg_vbpq_mailin). Domino thật dùng cơ chế "
            + "'mail-in database' nội bộ (không phải SMTP) — xem javadoc VBdenChuyenVanBanPhapQuyUseCase")
    public ResponseEntity<ApiResponse<UUID>> chuyenVanBanPhapQuy(
            @Valid @RequestBody VBdenChuyenVanBanPhapQuyRequest request) {
        return created(vbdenChuyenVanBanPhapQuyUseCase.execute(request));
    }

    @PostMapping("/GuiSangVBCDDH")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển văn bản đến thành văn bản chỉ đạo điều hành (BL-211, tương thích qlvb/vbden/GuiSangVBCDDH)",
        description = "Tạo document mới documentType=vbcddh qua hạ tầng Dynamic Data Engine chung "
            + "(BL-101) — module VBCDDH đầy đủ (BL-250) chưa xây, dùng trực tiếp hạ tầng document "
            + "chung đúng rủi ro đã ghi trong backlog ('tạm thời dùng direct DB'). Đánh dấu "
            + "assignment_of_directive_document=1 trên văn bản đến gốc. Không tìm thấy role gate nào "
            + "trong GuiSangVBCDDH.java thật nên chỉ yêu cầu đã đăng nhập (không suy diễn quyền mới)")
    public ResponseEntity<ApiResponse<UUID>> guiSangVbcddh(@Valid @RequestBody GuiSangVbcddhRequest request) {
        return created(guiSangVbcddhUseCase.execute(request));
    }

    @PostMapping("/ChuyenXuLyVbcddhNhiemVu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đồng bộ lại nhiệm vụ vbcddh khi văn bản đến được Chuyển xử lý lần nữa "
        + "(tương thích qlvb/vbden/ChuyenXuLyVBCDDH)",
        description = "KHÁC POST .../GuiSangVBCDDH (tạo MỚI nhiệm vụ lần đầu): endpoint này chỉ đồng "
            + "bộ lại nhiệm vụ vbcddh ĐÃ TỒN TẠI (liên kết qua idvbden) mà người đang thao tác hiện "
            + "đang được giao thực hiện — ghi đè đơn vị/chuyên viên thực hiện + phối hợp theo lựa chọn "
            + "mới, ghi 1 vết tiến độ (vbcddh_res), rồi rollup lại văn bản CĐĐH cha. Best-effort — lỗi "
            + "đồng bộ 1 nhiệm vụ không chặn request. Không tìm thấy role gate riêng trong "
            + "ChuyenXuLyVBCDDH.java thật nên chỉ yêu cầu đã đăng nhập — xem javadoc "
            + "ChuyenXuLyVbcddhNhiemVuUseCase")
    public ResponseEntity<ApiResponse<UUID>> chuyenXuLyVbcddhNhiemVu(
            @Valid @RequestBody ChuyenXuLyVbcddhNhiemVuRequest request) {
        return created(chuyenXuLyVbcddhNhiemVuUseCase.execute(request));
    }

    @PostMapping("/TuChoiTiepNhan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Từ chối tiếp nhận / Trả lại văn bản đến (bổ sung 07/09/2026 (2)(8), tương "
        + "thích qlvb/vbdi?databasetype=vbden&action=ReplyDocumentOut, nhánh type != \"vaoso\")",
        description = "type=\"tralai\" = Trả lại (is_document_reply=1) hoặc type khác = Từ chối tiếp "
            + "nhận (is_document_refuse=1) — CẢ 2 đều publish phản hồi trạng thái cho đơn vị gửi "
            + "(STATUS_02 'Từ chối tiếp nhận' hoặc status_code phi chuẩn \"tralai\"/'Trả lại' — đối "
            + "chiếu StatusProcess.java xác nhận \"tralai\" VẪN đi qua cơ chế response_status như mọi "
            + "mã khác). Cả 2 nhánh: loại identifier khỏi unit_process, thêm vào unit_discard/"
            + "user_discard (gộp cả CapNhatVanBanDen.java#khongvaoso() — Domino gọi 2 endpoint riêng "
            + "cho cùng 1 nút 'Từ chối'/'Trả lại', ở đây gộp 1 giao dịch atomic — KHÁC hẳn "
            + "POST .../KhongVaoSo, dùng cho nút 'Không vào sổ' riêng biệt, không publish broker). "
            + "Không tìm thấy role gate riêng trong ReplyDocumentOut.java thật nên chỉ yêu cầu đã "
            + "đăng nhập. CHƯA port: nhánh type=\"vaoso\" (thay thế/thu hồi văn bản đến) — xem javadoc "
            + "TuChoiTiepNhanVbdenUseCase")
    public ResponseEntity<ApiResponse<UUID>> tuChoiTiepNhan(@Valid @RequestBody TuChoiTiepNhanVbdenRequest request) {
        return created(tuChoiTiepNhanVbdenUseCase.execute(request));
    }

    // Yeu cau nguoi dung 13/09/2026 ("doc va port luon nhanh 2 di cho xong viec nay") - port nhanh
    // type="vaoso" cua ReplyDocumentOut.java (xu ly "thay the/thu hoi") con lai chua port o
    // TuChoiTiepNhanVbdenUseCase - xem javadoc CapNhatVanBanThayTheThuHoiUseCase.
    @PostMapping("/CapNhatVanBanThayTheThuHoi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật văn bản thay thế/thu hồi (bổ sung 13/09/2026, tương thích "
        + "qlvb/vbdi?databasetype=vbden&action=ReplyDocumentOut, nhánh type=\"vaoso\")",
        description = "Nút 'Cập nhật VB' (capnhatVB() — chovaoso/ItemsListTuChoi.jsx/ItemsList.jsx, "
            + "denquamang/ItemsList.jsx) cho văn bản đến mang bussiness_doc_type=\"1\"(Thu hồi)/\"4\""
            + "(Thay thế) + documentoutrevoke (trỏ văn bản đến CŨ, cùng tenant — do "
            + "VbdenIncomingDocumentWriter, td-woker, tự gán lúc giao hàng). Văn bản đến CŨ trạng thái "
            + "\"Mới\" bị huỷ hẳn (soft-delete); đã xử lý dở thì GIỮ NGUYÊN, chỉ nhận nội dung + tệp "
            + "đính kèm từ văn bản MỚI đè lên. Luôn publish phản hồi STATUS_03 'Đã tiếp nhận' cho đơn "
            + "vị gửi (tự bỏ qua nếu không phải văn bản qua giao liên thông) — không tìm thấy role gate "
            + "riêng trong ReplyDocumentOut.java thật nên chỉ yêu cầu đã đăng nhập, cùng quy ước "
            + "TuChoiTiepNhanVbdenUseCase")
    public ResponseEntity<ApiResponse<UUID>> capNhatVanBanThayTheThuHoi(
            @Valid @RequestBody CapNhatVanBanThayTheThuHoiRequest request) {
        return ok(capNhatVanBanThayTheThuHoiUseCase.execute(request));
    }

    @PostMapping("/KhongVaoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Không vào sổ văn bản đến (bổ sung 07/09/2026 (4), tương thích "
        + "qlvb/vbden/CapNhatVanBanDen?action=khongvaoso)",
        description = "KHÁC hẳn POST .../TuChoiTiepNhan (nút 'Từ chối'/'Trả lại', CÓ publish broker "
            + "trạng thái) — đây là nút 'Không vào sổ' RIÊNG ở các màn khác (vào sổ hàng loạt/đơn lẻ), "
            + "KHÔNG publish gì cho đơn vị gửi (đúng Domino thật, khongvaoso() không gọi ResponseStatus). "
            + "Loại identifier khỏi 4 field (contact_unit_identifier/unit_process/unit_coordinate/"
            + "unit_to_read — nhiều hơn TuChoiTiepNhan chỉ loại unit_process), thêm vào unit_discard/"
            + "user_discard. Không tìm thấy role gate riêng trong CapNhatVanBanDen.java thật cho "
            + "action này nên chỉ yêu cầu đã đăng nhập — xem javadoc KhongVaoSoVbdenUseCase")
    public ResponseEntity<ApiResponse<UUID>> khongVaoSo(@Valid @RequestBody KhongVaoSoVbdenRequest request) {
        return created(khongVaoSoVbdenUseCase.execute(request));
    }

    @PostMapping("/KiemTrung")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiểm tra trùng văn bản đến (BL-210/211, tương thích qlvb/vbden/KiemTrung)",
        description = "Trùng theo (identifier, promulgation_date, code) — cảnh báo trước khi lưu, không chặn cứng")
    public ResponseEntity<ApiResponse<List<VbdenDuplicateDto>>> kiemTrung(
            @RequestBody KiemTrungVbdenRequest request) {
        return ok(kiemTrungVbdenUseCase.execute(request));
    }

    @PostMapping("/KiemTrungSoDen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiểm tra trùng số đến trong cùng sổ (BL-210/211, tương thích qlvb/vbden/KiemTrungSoDen)",
        description = "Trùng document_number trong cùng document_register_id, chỉ tính văn bản do đơn vị "
            + "của người dùng hiện tại tạo")
    public ResponseEntity<ApiResponse<List<VbdenDuplicateDto>>> kiemTrungSoDen(
            @RequestBody KiemTrungSoDenRequest request) {
        return ok(kiemTrungSoDenUseCase.execute(request));
    }

    @PostMapping("/GetSoVanBanDen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách sổ văn bản đến (BL-210/211, Slice 9, tương thích qlvb/vbden/GetSoVanBanDen)",
        description = "Màn 'Vào sổ văn bản đến' — 6 viewname: sovanban, sovanban_sx_ngayden, homqua, "
            + "trongngay, trongthang, trongtuan (đọc từ docs/views_domino/view_vanbanden_so.dxl). "
            + "identifier tự resolve server-side (org-scoped, giống N05/N12/N22), 'keys' không dùng")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSoVanBanDen(
            @Valid @RequestBody GetSoVanBanDenRequest request) {
        return ok(getSoVanBanDenUseCase.execute(request));
    }

    @PostMapping("/Luu")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Hiệu chỉnh / Vào sổ văn bản đến ĐÃ TỒN TẠI (MVP, tương thích 1 phần "
        + "qlvb/vbden?action=luu khi id có giá trị)",
        description = "Chỉ port: (1) merge field vào văn bản gốc, (2) tạo/cập nhật bản ghi sổ khi "
            + "documentRegisterId có giá trị (đánh số qua DocumentRegister, giống TiepNhanVbdenUseCase). "
            + "CHƯA port: tự tạo quá trình xử lý, dọn khỏi danh sách chờ vào sổ, phản hồi văn bản đi "
            + "liên kết — xem javadoc LuuVanBanDenUseCase")
    public ResponseEntity<ApiResponse<LuuVanBanDenResultDto>> luu(@Valid @RequestBody LuuVanBanDenRequest request) {
        return ok(luuVanBanDenUseCase.execute(request));
    }

    // Yeu cau nguoi dung 12/09/2026 ("van ban den chua thay hien thi vet hieu chinh, lam nhu van
    // ban di") - mirror VbdiController#luuVetHieuChinh/vetHieuChinh, xem javadoc
    // AddVbdenEditHistoryUseCase/ListVbdenEditHistoryUseCase.
    @PostMapping("/LuuVetHieuChinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi vết hiệu chỉnh field của văn bản đến",
        description = "Mỗi lần Lưu có field đổi ghi 1 dòng {field:{oldvalue,newvalue}} vào bảng "
            + "vbden_edit_history_documents (bảng lưu vết RIÊNG). user_creator do SERVER tự gán "
            + "theo tài khoản đang đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> luuVetHieuChinh(
            @Valid @RequestBody AddVbdenEditHistoryRequest request) {
        return created(addVbdenEditHistoryUseCase.execute(request));
    }

    @PostMapping("/VetHieuChinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử hiệu chỉnh theo văn bản đến cha (tương thích qlvb/danhmuc/GetRes)",
        description = "Đúng khuôn item legacy {id,parentid,form=log,form_edit,edit_obj,creator,"
            + "user_creator,created} để VetHieuChinh.jsx render; thứ tự TĂNG DẦN theo tạo ra")
    public ResponseEntity<ApiResponse<PaginationResponse<Map<String, Object>>>> vetHieuChinh(
            @Valid @RequestBody ListVbdenEditHistoryRequest request) {
        PaginationResponse<Map<String, Object>> result = listVbdenEditHistoryUseCase.execute(request);
        // Khong dung BaseController.ok(...): overload ok(PaginationResponse) tra
        // ApiResponse<List<T>>, trong khi FE doc data.items - can giu ApiResponse<PaginationResponse>.
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // Yeu cau nguoi dung 24/09/2026 ("luu lai vet ngay gio cap nhat, co lien ket toi van ban da cap nhat,
    // the hien tep cu da cap nhat nhung khong cho tai, chi hien thi ten") - xem javadoc
    // ListVbdenUpdateHistoryUseCase. Ghi do CapNhatVanBanThayTheThuHoiUseCase tu lam (khong co endpoint ghi).
    @PostMapping("/LichSuCapNhat")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử cập nhật văn bản đến từ bản gửi lại/thay thế (nút Cập nhật VB)",
        description = "Mới nhất trước; mỗi item {id,parent_id,source_document_id,action,update_obj,creator,"
            + "user_creator,created}. update_obj.superseded_files chỉ mang tên/kích thước tệp cũ đã bị thay "
            + "thế (tệp đã bị ẩn, không tải được)")
    public ResponseEntity<ApiResponse<PaginationResponse<Map<String, Object>>>> lichSuCapNhat(
            @Valid @RequestBody com.td.application.vbden.ListVbdenUpdateHistoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(listVbdenUpdateHistoryUseCase.execute(request)));
    }

    // Yeu cau nguoi dung 18/09/2026 ("them vet audit cua van ban den, tham khao van ban di da lam va
    // co hien thi khi xem chi tiet") - mirror VbdiController#logUserAction/getLogUserAction, xem
    // javadoc AddVbdenUserActionLogUseCase/ListVbdenUserActionLogUseCase. KHAC "Vet hieu chinh" o
    // tren (chi ghi FIELD doi) - day la "Lich su thao tac van ban" (ghi HANH DONG da bam).
    @PostMapping("/LogUserAction")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi 1 dòng Lịch sử thao tác văn bản đến (18/09/2026)",
        description = "username SERVER-AUTHORITATIVE qua CurrentUserProvider — fullname/unit/identifier "
            + "là field trình bày, chấp nhận từ client. Không yêu cầu role cập nhật vbden vì đây là log "
            + "side-effect đi kèm nhiều hành động khác nhau, không phải 1 hành động nghiệp vụ độc lập")
    public ResponseEntity<ApiResponse<UUID>> logUserAction(
            @Valid @RequestBody AddVbdenUserActionLogRequest request) {
        return created(addVbdenUserActionLogUseCase.execute(request));
    }

    @PostMapping("/GetLogUserAction")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đọc Lịch sử thao tác văn bản theo văn bản đến (18/09/2026)",
        description = "Trả mảng PHẲNG (không bọc PaginationResponse) — mỗi phần tử là 1 người dùng, "
            + "mỗi loại thao tác là 1 field chứa danh sách timestamp nối ';' (đã tự nhóm lại từ bảng "
            + "phẳng vbden_user_action_logs, xem javadoc ListVbdenUserActionLogUseCase)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLogUserAction(
            @Valid @RequestBody ListVbdenUserActionLogRequest request) {
        return ok(listVbdenUserActionLogUseCase.execute(request));
    }

    // Yeu cau nguoi dung 14/09/2026 ("o chi tiet van ban den, chuc nang Xoa dang goi API cu ...
    // kiem tra neu chua co thi port sang JAVA va cutoverFE") - xem javadoc XoaVanBanDenUseCase.
    @PostMapping("/XoaVanBanDen")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Xóa văn bản đến (bổ sung 14/09/2026, tương thích qlvb/vbden/XoaVanBanDen)",
        description = "Soft-delete bản ghi \"sổ\" (idso, nếu có) KHÔNG điều kiện, và soft-delete "
            + "CHÍNH văn bản nội dung (idvbden) CHỈ KHI người thao tác là identifier_creator của nó "
            + "VÀ đây là bản ghi sổ cuối cùng của identifier đó (đúng XoaVanBanDen.java thật). Response "
            + "luôn thành công (idempotent) trừ khi thiếu idvbden hoặc lỗi hệ thống thật sự — đúng "
            + "hành vi Domino gốc (doService() luôn ghi đè status=true sau processRequest không lỗi) — "
            + "xem javadoc XoaVanBanDenUseCase")
    public ResponseEntity<ApiResponse<UUID>> xoaVanBanDen(@Valid @RequestBody XoaVanBanDenRequest request) {
        return ok(xoaVanBanDenUseCase.execute(request));
    }

    // Doi xung XoaVanBanDen - xem javadoc PhucHoiVanBanDenUseCase (vi sao port CUNG luc, khong tach rieng).
    @PostMapping("/PhucHoiVanBan")
    @PreAuthorize(CAP_NHAT_VBDEN_ROLES)
    @Operation(summary = "Phục hồi văn bản đến đã xóa (bổ sung 14/09/2026, tương thích qlvb/vbden/PhucHoiVanBan)",
        description = "Đối xứng XoaVanBanDen — phục hồi bản ghi \"sổ\" (idso, nếu có) KHÔNG điều kiện, "
            + "và phục hồi văn bản nội dung (idvbden) khi người thao tác là identifier_creator của nó "
            + "(port THỐNG NHẤT dùng identifier_creator cho cả xóa/phục hồi, khác PhucHoiVanBan.java "
            + "thật dùng user_creator==username — xem javadoc lớp)")
    public ResponseEntity<ApiResponse<UUID>> phucHoiVanBan(@Valid @RequestBody PhucHoiVanBanDenRequest request) {
        return ok(phucHoiVanBanDenUseCase.execute(request));
    }

    // Yeu cau nguoi dung 14/09/2026 ("chuc nang Tra lai -> modal Tra lai van ban -> dang goi
    // qlvb/vbden/GetNguoiTraLai?databasetype=vbden -> port sang java") - xem javadoc GetNguoiTraLaiUseCase.
    @PostMapping("/GetNguoiTraLai")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách người trả lại (modal Trả lại văn bản, bổ sung 14/09/2026, tương "
        + "thích qlvb/vbden/GetNguoiTraLai)",
        description = "Người đã 'chuyển xử lý' văn bản này cho người đang đăng nhập (đối chiếu "
            + "user_process_obj/user_assigner_obj snapshot của các bản ghi vbden_res action=chuyenxuly) "
            + "— để chọn 'trả lại' đúng người đó. Không tìm thấy role gate riêng trong "
            + "GetNguoiTraLai.java thật nên chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getNguoiTraLai(
            @Valid @RequestBody GetNguoiTraLaiRequest request) {
        return ok(getNguoiTraLaiUseCase.execute(request));
    }

    // Yeu cau nguoi dung 14/09/2026 ("modal Tra lai van ban -> chon va dong y -> dang goi api cu
    // qlvb/vbden/TraLaiVanBan?databasetype=vbden -> port va cutoverFE") - xem javadoc TraLaiVanBanUseCase.
    @PostMapping("/TraLaiVanBan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trả lại văn bản đến (bổ sung 14/09/2026, tương thích qlvb/vbden/TraLaiVanBan)",
        description = "Loại người đang thao tác khỏi user_process, thêm người được chọn trả lại (từ "
            + "dropdown GetNguoiTraLai) vào user_returned (view 'vanbantralai' khoá field này), tạo "
            + "bản ghi quá trình xử lý (action=tralai, snapshot người được trả lại), xoá node sơ đồ xử "
            + "lý của người thao tác. Không tìm thấy role gate riêng trong TraLaiVanBan.java thật nên "
            + "chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> traLaiVanBan(@Valid @RequestBody TraLaiVanBanRequest request) {
        return created(traLaiVanBanUseCase.execute(request));
    }

    @PostMapping("/SuaYKien")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sửa ý kiến quá trình xử lý (bổ sung 15/09/2026, tương thích 1 phần "
        + "qlvb/vbden/CapNhatVanBanDen?databasetype=vbden_res&action=suanoidung)",
        description = "Sửa TRỰC TIẾP field opinion của 1 bản ghi vbden_res ĐÃ CÓ (id truyền vào là id "
            + "của chính bản ghi đó, KHÔNG phải parentid) — KHÔNG tạo bản ghi mới, KHÔNG đụng văn bản "
            + "đến cha. Ngoại lệ có chủ đích với thiết kế append-only của vbden_res_documents, xem "
            + "javadoc SuaYKienVbdenUseCase. Không tìm thấy role gate riêng trong CapNhatVanBanDen.java "
            + "thật cho action này nên chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> suaYKien(@Valid @RequestBody SuaYKienVbdenRequest request) {
        return created(suaYKienVbdenUseCase.execute(request));
    }

    @PostMapping("/ChoYKien")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cho ý kiến (N08 — TVUB/TVNB/BCH/BTV/TTĐU, tương thích qlvb/vbden/ChuyenXuLy?action=choykien)",
        description = "Ghi ý kiến trên 'văn bản đến giả' (document_type=vbden_xinykien) — chuyển "
            + "người thực hiện khỏi user_process (rời chochoykien*) sang user_processed/user_delivered "
            + "(vào dachoykien*/dachuyenchoykien*). Đây là hành động CÁ NHÂN (người được hỏi ý kiến tự "
            + "cho ý kiến của mình), không yêu cầu quyền phân công/phân phối như ChuyenXuLy — chỉ cần "
            + "đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> choYKien(@Valid @RequestBody ChoYKienXinYKienRequest request) {
        return created(choYKienXinYKienUseCase.execute(request));
    }

    @PostMapping("/CapNhatPhoiHop")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật kết quả phối hợp xử lý (N20, bổ sung 07/09/2026, tương thích 1 "
        + "phần qlvb/vbden/CapNhatVanBanDen?action=capnhatphoihop)",
        description = "Người phối hợp báo đã xong phần việc của mình — loại actingUsername khỏi "
            + "user_coordinate, thêm vào user_coordinated (KHÔNG đụng user_process/user_processing, "
            + "KHÔNG tính lại status, khác KetThucXuLy/CapNhatKetQuaXuLy). Không làm gì thêm nếu "
            + "user_coordinate đang rỗng (đúng Domino thật) nhưng vẫn tạo bản ghi quá trình xử lý. "
            + "Không tìm thấy role gate riêng trong CapNhatVanBanDen.java thật cho action này nên chỉ "
            + "yêu cầu đã đăng nhập — xem javadoc CapNhatPhoiHopUseCase")
    public ResponseEntity<ApiResponse<UUID>> capNhatPhoiHop(@Valid @RequestBody CapNhatPhoiHopRequest request) {
        return created(capNhatPhoiHopUseCase.execute(request));
    }

    @PostMapping("/CapNhatDaXemDeBiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã xem để biết (bổ sung 15/09/2026, tương thích 1 phần "
        + "qlvb/vbden/CapNhatVanBanDen?action=capnhatdaxemdebiet)",
        description = "Người 'xem để biết' báo đã xem xong — loại actingUsername khỏi user_to_read, "
            + "thêm vào user_to_readed (khoá đúng view daxemdebiet). Không làm gì thêm nếu "
            + "user_to_read đang rỗng (đúng Domino thật) nhưng vẫn tạo bản ghi quá trình xử lý. Không "
            + "tìm thấy role gate riêng trong CapNhatVanBanDen.java thật cho action này nên chỉ yêu "
            + "cầu đã đăng nhập — xem javadoc CapNhatDaXemDeBietUseCase")
    public ResponseEntity<ApiResponse<UUID>> capNhatDaXemDeBiet(
        @Valid @RequestBody CapNhatDaXemDeBietRequest request) {
        return created(capNhatDaXemDeBietUseCase.execute(request));
    }

    @PostMapping("/TheoDoiNhiemVu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Theo dõi / Bỏ theo dõi văn bản đến (N20, bổ sung 07/09/2026, tương thích 1 "
        + "phần qlvb/vbden/TheoDoiNhiemVu)",
        description = "action=theodoi: thêm identifier PHẲNG người thao tác vào field tasks trên văn "
            + "bản gốc (đánh dấu 'cần theo dõi', đọc lại qua GetView viewname=vanbancantheodoi); "
            + "action=huytheodoi: loại khỏi tasks; action=chuyentheodoi: Domino gốc là hàm RỖNG, "
            + "không làm gì (port giữ nguyên no-op). Không tìm thấy role gate riêng trong "
            + "TheoDoiNhiemVu.java thật nên chỉ yêu cầu đã đăng nhập — xem javadoc TheoDoiNhiemVuUseCase")
    public ResponseEntity<ApiResponse<UUID>> theoDoiNhiemVu(@Valid @RequestBody TheoDoiNhiemVuRequest request) {
        return created(theoDoiNhiemVuUseCase.execute(request));
    }

    // Bugfix 16/09/2026 (nguoi dung bao "port lan truoc goi NHAM TheoDoiNhiemVu cho route
    // vbden/xuly/theodoi -> Bo theo doi bao thanh cong nhung khong mat khoi danh sach") - xem javadoc
    // TheoDoiVanBanDenUseCase ve su khac biet voi TheoDoiNhiemVuUseCase (field user_track/user_tracked,
    // KHONG PHAI field tasks).
    @PostMapping("/TheoDoiVanBan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Theo dõi lại / Bỏ theo dõi văn bản đến (route vbden/xuly/theodoi, tương "
        + "thích 1 phần qlvb/vbden/CapNhatVanBanDen?action=huytheodoi|theodoilai)",
        description = "action=huytheodoi: CHỈ chạy khi user_track hiện KHÔNG rỗng - loại username "
            + "người thao tác khỏi user_track, thêm vào user_tracked (khoá đúng view datheodoi). "
            + "action=theodoilai: CHỈ chạy khi user_tracked hiện KHÔNG rỗng - thêm username vào "
            + "user_track VÀ loại khỏi user_tracked (đính chính 16/09/2026, khác Domino gốc CÓ CHỦ "
            + "ĐÍCH — văn bản không còn nằm ở cả 2 tab cùng lúc). KHÁC HẲN TheoDoiNhiemVu (field tasks, "
            + "khoá identifier - dùng cho tab 'Văn bản cần theo dõi')")
    public ResponseEntity<ApiResponse<UUID>> theoDoiVanBan(@Valid @RequestBody TheoDoiVanBanDenRequest request) {
        return created(theoDoiVanBanDenUseCase.execute(request));
    }

    @PostMapping("/GuiXinYKien")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi xin ý kiến TVUB/TVNB từ văn bản đi (N08, tương thích qlvb/vbden/GuiXinYKien + qlvb/vbden/GuiXinYKienNoiBo)",
        description = "Tạo/cập nhật 'văn bản đến giả' (document_type=vbden_xinykien) từ danh sách "
            + "người nhận ĐÃ CÓ SẴN trên chính document vbdi (field list_tvub_obj/list_tvnb_obj, tuỳ "
            + "cờ noibo) — payload chỉ cần idvbdi, không cần gửi lại danh sách người nhận. Không tìm "
            + "thấy role gate riêng trong GuiXinYKien.java thật nên chỉ yêu cầu đã đăng nhập (không "
            + "suy diễn quyền mới, cùng quyết định như GuiSangVBCDDH)")
    public ResponseEntity<ApiResponse<UUID>> guiXinYKien(@Valid @RequestBody GuiXinYKienRequest request) {
        return created(guiXinYKienUseCase.execute(request));
    }

    // 18/09/2026 (cutover "Gui xin y kien tu vbden" - VBDenButtonActionXuLy.jsx#handleXinYKien,
    // truoc do goi Domino truc tiep) - xem javadoc GuiXinYKienTuVBdenUseCase.
    @PostMapping("/GuiXinYKienTuVBden")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi xin ý kiến TVNB từ văn bản đến (bổ sung 18/09/2026, tương thích "
        + "qlvb/vbden/GuiXinYKienTuVBden)",
        description = "Tạo/cập nhật 'văn bản đến giả' (document_type=vbden_xinykien) từ danh sách "
            + "người nhận FE gửi kèm (khác GuiXinYKien — nguồn vbdi tự đọc list_tvub_obj có sẵn trên "
            + "document, ở đây nguồn là vbden và danh sách đến từ chính request). Không tìm thấy role "
            + "gate riêng trong GuiXinYKienTuVBden.java thật nên chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> guiXinYKienTuVBden(
            @Valid @RequestBody GuiXinYKienTuVBdenRequest request) {
        return created(guiXinYKienTuVBdenUseCase.execute(request));
    }

    @PostMapping("/SoLuongCanXuLy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Số lượng văn bản/hồ sơ cần xử lý (badge menu, tương thích "
        + "qlvb/vbden/SoLuongCanXuLy)",
        description = "Số liệu CÁ NHÂN của người dùng đang đăng nhập trên 5 nhóm (vbden/duthao/"
            + "hosocv/phieutrinhtd/vbdi) — không nhận tham số (username lấy từ token đăng nhập, "
            + "action reset/update của Domino gốc không còn ý nghĩa vì không dùng cache). Trả THẲNG "
            + "1 object phẳng (khác Domino trả mảng 1-3 object) — CHƯA port: số liệu cấp cơ quan/"
            + "nhiệm vụ chỉ đạo điều hành (cần role/menu_vbcddh của user) — xem javadoc "
            + "GetSoLuongCanXuLyUseCase")
    public ResponseEntity<ApiResponse<Map<String, Object>>> soLuongCanXuLy() {
        return ok(getSoLuongCanXuLyUseCase.execute());
    }

    @PostMapping("/BaoCaoTheoPhong")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Báo cáo tổng hợp theo đơn vị - cá nhân (thay qlvb/vbcddh/danhsachdonvithuchien "
        + "+ qlvb/vbden/TimKiemVanBanDen)",
        description = "Trả {unit: cây tổ chức shape Domino (nhân sự gom vào node form=nhansu), data: văn bản "
            + "đến trong phạm vi tổ chức (view toanbovanban, lọc search.received_date), total, truncated}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> baoCaoTheoPhong(
            @RequestBody BaoCaoTheoPhongVbdenRequest request) {
        return ok(baoCaoTheoPhongVbdenUseCase.execute(request));
    }
}
