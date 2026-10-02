package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.hosocv.CapNhatHoSoUseCase;
import com.td.application.hosocv.ChuyenLuuTruHoSoRequest;
import com.td.application.hosocv.ChuyenLuuTruHoSoUseCase;
import com.td.application.hosocv.DeleteHoSoUseCase;
import com.td.application.hosocv.DownloadFilesHoSoCVRequest;
import com.td.application.hosocv.DownloadFilesHoSoCVUseCase;
import com.td.application.hosocv.DuyetHoSoRequest;
import com.td.application.hosocv.DuyetHoSoUseCase;
import com.td.application.hosocv.GanKemVanBanRequest;
import com.td.application.hosocv.GanKemVanBanUseCase;
import com.td.application.hosocv.GetHoSoChiTietRequest;
import com.td.application.hosocv.GetHoSoChiTietUseCase;
import com.td.application.hosocv.GetTokenLgspUseCase;
import com.td.application.hosocv.GopYPhoiHopRequest;
import com.td.application.hosocv.GopYPhoiHopUseCase;
import com.td.application.hosocv.HoSoFileGroupDto;
import com.td.application.hosocv.KetThucHoSoRequest;
import com.td.application.hosocv.KetThucHoSoUseCase;
import com.td.application.hosocv.LuuHoSoRequest;
import com.td.application.hosocv.LuuVanBanKhacRequest;
import com.td.application.hosocv.LuuVanBanKhacUseCase;
import com.td.application.hosocv.RestoreHoSoUseCase;
import com.td.application.hosocv.TaoHoSoCVRequest;
import com.td.application.hosocv.TaoHoSoCVUseCase;
import com.td.application.hosocv.TrinhDuyetHoSoRequest;
import com.td.application.hosocv.TrinhDuyetHoSoUseCase;
import com.td.application.hosocv.XuLyLaiHoSoRequest;
import com.td.application.hosocv.XemVanBanKhacUseCase;
import com.td.application.hosocv.XoaVanBanKhacUseCase;
import com.td.application.hosocv.XuLyLaiHoSoUseCase;
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
 * BL-240 (HoSoCV Core) + mở rộng theo yêu cầu người dùng ("port các chức năng sang JAVA... tạo
 * bảng mới, sử dụng jsonb (dynamic field) như các module đã thực hiện"): Tạo/xem/sửa/xoá/khôi phục
 * hồ sơ công việc, gắn văn bản đến-đi-dự thảo vào hồ sơ, danh sách theo ~26 view, vòng đời
 * trình duyệt/duyệt/chuyển lưu trữ/kết thúc/xử lý lại, góp ý/phối hợp, download file, token LGSP.
 *
 * <p><b>Hạ tầng dữ liệu (mở rộng)</b>: từ chỗ dùng bảng {@code documents} dùng chung (thiết kế BAN
 * ĐẦU của BL-240), giờ hồ sơ công việc có bảng vật lý ĐỘC LẬP {@code hosocv_documents} (đúng tiền
 * lệ {@code duthao_documents}/{@code vbdi_documents}) + bảng vết xử lý riêng
 * {@code hosocv_process_documents} — xem javadoc {@code HoSoCVDocument}/{@code HoSoCVProcessDocument}.
 * Đã sửa bug tra sai bảng trong {@link GanKemVanBanUseCase} (xem javadoc lớp đó).
 *
 * <p>Quyền: đúng bằng chứng thật ({@code docs/frontend/07-hosocv-hosoluutru.md} mục "Quyền") —
 * KHÔNG có kiểm tra role dạng {@code CheckRole} ở tầng module {@code hosocv} (khác hẳn
 * {@code hosoluutru} — module RIÊNG, ngoài phạm vi BL-240). Phân quyền thao tác trên từng hồ sơ
 * hoàn toàn dựa vào so sánh identity (data-level ownership: {@code creator}/
 * {@code process_handler}/{@code approved_by}/{@code coordinator}), không phải role hệ thống — nên
 * toàn bộ endpoint chỉ yêu cầu đã đăng nhập, đúng nguyên tắc SEC-01 "không suy diễn quyền khi không
 * có bằng chứng".
 *
 * <p><b>Cập nhật 08/09/2026</b>: {@code ChuyenLuuTru} giờ nối đầy đủ với module {@code hosoluutru}
 * đã port (xem {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md}) — không còn
 * dừng ở {@code storaged="2"}, xem javadoc {@code ChuyenLuuTruHoSoUseCase}.</p>
 *
 * <p><b>Không làm</b> (ghi rõ, tránh hiểu nhầm khi review): tìm kiếm nâng cao (phụ thuộc
 * Elasticsearch), 3 actionName gắn văn bản thiếu bằng chứng ({@code ganvanbankhac}/
 * {@code xemvanbankhac}/{@code ganvanbandentuvbdi}), chiều gắn kết {@code duthao → hosocv} (thuộc
 * checklist duthao, không phải hosocv).
 */
@RestController
@RequestMapping("/api/v1/hosocv")
@RequiredArgsConstructor
@Validated
@Tag(name = "HoSoCV", description = "Hồ sơ công việc — Core (BL-240)")
public class HoSoCVController extends BaseController {

    private static final String DB_TYPE = "hosocv";

    private final TaoHoSoCVUseCase taoHoSoCVUseCase;
    private final GanKemVanBanUseCase ganKemVanBanUseCase;
    private final DownloadFilesHoSoCVUseCase downloadFilesHoSoCVUseCase;
    private final ViewQueryService viewQueryService;
    // Mo rong BL-240 (G1/G3/G4/G6/G8, xem docs/analysis/13-checklist-port-hosocv-domino-sang-java.md).
    private final GetHoSoChiTietUseCase getHoSoChiTietUseCase;
    private final CapNhatHoSoUseCase capNhatHoSoUseCase;
    private final DeleteHoSoUseCase deleteHoSoUseCase;
    private final RestoreHoSoUseCase restoreHoSoUseCase;
    private final TrinhDuyetHoSoUseCase trinhDuyetHoSoUseCase;
    private final DuyetHoSoUseCase duyetHoSoUseCase;
    private final ChuyenLuuTruHoSoUseCase chuyenLuuTruHoSoUseCase;
    private final KetThucHoSoUseCase ketThucHoSoUseCase;
    private final XuLyLaiHoSoUseCase xuLyLaiHoSoUseCase;
    private final GopYPhoiHopUseCase gopYPhoiHopUseCase;
    private final GetTokenLgspUseCase getTokenLgspUseCase;
    // 02/10/2026: "van ban khac" tab VAN BAN LIEN QUAN - xem javadoc LuuVanBanKhacUseCase.
    private final LuuVanBanKhacUseCase luuVanBanKhacUseCase;
    private final XemVanBanKhacUseCase xemVanBanKhacUseCase;
    private final XoaVanBanKhacUseCase xoaVanBanKhacUseCase;

    @PostMapping("/TaoHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo hồ sơ công việc (BL-240 DoD 1)",
        description = "form-data field động (title, category, first_topic, ...) + meta "
            + "date_fields/number_fields/richtext_fields (BL-101). Trạng thái khởi tạo "
            + "DANG_XU_LY (\"0\")")
    public ResponseEntity<ApiResponse<UUID>> taoHoSo(@RequestParam Map<String, String> params) {
        return created(taoHoSoCVUseCase.execute(buildTaoHoSoRequest(params)));
    }

    private TaoHoSoCVRequest buildTaoHoSoRequest(Map<String, String> params) {
        var request = new TaoHoSoCVRequest();
        request.setTitle(firstNonBlank(params.get("title"), params.get("subject")));
        request.setStatus(params.get("status"));
        request.setDateFields(params.get("date_fields"));
        request.setNumberFields(params.get("number_fields"));
        request.setRichtextFields(params.get("richtext_fields"));

        // Bugfix 24/08/2026 (đồng bộ VbdenController): "document_type" là field nghiệp vụ thật của
        // form hồ sơ công việc ("Loại văn bản" — Công văn/Báo cáo/...), khác hoàn toàn documentType/
        // DOCUMENT_TYPE ở tầng entity (hardcode "hosocv" trong TaoHoSoCVUseCase, không đọc từ
        // params) — không được strip khỏi fields động nữa.
        Map<String, String> fields = new java.util.LinkedHashMap<>(params);
        for (String metaKey : List.of(
                "title", "subject", "status", "date_fields", "number_fields", "richtext_fields")) {
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

    @PostMapping("/GanVanBan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gắn văn bản đến/đi/dự thảo vào hồ sơ (BL-240 DoD 2)",
        description = "actionName chọn cặp field (gankemduthao/ganvanbandi/ganvanbanphathanh/"
            + "ganvanbanden/ganvanbandenxuly/gankemphieutrinh), action=\"remove\" để gỡ (mặc định là "
            + "gắn thêm) — ghi CẢ 2 phía (văn bản + hồ sơ) cho cả gắn thêm lẫn gỡ (xem javadoc "
            + "GanKemVanBanUseCase, sửa 01/09/2026 theo đúng XuLyGanKemDuThaoVaoHoSoVC() thật)")
    public ResponseEntity<ApiResponse<UUID>> ganVanBan(@Valid @RequestBody GanKemVanBanRequest request) {
        return ok(ganKemVanBanUseCase.execute(request));
    }

    @PostMapping("/GetAllFile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download file từ hồ sơ (BL-240 DoD 4)",
        description = "Đúng cơ chế thật qlvb/getallfilehosocv (AttachmentController.doGetAllFileHSCV "
            + "-> TDAttachment.GetAttachmentByHSCV()) — trả về danh sách file (metadata) của từng "
            + "văn bản liên quan trong hồ sơ; tải nội dung file qua endpoint chung "
            + "GET /api/v1/documents/{documentId}/files/{fileId}/download")
    public ResponseEntity<ApiResponse<List<HoSoFileGroupDto>>> getAllFile(
            @RequestBody DownloadFilesHoSoCVRequest request) {
        return ok(downloadFilesHoSoCVUseCase.execute(request));
    }

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách hồ sơ công việc theo view (BL-240 DoD 3, mở rộng ~26 view)",
        description = "Uỷ quyền cho ViewQueryService (BL-102) — xem toàn bộ view đã port trong "
            + "javadoc ViewQueryService (hằng số VIEW_HOSOCV_*)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết hồ sơ công việc (G1)",
        description = "Tương thích qlvb/get?databasetype=hosocv&items=getall — trả toàn bộ field + "
            + "danh sách văn bản liên quan (van_ban_lien_quan) + lịch sử xử lý (lich_su_xu_ly)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(
            @RequestBody GetHoSoChiTietRequest request) {
        return ok(getHoSoChiTietUseCase.execute(request));
    }

    @PostMapping("/LuuHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật hồ sơ công việc đã tồn tại (G1)",
        description = "Tương thích 1 phần qlvb/add?id=...&databasetype=hosocv — merge dynamic_fields, "
            + "cũng dùng cho \"chọn danh mục hồ sơ\" (chondanhmuchoso, gửi field category/category_id)")
    public ResponseEntity<ApiResponse<UUID>> luuHoSo(@Valid @RequestBody LuuHoSoRequest request) {
        return ok(capNhatHoSoUseCase.execute(request));
    }

    @PostMapping("/XoaHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xoá mềm hồ sơ công việc (G6)",
        description = "Nhận danh sách id, xoá được nhiều hồ sơ 1 lần — KHÔNG kiểm tra phần tử con "
            + "(đúng hành vi thật, khác vbden/vbdi)")
    public ResponseEntity<ApiResponse<List<UUID>>> xoaHoSo(@RequestBody List<UUID> hosocvIds) {
        return ok(deleteHoSoUseCase.execute(hosocvIds));
    }

    @PostMapping("/PhucHoiHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khôi phục hồ sơ công việc từ thùng rác (G6)")
    public ResponseEntity<ApiResponse<List<UUID>>> phucHoiHoSo(@RequestBody List<UUID> hosocvIds) {
        return ok(restoreHoSoUseCase.execute(hosocvIds));
    }

    @PostMapping("/TrinhDuyet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trình lãnh đạo duyệt hồ sơ (G3)",
        description = "trinhlanhdaoduyet: \"\" → \"2\" (đang chờ duyệt)")
    public ResponseEntity<ApiResponse<UUID>> trinhDuyet(@Valid @RequestBody TrinhDuyetHoSoRequest request) {
        return ok(trinhDuyetHoSoUseCase.execute(request));
    }

    @PostMapping("/DuyetHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lãnh đạo duyệt/không duyệt hồ sơ đã trình (G3)",
        description = "trinhlanhdaoduyet: \"2\" → \"1\" (đã duyệt) hoặc \"3\" (không được duyệt); "
            + "yêu cầu hồ sơ đang ở trạng thái chờ duyệt")
    public ResponseEntity<ApiResponse<UUID>> duyetHoSo(@Valid @RequestBody DuyetHoSoRequest request) {
        return ok(duyetHoSoUseCase.execute(request));
    }

    @PostMapping("/ChuyenLuuTru")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển lưu trữ hồ sơ (G3)",
        description = "storaged: \"\"/\"0\" → \"2\" (đã chuyển, chờ kho tiếp nhận) — mở rộng 08/09/2026: "
            + "giờ tạo LUÔN bản ghi hosoluutru tương ứng (module đã port), yêu cầu thêm dmhsId (id danh "
            + "mục hồ sơ lưu trữ, records_storage_catalogs); trạng thái \"1\"/\"3\" tiếp tục do "
            + "hosoluutru xử lý (Tiếp nhận/Từ chối), xem javadoc ChuyenLuuTruHoSoUseCase")
    public ResponseEntity<ApiResponse<UUID>> chuyenLuuTru(@Valid @RequestBody ChuyenLuuTruHoSoRequest request) {
        return ok(chuyenLuuTruHoSoUseCase.execute(request));
    }

    @PostMapping("/KetThucHoSo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kết thúc hồ sơ (đơn/nhiều) (G3)",
        description = "status: \"0\" → \"1\" + end_date/summary — nhận danh sách id "
            + "(ketthuchoso/ketthucnhieuhoso cùng 1 luồng)")
    public ResponseEntity<ApiResponse<List<UUID>>> ketThucHoSo(@RequestBody KetThucHoSoRequest request) {
        return ok(ketThucHoSoUseCase.execute(request));
    }

    @PostMapping("/XuLyLai")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xử lý lại hồ sơ bị từ chối duyệt (đơn/nhiều) (G3/G9)",
        description = "trinhlanhdaoduyet: \"3\" → \"\" — nhận danh sách id "
            + "(xulylaihoso/xulylainhieuhoso cùng 1 luồng)")
    public ResponseEntity<ApiResponse<List<UUID>>> xuLyLai(@RequestBody XuLyLaiHoSoRequest request) {
        return ok(xuLyLaiHoSoUseCase.execute(request));
    }

    @PostMapping("/GopYPhoiHop")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi ý kiến góp ý/phối hợp vào hồ sơ (G4/G9)",
        description = "Gộp actionName gopy/phoihop — ghi vết hosocv_process_documents + đồng bộ "
            + "ngược coordinated/user_coordinated")
    public ResponseEntity<ApiResponse<UUID>> gopYPhoiHop(@Valid @RequestBody GopYPhoiHopRequest request) {
        return ok(gopYPhoiHopUseCase.execute(request));
    }

    @PostMapping("/LuuVanBanKhac")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thêm/sửa văn bản, tài liệu liên quan khác của hồ sơ",
        description = "Thay qlvb/add?databasetype=hosocv (form other_related_document). id rỗng = thêm "
            + "vào hồ sơ hosocvId; fields chỉ nhận code/promulgation_date/unit_publisher/signer/subject. "
            + "Tệp đính kèm tải lên sau qua /api/v1/documents/{id}/files")
    public ResponseEntity<ApiResponse<UUID>> luuVanBanKhac(@RequestBody LuuVanBanKhacRequest request) {
        return ok(luuVanBanKhacUseCase.execute(request));
    }

    @PostMapping("/XemVanBanKhac")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem 1 văn bản khác của hồ sơ (thay qlvb/get?databasetype=hosocv)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemVanBanKhac(@RequestBody GetHoSoChiTietRequest request) {
        return ok(xemVanBanKhacUseCase.execute(parseUuidOrNull(request.getId())));
    }

    @PostMapping("/XoaVanBanKhac")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xoá mềm văn bản khác khỏi hồ sơ (thay qlvb/delete)")
    public ResponseEntity<ApiResponse<List<UUID>>> xoaVanBanKhac(@RequestBody List<UUID> ids) {
        return ok(xoaVanBanKhacUseCase.execute(ids));
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @PostMapping("/GetTokenLgsp")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy token LGSP (G8)",
        description = "Yêu cầu cấu hình env hosocv.lgsp.* (xem javadoc GetTokenLgspUseCase) — trả "
            + "lỗi nghiệp vụ rõ ràng nếu chưa cấu hình, KHÔNG hard-code secret")
    public ResponseEntity<ApiResponse<String>> getTokenLgsp() {
        return ok(getTokenLgspUseCase.execute());
    }
}
