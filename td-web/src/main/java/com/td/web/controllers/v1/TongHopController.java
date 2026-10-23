package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.tonghop.GetProvinceDispatchDetailUseCase;
import com.td.application.tonghop.GetProvinceScopeUseCase;
import com.td.application.tonghop.ListProvinceTenantsUseCase;
import com.td.application.tonghop.ProvinceScope;
import com.td.application.tonghop.ProvinceScopePolicy;
import com.td.application.tonghop.GetProvinceStatisticsUseCase;
import com.td.application.tonghop.ProvinceDispatchSearchRequest;
import com.td.application.tonghop.ProvinceDispatchSearchResult;
import com.td.application.tonghop.ProvinceDispatchSummary;
import com.td.application.tonghop.ProvinceDocumentSearchRequest;
import com.td.application.tonghop.ProvinceDocumentSearchResult;
import com.td.application.tonghop.ProvinceStatisticsRequest;
import com.td.application.tonghop.ProvinceStatisticsResult;
import com.td.application.tonghop.ProvinceSyncService;
import com.td.application.tonghop.ResendProvinceDispatchRequest;
import com.td.application.tonghop.ResendProvinceDispatchUseCase;
import com.td.application.tonghop.SearchProvinceDispatchesUseCase;
import com.td.application.tonghop.SearchProvinceDocumentsUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tổng hợp toàn tỉnh (route FE {@code /tonghop}): thay các endpoint Domino {@code qlvb/danhmuc/TongHopChung},
 * {@code qlvb/tichhop/TimKiemToanTinh*}, {@code qlvb/tichhop/TheoDoiVanBanDiEs*}. Đọc kho tập trung
 * {@code province_*} (control-plane) — xem docs/analysis/26-tong-hop-toan-tinh-java.md.
 *
 * <p>Quyền: "Tổng hợp chung" giữ như bản cũ (mọi người dùng đăng nhập xem được số liệu đơn vị); tìm kiếm,
 * giám sát, gửi lại, đồng bộ chỉ quản trị ({@code ADMIN} = {@code access=Manager} ở FE) — bản cũ chỉ ẩn nút ở
 * FE, API không chặn.</p>
 *
 * <p>Phạm vi (22/10/2026, {@code ProvinceScopePolicy} — tdgateway/docs/24 §2.8): tài khoản tổng + ADMIN tenant cấp tỉnh
 * xem toàn tỉnh; ADMIN đơn vị chỉ thấy/gửi lại văn bản của tenant mình (áp trong use case); "Đồng bộ ngay" chỉ tài
 * khoản tổng (thao tác toàn hệ thống).</p>
 */
@RestController
@RequestMapping("/api/v1/tonghop")
@RequiredArgsConstructor
@Tag(name = "TongHop", description = "Tổng hợp, tìm kiếm, giám sát văn bản toàn tỉnh")
public class TongHopController extends BaseController {

    private static final String PROVINCE_ADMIN = "hasRole('ADMIN')";

    private final GetProvinceStatisticsUseCase getProvinceStatisticsUseCase;
    private final SearchProvinceDocumentsUseCase searchProvinceDocumentsUseCase;
    private final SearchProvinceDispatchesUseCase searchProvinceDispatchesUseCase;
    private final GetProvinceDispatchDetailUseCase getProvinceDispatchDetailUseCase;
    private final ResendProvinceDispatchUseCase resendProvinceDispatchUseCase;
    private final ProvinceSyncService provinceSyncService;
    private final ProvinceScopePolicy provinceScopePolicy;
    private final GetProvinceScopeUseCase getProvinceScopeUseCase;
    private final ListProvinceTenantsUseCase listProvinceTenantsUseCase;

    @GetMapping("/PhamVi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Phạm vi dữ liệu của người dùng hiện tại: toàn tỉnh hay chỉ đơn vị (tenant) mình")
    public ResponseEntity<ApiResponse<ProvinceScope>> phamVi() {
        return respond(getProvinceScopeUseCase.execute());
    }

    @GetMapping("/DonVi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đơn vị (tenant) trong phạm vi của người dùng — cho ô lọc theo đơn vị")
    public ResponseEntity<ApiResponse<List<ListProvinceTenantsUseCase.TenantOption>>> donVi() {
        return respond(listProvinceTenantsUseCase.execute());
    }

    @PostMapping("/ThongKe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tổng hợp chung: số liệu vbden/vbdi theo đơn vị, tổng, xu hướng theo tháng")
    public ResponseEntity<ApiResponse<ProvinceStatisticsResult>> thongKe(
            @RequestBody(required = false) ProvinceStatisticsRequest request) {
        return respond(getProvinceStatisticsUseCase.execute(request));
    }

    @PostMapping("/TimKiem")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Tìm kiếm văn bản đến/đi toàn tỉnh (phân trang, bộ đếm theo bộ lọc, export=true để xuất)")
    public ResponseEntity<ApiResponse<ProvinceDocumentSearchResult>> timKiem(
            @RequestBody(required = false) ProvinceDocumentSearchRequest request) {
        return respond(searchProvinceDocumentsUseCase.execute(request));
    }

    @PostMapping("/GiamSatVanBanDi")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Giám sát gửi văn bản đi tập trung: danh sách văn bản đi + tiến độ nơi nhận")
    public ResponseEntity<ApiResponse<ProvinceDispatchSearchResult>> giamSat(
            @RequestBody(required = false) ProvinceDispatchSearchRequest request) {
        return respond(searchProvinceDispatchesUseCase.execute(request));
    }

    @PostMapping("/GiamSatVanBanDi/ThongKe")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Giám sát gửi văn bản đi: thẻ KPI + top đơn vị phát hành/nhận chậm (cùng bộ lọc danh sách)")
    public ResponseEntity<ApiResponse<ProvinceDispatchSummary>> giamSatThongKe(
            @RequestBody(required = false) ProvinceDispatchSearchRequest request) {
        return respond(searchProvinceDispatchesUseCase.summary(request));
    }

    @GetMapping("/GiamSatVanBanDi/ChiTiet")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Chi tiết 1 văn bản đi: từng nơi nhận + dòng thời gian sự kiện")
    public ResponseEntity<ApiResponse<GetProvinceDispatchDetailUseCase.Detail>> chiTiet(
            @RequestParam String tenantId, @RequestParam UUID documentId) {
        return respond(getProvinceDispatchDetailUseCase.execute(tenantId, documentId));
    }

    @PostMapping("/GiamSatVanBanDi/GuiLai")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Gửi lại văn bản đi tới các nơi nhận đã chọn (chạy luồng Gửi lại chuẩn trong tenant sở hữu)")
    public ResponseEntity<ApiResponse<Integer>> guiLai(@RequestBody ResendProvinceDispatchRequest request) {
        return respond(resendProvinceDispatchUseCase.execute(request));
    }

    @GetMapping("/DongBo")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Trạng thái đồng bộ kho tổng hợp theo từng tenant")
    public ResponseEntity<ApiResponse<ProvinceSyncService.SyncStatus>> trangThaiDongBo() {
        return ok(provinceSyncService.status());
    }

    @PostMapping("/DongBo")
    @PreAuthorize(PROVINCE_ADMIN)
    @Operation(summary = "Đồng bộ ngay (full=true: quét lại toàn bộ)")
    public ResponseEntity<ApiResponse<ProvinceSyncService.SyncStatus>> dongBo(
            @RequestParam(defaultValue = "false") boolean full) {
        if (!provinceScopePolicy.current().isSuperAccount()) {
            return badRequest(Result.<ProvinceSyncService.SyncStatus>failure(
                "Chỉ tài khoản quản trị tổng được chạy đồng bộ kho tổng hợp"));
        }
        return ok(provinceSyncService.syncNow(full));
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(Result<T> result) {
        return result.isSuccess() ? ok(result) : badRequest(result);
    }
}
