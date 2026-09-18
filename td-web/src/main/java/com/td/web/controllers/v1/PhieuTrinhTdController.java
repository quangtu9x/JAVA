package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.phieutrinhtd.AddPhieuTrinhTdDynamicDocumentUseCase;
import com.td.application.phieutrinhtd.CapNhatPhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.ChuyenTrangThaiPhieuTrinhTdRequest;
import com.td.application.phieutrinhtd.ChuyenTrangThaiPhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.DeletePhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.GetPhieuTrinhTdChiTietRequest;
import com.td.application.phieutrinhtd.GetPhieuTrinhTdChiTietUseCase;
import com.td.application.phieutrinhtd.LuuPhieuTrinhTdRequest;
import com.td.application.phieutrinhtd.RestorePhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.TrinhThamKhaoPhieuTrinhTdRequest;
import com.td.application.phieutrinhtd.TrinhThamKhaoPhieuTrinhTdUseCase;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Phiếu trình thẩm định" (phieutrinhtd) sang Java —
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md},
 * {@code docs/analysis/21-checklist-port-phieutrinh-domino-sang-java.md}. CRUD + chuyển trạng thái
 * (trình tham khảo/trình duyệt/trình thông qua/thu hồi...) cho form Domino {@code submission_dossier}.
 *
 * <p><b>Quyền</b>: {@code isAuthenticated()} cho mọi endpoint — không có bằng chứng role gate riêng ở
 * Domino cho module này (đúng lý do đã áp dụng cho {@code hosoluutru}).</p>
 *
 * <p><b>Không làm</b> (xem checklist 21 Phần 1/4): module Domino "phieutrinh" (không TD, khác hẳn
 * "phieutrinhtd") — không có menu/FE/DXL nào để đối chiếu, không port. FE cutover chưa thực hiện trong
 * lô này (giữ nguyên Domino, xem checklist Nhóm 3).</p>
 */
@RestController
@RequestMapping("/api/v1/phieutrinhtd")
@RequiredArgsConstructor
@Validated
@Tag(name = "PhieuTrinhTD", description = "Phiếu trình thẩm định (port từ Domino)")
public class PhieuTrinhTdController extends BaseController {

    private static final String DB_TYPE = "phieutrinhtd";

    private final AddPhieuTrinhTdDynamicDocumentUseCase addPhieuTrinhTdDynamicDocumentUseCase;
    private final GetPhieuTrinhTdChiTietUseCase getPhieuTrinhTdChiTietUseCase;
    private final CapNhatPhieuTrinhTdUseCase capNhatPhieuTrinhTdUseCase;
    private final DeletePhieuTrinhTdUseCase deletePhieuTrinhTdUseCase;
    private final RestorePhieuTrinhTdUseCase restorePhieuTrinhTdUseCase;
    private final TrinhThamKhaoPhieuTrinhTdUseCase trinhThamKhaoPhieuTrinhTdUseCase;
    private final ChuyenTrangThaiPhieuTrinhTdUseCase chuyenTrangThaiPhieuTrinhTdUseCase;
    private final ViewQueryService viewQueryService;

    @PostMapping("/TaoPhieuTrinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo mới phiếu trình thẩm định",
        description = "Tương thích qlvb/add?databasetype=phieutrinhtd (không id) — mặc định status=\"0\" (Đang soạn thảo)")
    public ResponseEntity<ApiResponse<UUID>> taoPhieuTrinh(@RequestBody AddDynamicDocumentRequest request) {
        return created(addPhieuTrinhTdDynamicDocumentUseCase.execute(request));
    }

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết phiếu trình", description = "Tương thích qlvb/get?databasetype=phieutrinhtd — kèm lịch sử quatrinh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(
            @RequestBody GetPhieuTrinhTdChiTietRequest request) {
        return ok(getPhieuTrinhTdChiTietUseCase.execute(request));
    }

    @PostMapping("/LuuPhieuTrinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật phiếu trình đã tồn tại", description = "Merge dynamic_fields, không đổi status")
    public ResponseEntity<ApiResponse<UUID>> luuPhieuTrinh(@Valid @RequestBody LuuPhieuTrinhTdRequest request) {
        return ok(capNhatPhieuTrinhTdUseCase.execute(request));
    }

    @PostMapping("/XoaPhieuTrinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xoá mềm phiếu trình")
    public ResponseEntity<ApiResponse<List<UUID>>> xoaPhieuTrinh(@RequestBody List<UUID> phieuTrinhIds) {
        return ok(deletePhieuTrinhTdUseCase.execute(phieuTrinhIds));
    }

    @PostMapping("/PhucHoiPhieuTrinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khôi phục phiếu trình từ thùng rác")
    public ResponseEntity<ApiResponse<List<UUID>>> phucHoiPhieuTrinh(@RequestBody List<UUID> phieuTrinhIds) {
        return ok(restorePhieuTrinhTdUseCase.execute(phieuTrinhIds));
    }

    @PostMapping("/TrinhThamKhao")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trình tham khảo (xin ý kiến song song)",
        description = "KHÔNG đổi status chính — thêm người vào nguoithamkhaohide + ghi log quatrinh")
    public ResponseEntity<ApiResponse<UUID>> trinhThamKhao(
            @Valid @RequestBody TrinhThamKhaoPhieuTrinhTdRequest request) {
        return ok(trinhThamKhaoPhieuTrinhTdUseCase.execute(request));
    }

    @PostMapping("/ChuyenTrangThai")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển trạng thái phiếu trình (trình duyệt/trình thông qua/duyệt/không duyệt/thu hồi...)",
        description = "Dùng chung cho mọi target status — xem javadoc ChuyenTrangThaiPhieuTrinhTdUseCase")
    public ResponseEntity<ApiResponse<UUID>> chuyenTrangThai(
            @Valid @RequestBody ChuyenTrangThaiPhieuTrinhTdRequest request) {
        return ok(chuyenTrangThaiPhieuTrinhTdUseCase.execute(request));
    }

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách phiếu trình theo view",
        description = "Uỷ quyền ViewQueryService — xem hằng số VIEW_PHIEUTRINHTD_*")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }
}
