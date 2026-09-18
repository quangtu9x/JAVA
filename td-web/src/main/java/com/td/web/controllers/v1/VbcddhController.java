package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.vbcddh.CapNhatTienDoRequest;
import com.td.application.vbcddh.CapNhatTienDoUseCase;
import com.td.application.vbcddh.NhacViecResponseDto;
import com.td.application.vbcddh.NhacViecUseCase;
import com.td.application.vbcddh.NhiemVuThongKeItemDto;
import com.td.application.vbcddh.PhanCongNhiemVuRequest;
import com.td.application.vbcddh.PhanCongNhiemVuUseCase;
import com.td.application.vbcddh.ThongKeNhiemVuRequest;
import com.td.application.vbcddh.ThongKeNhiemVuUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.infrastructure.security.QlvbRoles;
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
 * BL-250 (VBCDDH Core): Phân công nhiệm vụ / Cập nhật tiến độ / Thống kê nhiệm vụ / Nhắc việc của
 * văn bản chỉ đạo điều hành — tương thích API legacy {@code qlvb/vbcddh/*} (nơi có bằng chứng URL
 * thật — {@code MappingVBCDDH.java}). "Tạo văn bản CĐĐH" (DoD 1) KHÔNG có endpoint riêng ở đây — đã
 * được đáp ứng đầy đủ bởi hạ tầng Dynamic Data Engine chung
 * ({@code POST /api/v1/documents/add?documentType=vbcddh}, BL-101), đúng như Domino thật (văn bản
 * CĐĐH cũng dùng {@code qlvb/add} tổng quát, không có servlet riêng cho việc tạo văn bản).
 */
@RestController
@RequestMapping("/api/v1/vbcddh")
@RequiredArgsConstructor
@Validated
@Tag(name = "VBCDDH", description = "Văn bản chỉ đạo điều hành — Core (BL-250), tương thích qlvb/vbcddh/*")
public class VbcddhController extends BaseController {

    private static final String DB_TYPE = "vbcddh";

    // SEC-01: role tìm thấy trực tiếp gate nút "Giao chuyên viên" (hành động phân công) trong FE
    // thật (docs/frontend/08-vbcddh.md mục "Quyền" — QPhanPhoi/QPhanCong, cùng họ role đã dùng cho
    // ChuyenXuLy vbden ở BL-211/SEC-01) — không suy diễn role mới, tái dùng nguyên các hằng đã có.
    private static final String PHAN_CONG_ROLES =
        "hasAnyRole('ADMIN', '" + QlvbRoles.PHAN_CONG + "', '" + QlvbRoles.PHAN_PHOI + "', '"
            + QlvbRoles.QUAN_TRI + "')";

    private final PhanCongNhiemVuUseCase phanCongNhiemVuUseCase;
    private final CapNhatTienDoUseCase capNhatTienDoUseCase;
    private final ThongKeNhiemVuUseCase thongKeNhiemVuUseCase;
    private final NhacViecUseCase nhacViecUseCase;
    private final ViewQueryService viewQueryService;

    @PostMapping("/PhanCongNhiemVu")
    @PreAuthorize(PHAN_CONG_ROLES)
    @Operation(summary = "Phân công nhiệm vụ cho đơn vị (BL-250, tương thích qlvb/vbcddh/phancongnhiemvu)",
        description = "Tạo 1 bản ghi nhiệm vụ (documentType=vbcddh, form=nhiemvu) cho mỗi đơn vị "
            + "trong assignments, liên kết idVanBan về văn bản chỉ đạo gốc — rồi tính lại tinhtrang "
            + "tổng hợp trên văn bản gốc (tương đương CapNhatTinhTrangVBCD.java thật)")
    public ResponseEntity<ApiResponse<List<UUID>>> phanCongNhiemVu(@Valid @RequestBody PhanCongNhiemVuRequest request) {
        return created(phanCongNhiemVuUseCase.execute(request));
    }

    @PostMapping("/CapNhatTienDo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật tiến độ thực hiện nhiệm vụ (BL-250)",
        description = "action=capnhattiendo (mặc định, chỉ ghi ý kiến) hoặc action=capnhatketquaxuly "
            + "(kết thúc, set tinhtrang=\"Đã thực hiện\" trên nhiệm vụ + rollup lên văn bản gốc). "
            + "Tạo document con documentType=vbcddh_res (tương đương LuuVet() trong ChuyenXuLyVBCDDH.java "
            + "thật — form=tiendonhiemvu/ketquaxulynhiemvu). Không có bằng chứng role gate riêng trong "
            + "source thật cho hành động này (chỉ có ràng buộc quan hệ dữ liệu phía FE) nên chỉ yêu cầu "
            + "đã đăng nhập, không suy diễn role mới")
    public ResponseEntity<ApiResponse<UUID>> capNhatTienDo(@Valid @RequestBody CapNhatTienDoRequest request) {
        return created(capNhatTienDoUseCase.execute(request));
    }

    @PostMapping("/ThongKeNhiemVu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thống kê nhiệm vụ (BL-250, tương thích qlvb/vbcddh/thongkenhiemvu)",
        description = "6 tổ hợp cố định (tinhtrang × quahan) theo đơn vị (identifiers) hoặc người chủ "
            + "trì (nguoiList, ưu tiên hơn identifiers nếu có), lọc thêm theo khoảng ngày tuNgay/denNgay "
            + "(create_date, định dạng ISO yyyy-MM-dd) — đúng logic đếm thật ThongKeNhiemVu.java. Không "
            + "có bằng chứng role gate trong source thật nên chỉ yêu cầu đã đăng nhập")
    public ResponseEntity<ApiResponse<Map<String, List<NhiemVuThongKeItemDto>>>> thongKeNhiemVu(
            @RequestBody ThongKeNhiemVuRequest request) {
        return ok(thongKeNhiemVuUseCase.execute(request));
    }

    @PostMapping("/NhacViec")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Nhắc việc tự động (BL-250, tương thích qlvb/vbcddh/nhacviec)",
        description = "2 nhóm (đang thực hiện/đã hoàn thành) theo đơn vị của người dùng hiện tại — "
            + "\"Sắp đến hạn\" = hanxuly_date trong [hôm nay, ngày mai], đúng ngưỡng thật NhacViec.java. "
            + "Đơn giản hoá: dùng organizationId của người dùng làm khoá tra cứu thay vì cơ chế phân "
            + "cấp identifiers/level đầy đủ của Domino (model dữ liệu hiện tại không có field tương "
            + "đương) — xem Javadoc NhacViecUseCase")
    public ResponseEntity<ApiResponse<NhacViecResponseDto>> nhacViec() {
        return ok(nhacViecUseCase.execute());
    }

    // Bugfix 16/09/2026 (yêu cầu người dùng: tab "Danh sách nhiệm vụ" ở chi tiết văn bản đến — port
    // sang Java route qlvb/view/query?databasetype=vbcddh, viewname vwNhiem_Vu_By_Idvanban).
    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách nhiệm vụ theo view (BL-250, tương thích qlvb/view/query?databasetype=vbcddh)",
        description = "Uỷ quyền cho ViewQueryService (BL-102) — vwNhiem_Vu_By_Idvanban (bảng phân "
            + "công): khoá là UNION 4 field idvanban/idvbdi/idvbden/idduthao (nhiệm vụ có thể phát "
            + "sinh từ văn bản qlvb gốc, văn bản đi, văn bản đến, hoặc dự thảo)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }
}
