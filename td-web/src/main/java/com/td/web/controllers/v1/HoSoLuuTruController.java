package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.hosoluutru.AddHoSoLuuTruDynamicDocumentUseCase;
import com.td.application.hosoluutru.CapNhatHoSoLuuTruUseCase;
import com.td.application.hosoluutru.ChuyenLuuTruLichSuUseCase;
import com.td.application.hosoluutru.ChuyenLuuTruNoiBoUseCase;
import com.td.application.hosoluutru.DangKyMuonTaiLieuRequest;
import com.td.application.hosoluutru.DangKyMuonTaiLieuUseCase;
import com.td.application.hosoluutru.DeleteHoSoLuuTruUseCase;
import com.td.application.hosoluutru.DuyetPhieuYeuCauRequest;
import com.td.application.hosoluutru.DuyetPhieuYeuCauUseCase;
import com.td.application.hosoluutru.GetDanhMucLuuTruListUseCase;
import com.td.application.hosoluutru.GetHoSoLuuTruChiTietRequest;
import com.td.application.hosoluutru.GetHoSoLuuTruChiTietUseCase;
import com.td.application.hosoluutru.HoSoLuuTruLifecycleActionRequest;
import com.td.application.hosoluutru.LuuDanhMucLuuTruRequest;
import com.td.application.hosoluutru.LuuDanhMucLuuTruUseCase;
import com.td.application.hosoluutru.LuuHoSoLuuTruRequest;
import com.td.application.hosoluutru.PhieuYeuCauActionRequest;
import com.td.application.hosoluutru.RestoreHoSoLuuTruUseCase;
import com.td.application.hosoluutru.ThemVanBanLuuTruRequest;
import com.td.application.hosoluutru.ThemVanBanLuuTruUseCase;
import com.td.application.hosoluutru.ThuHoiHoSoDaChoMuonUseCase;
import com.td.application.hosoluutru.ThuHoiLuuTruLichSuUseCase;
import com.td.application.hosoluutru.ThuHoiYeuCauMuonUseCase;
import com.td.application.hosoluutru.TiepNhanHoSoLuuTruRequest;
import com.td.application.hosoluutru.TiepNhanHoSoLuuTruUseCase;
import com.td.application.hosoluutru.TraLaiHoSoLuuTruUseCase;
import com.td.application.hosoluutru.TraTaiLieuUseCase;
import com.td.application.hosoluutru.XoaDanhMucLuuTruUseCase;
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
 * Port module "Hồ sơ lưu trữ" (hosoluutru) sang Java —
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md},
 * {@code docs/analysis/17-checklist-port-hosoluutru-domino-sang-java.md}. Tiếp nhận/từ chối hồ sơ
 * chuyển từ {@code hosocv}, CRUD hồ sơ/văn bản con/danh mục dùng chung, chuyển lưu trữ nội bộ +
 * lịch sử (LTLS chiều chờ duyệt/thu hồi), phiếu yêu cầu khai thác tài liệu (mượn/trả).
 *
 * <p><b>Quyền</b>: {@code isAuthenticated()} cho mọi endpoint (đúng bằng chứng — không có role check
 * BE thật ở Domino cho các action đặc thù; role {@code EDIT_ROLES}/{@code READ_ROLES} chỉ tồn tại ở
 * FE, không có đối chiếu BE — xem tài liệu 16 mục 2.3/8.3). Phân quyền dữ liệu (so
 * {@code identifier}) nằm ở tầng use-case/query, không phải annotation.</p>
 *
 * <p><b>Không làm</b> (xem tài liệu 17 Phần 2.3/4): {@code ketchuyenhosotuqlvb} (route không tồn tại
 * ở BE Domino), duyệt/từ chối LTLS ({@code chuyenluutrutinh} "1"/"3" — không có bằng chứng), tìm kiếm
 * nâng cao (Elasticsearch), CRUD chi tiết qua UI FE (chưa cutover {@code ChiTietPage.jsx}/
 * {@code PhieuYeuCauModal.jsx} — BE đã sẵn sàng).</p>
 */
@RestController
@RequestMapping("/api/v1/hosoluutru")
@RequiredArgsConstructor
@Validated
@Tag(name = "HoSoLuuTru", description = "Hồ sơ lưu trữ (port từ Domino)")
public class HoSoLuuTruController extends BaseController {

    private static final String DB_TYPE = "hosoluutru";

    private final AddHoSoLuuTruDynamicDocumentUseCase addHoSoLuuTruDynamicDocumentUseCase;
    private final GetHoSoLuuTruChiTietUseCase getHoSoLuuTruChiTietUseCase;
    private final CapNhatHoSoLuuTruUseCase capNhatHoSoLuuTruUseCase;
    private final DeleteHoSoLuuTruUseCase deleteHoSoLuuTruUseCase;
    private final RestoreHoSoLuuTruUseCase restoreHoSoLuuTruUseCase;
    private final TiepNhanHoSoLuuTruUseCase tiepNhanHoSoLuuTruUseCase;
    private final TraLaiHoSoLuuTruUseCase traLaiHoSoLuuTruUseCase;
    private final ChuyenLuuTruNoiBoUseCase chuyenLuuTruNoiBoUseCase;
    private final ChuyenLuuTruLichSuUseCase chuyenLuuTruLichSuUseCase;
    private final ThuHoiLuuTruLichSuUseCase thuHoiLuuTruLichSuUseCase;
    private final ThemVanBanLuuTruUseCase themVanBanLuuTruUseCase;
    private final LuuDanhMucLuuTruUseCase luuDanhMucLuuTruUseCase;
    private final GetDanhMucLuuTruListUseCase getDanhMucLuuTruListUseCase;
    private final XoaDanhMucLuuTruUseCase xoaDanhMucLuuTruUseCase;
    private final DangKyMuonTaiLieuUseCase dangKyMuonTaiLieuUseCase;
    private final DuyetPhieuYeuCauUseCase duyetPhieuYeuCauUseCase;
    private final ThuHoiYeuCauMuonUseCase thuHoiYeuCauMuonUseCase;
    private final TraTaiLieuUseCase traTaiLieuUseCase;
    private final ThuHoiHoSoDaChoMuonUseCase thuHoiHoSoDaChoMuonUseCase;
    private final ViewQueryService viewQueryService;

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết hồ sơ lưu trữ",
        description = "Tương thích qlvb/get?databasetype=hosoluutru&items=getall — trả toàn bộ field + "
            + "văn bản liên quan + phiếu yêu cầu + lịch sử LTLS + lịch sử chuyển lưu trữ (từ hosocv)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(
            @RequestBody GetHoSoLuuTruChiTietRequest request) {
        return ok(getHoSoLuuTruChiTietUseCase.execute(request));
    }

    @PostMapping("/LuuHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật hồ sơ lưu trữ đã tồn tại", description = "Merge dynamic_fields, không ghi đè")
    public ResponseEntity<ApiResponse<UUID>> luuHoSo(@Valid @RequestBody LuuHoSoLuuTruRequest request) {
        return ok(capNhatHoSoLuuTruUseCase.execute(request));
    }

    @PostMapping("/XoaHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xoá mềm hồ sơ lưu trữ (đệ quy văn bản con)")
    public ResponseEntity<ApiResponse<List<UUID>>> xoaHoSo(@RequestBody List<UUID> hosoLuuTruIds) {
        return ok(deleteHoSoLuuTruUseCase.execute(hosoLuuTruIds));
    }

    @PostMapping("/PhucHoiHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khôi phục hồ sơ lưu trữ từ thùng rác (đệ quy văn bản con)")
    public ResponseEntity<ApiResponse<List<UUID>>> phucHoiHoSo(@RequestBody List<UUID> hosoLuuTruIds) {
        return ok(restoreHoSoLuuTruUseCase.execute(hosoLuuTruIds));
    }

    @PostMapping("/TiepNhan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kho tiếp nhận hồ sơ chuyển từ hosocv",
        description = "form: hoso_chuaduyet -> hoso; cập nhật ngược storaged=\"1\" trên hosocv gốc")
    public ResponseEntity<ApiResponse<UUID>> tiepNhan(@Valid @RequestBody TiepNhanHoSoLuuTruRequest request) {
        return ok(tiepNhanHoSoLuuTruUseCase.execute(request));
    }

    @PostMapping("/TraLai")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kho từ chối tiếp nhận hồ sơ",
        description = "Xoá mềm bản ghi hosoluutru (đệ quy văn bản con); cập nhật ngược storaged=\"3\" trên hosocv gốc")
    public ResponseEntity<ApiResponse<UUID>> traLai(@Valid @RequestBody TiepNhanHoSoLuuTruRequest request) {
        return ok(traLaiHoSoLuuTruUseCase.execute(request));
    }

    @PostMapping("/ChuyenLuuTruNoiBo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển lưu trữ chính thức nội bộ", description = "chuyenluutru: \"0\" -> \"1\"")
    public ResponseEntity<ApiResponse<UUID>> chuyenLuuTruNoiBo(
            @Valid @RequestBody HoSoLuuTruLifecycleActionRequest request) {
        return ok(chuyenLuuTruNoiBoUseCase.execute(request));
    }

    @PostMapping("/ChuyenLuuTruLichSu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển lưu trữ lịch sử (LTLS) - chờ duyệt", description = "chuyenluutrutinh: \"\" -> \"2\"")
    public ResponseEntity<ApiResponse<UUID>> chuyenLuuTruLichSu(
            @Valid @RequestBody HoSoLuuTruLifecycleActionRequest request) {
        return ok(chuyenLuuTruLichSuUseCase.execute(request));
    }

    @PostMapping("/ThuHoiLuuTruLichSu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thu hồi yêu cầu chuyển lưu trữ lịch sử", description = "chuyenluutrutinh: \"2\" -> \"0\"")
    public ResponseEntity<ApiResponse<UUID>> thuHoiLuuTruLichSu(
            @Valid @RequestBody HoSoLuuTruLifecycleActionRequest request) {
        return ok(thuHoiLuuTruLichSuUseCase.execute(request));
    }

    @PostMapping("/ThemVanBan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thêm/sửa thủ công 1 văn bản gắn vào hồ sơ lưu trữ")
    public ResponseEntity<ApiResponse<UUID>> themVanBan(@Valid @RequestBody ThemVanBanLuuTruRequest request) {
        return ok(themVanBanLuuTruUseCase.execute(request));
    }

    @PostMapping("/DanhMuc/Luu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo/sửa 1 bản ghi danh mục dùng chung (kyhieuhoso/chatluong/loaihinhtailieu)")
    public ResponseEntity<ApiResponse<UUID>> luuDanhMuc(@Valid @RequestBody LuuDanhMucLuuTruRequest request) {
        return ok(luuDanhMucLuuTruUseCase.execute(request));
    }

    @PostMapping("/DanhMuc/GetList")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách 1 loại danh mục dùng chung")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDanhMucList(
            @RequestParam String documentType) {
        return ok(getDanhMucLuuTruListUseCase.execute(documentType));
    }

    @PostMapping("/DanhMuc/Xoa")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xoá vĩnh viễn 1 bản ghi danh mục dùng chung",
        description = "Đúng bằng chứng FE (deleteoption=\"empty\") — hard delete, KHÁC xoá mềm hồ sơ/văn bản/phiếu yêu cầu")
    public ResponseEntity<ApiResponse<UUID>> xoaDanhMuc(@RequestParam UUID id) {
        return ok(xoaDanhMucLuuTruUseCase.execute(id));
    }

    @PostMapping("/DangKyMuon")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đăng ký mượn tài liệu lưu trữ", description = "Tạo phiếu yêu cầu (status=0) + user_dangkymuon")
    public ResponseEntity<ApiResponse<UUID>> dangKyMuon(@Valid @RequestBody DangKyMuonTaiLieuRequest request) {
        return ok(dangKyMuonTaiLieuUseCase.execute(request));
    }

    @PostMapping("/DuyetPhieuYeuCau")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kho duyệt/từ chối phiếu yêu cầu mượn", description = "status: \"0\" -> \"1\"/\"3\"")
    public ResponseEntity<ApiResponse<UUID>> duyetPhieuYeuCau(@Valid @RequestBody DuyetPhieuYeuCauRequest request) {
        return ok(duyetPhieuYeuCauUseCase.execute(request));
    }

    @PostMapping("/ThuHoiYeuCauMuon")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người mượn tự thu hồi yêu cầu đang chờ giải quyết", description = "status: \"0\" -> \"2\"")
    public ResponseEntity<ApiResponse<UUID>> thuHoiYeuCauMuon(@Valid @RequestBody PhieuYeuCauActionRequest request) {
        return ok(thuHoiYeuCauMuonUseCase.execute(request));
    }

    @PostMapping("/TraTaiLieu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người mượn tự trả tài liệu", description = "status1: rỗng/\"0\" -> \"1\"")
    public ResponseEntity<ApiResponse<UUID>> traTaiLieu(@Valid @RequestBody PhieuYeuCauActionRequest request) {
        return ok(traTaiLieuUseCase.execute(request));
    }

    @PostMapping("/ThuHoiHoSoDaChoMuon")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kho chủ động thu hồi hồ sơ đã cho mượn", description = "status1: rỗng/\"0\" -> \"2\"")
    public ResponseEntity<ApiResponse<UUID>> thuHoiHoSoDaChoMuon(@Valid @RequestBody PhieuYeuCauActionRequest request) {
        return ok(thuHoiHoSoDaChoMuonUseCase.execute(request));
    }

    @PostMapping("/TaoHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo trực tiếp 1 hồ sơ lưu trữ (CRUD độc lập, không qua hosocv)",
        description = "form-data field động + date_fields/number_fields/richtext_fields (BL-101)")
    public ResponseEntity<ApiResponse<UUID>> taoHoSo(@RequestBody AddDynamicDocumentRequest request) {
        return created(addHoSoLuuTruDynamicDocumentUseCase.execute(request));
    }

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách hồ sơ lưu trữ theo view",
        description = "Uỷ quyền ViewQueryService — xem hằng số VIEW_HOSOLUUTRU_*")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }
}
