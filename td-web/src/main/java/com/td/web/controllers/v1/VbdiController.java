package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.vbdi.BanHanhVbdiRequest;
import com.td.application.vbdi.CapNhatVanBanDiRequest;
import com.td.application.vbdi.CapNhatVanBanDiUseCase;
import com.td.application.vbdi.DeleteVbdiDocumentUseCase;
import com.td.application.vbdi.RestoreVbdiDocumentUseCase;
import com.td.application.vbdi.BanHanhVbdiUseCase;
import com.td.application.vbdi.GanVbdiDocumentRequest;
import com.td.application.vbdi.GanVbdiDocumentUseCase;
import com.td.application.vbdi.GanVanBanLienQuanRequest;
import com.td.application.vbdi.GanVanBanLienQuanUseCase;
import com.td.application.vbdi.XoaVanBanLienQuanRequest;
import com.td.application.vbdi.XoaVanBanLienQuanUseCase;
import com.td.application.vbdi.TraLoiVanBanDenRequest;
import com.td.application.vbdi.TraLoiVanBanDenUseCase;
import com.td.application.vbdi.XoaTraLoiVanBanDenRequest;
import com.td.application.vbdi.XoaTraLoiVanBanDenUseCase;
import com.td.application.vbdi.GetVbdiDetailRequest;
import com.td.application.vbdi.GetVbdiDetailUseCase;
import com.td.application.vbdi.GuiEmailVbdiRequest;
import com.td.application.vbdi.GuiEmailVbdiUseCase;
import com.td.application.vbdi.HieuChinhVbdiRequest;
import com.td.application.vbdi.HieuChinhVbdiUseCase;
import com.td.application.vbdi.AddVbdiEditHistoryRequest;
import com.td.application.vbdi.AddVbdiEditHistoryUseCase;
import com.td.application.vbdi.AddVbdiSeenDocumentRequest;
import com.td.application.vbdi.AddVbdiSeenDocumentUseCase;
import com.td.application.vbdi.AddVbdiImportantDocumentRequest;
import com.td.application.vbdi.AddVbdiImportantDocumentUseCase;
import com.td.application.vbdi.ChuyenXemDeBietVbdiRequest;
import com.td.application.vbdi.ChuyenXemDeBietVbdiUseCase;
import com.td.application.vbdi.AddVbdiUserActionLogRequest;
import com.td.application.vbdi.AddVbdiUserActionLogUseCase;
import com.td.application.vbdi.ListVbdiEditHistoryRequest;
import com.td.application.vbdi.ListVbdiEditHistoryUseCase;
import com.td.application.vbdi.ListVbdiUserActionLogRequest;
import com.td.application.vbdi.ListVbdiUserActionLogUseCase;
import com.td.application.vbdi.KySoVbdiRequest;
import com.td.application.vbdi.KySoVbdiUseCase;
import com.td.application.vbdi.ListVbdiSendLogRequest;
import com.td.application.vbdi.ListVbdiSendLogUseCase;
import com.td.application.vbdi.SendVbdiDocumentRequest;
import com.td.application.vbdi.SendVbdiDocumentUseCase;
import com.td.application.vbdi.SoanThaoVbdiRequest;
import com.td.application.vbdi.SoanThaoVbdiUseCase;
import com.td.application.vbdi.TrinhKyVbdiRequest;
import com.td.application.vbdi.TrinhKyVbdiUseCase;
import com.td.application.vbdi.GanKemTepVbdiRequest;
import com.td.application.vbdi.GanKemTepVbdiUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core): Soạn thảo / Trình ký / Ban hành / Danh sách văn bản đi theo view. Theo yêu cầu
 * người dùng (tách văn bản đi khỏi bảng dùng chung), module này KHÔNG còn dùng bảng {@code documents}
 * chung (BL-101) mà dùng bảng vật lý ĐỘC LẬP {@code vbdi_documents} qua entity {@code VbdiDocument} +
 * repository {@code VbdiDocumentRepository} (cùng kiến trúc {"tách bảng"} đã áp dụng cho {@code duthao}
 * — xem {@code DuThaoController}). "Soạn thảo" ở đây KHÔNG replicate module {@code duthao} thật (dự
 * thảo, BL-230 — chưa xây) mà tạo thẳng document {@code vbdi} trạng thái {@code DRAFT} vào
 * {@code vbdi_documents}, đúng phạm vi DoD BL-220 (xem Javadoc {@code SoanThaoVbdiUseCase}).
 */
@RestController
@RequestMapping("/api/v1/vbdi")
@RequiredArgsConstructor
@Validated
@Tag(name = "VBDi", description = "Văn bản đi — Core (BL-220)")
public class VbdiController extends BaseController {

    private static final String DB_TYPE = "vbdi";

    // SEC-01: quyền "cập nhật/hiệu chỉnh văn bản đi" thật (docs/frontend/05-vbden-vbdi.md mục
    // "Quyền" — currentUser.roles chứa QCapNhatVBDi/_DV/_PB), cộng ADMIN/CVQuanTri làm lối thoát
    // quản trị (quy ước đã dùng chung toàn hệ thống). Không tìm thấy role riêng cho hành động "ban
    // hành" trong bằng chứng đã đọc — dùng chung họ role cập nhật, không suy diễn role mới.
    private static final String CAP_NHAT_VBDI_ROLES =
        "hasAnyRole('ADMIN', '" + QlvbRoles.CAP_NHAT_VBDI + "', '" + QlvbRoles.CAP_NHAT_VBDI_DV
            + "', '" + QlvbRoles.CAP_NHAT_VBDI_PB + "', '" + QlvbRoles.QUAN_TRI + "')";

    private final SoanThaoVbdiUseCase soanThaoVbdiUseCase;
    private final CapNhatVanBanDiUseCase capNhatVanBanDiUseCase;
    private final DeleteVbdiDocumentUseCase deleteVbdiDocumentUseCase;
    private final RestoreVbdiDocumentUseCase restoreVbdiDocumentUseCase;
    private final HieuChinhVbdiUseCase hieuChinhVbdiUseCase;
    private final AddVbdiEditHistoryUseCase addVbdiEditHistoryUseCase;
    private final ListVbdiEditHistoryUseCase listVbdiEditHistoryUseCase;
    private final TrinhKyVbdiUseCase trinhKyVbdiUseCase;
    private final BanHanhVbdiUseCase banHanhVbdiUseCase;
    private final KySoVbdiUseCase kySoVbdiUseCase;
    private final GanKemTepVbdiUseCase ganKemTepVbdiUseCase;
    private final GuiEmailVbdiUseCase guiEmailVbdiUseCase;
    private final GetVbdiDetailUseCase getVbdiDetailUseCase;
    private final ViewQueryService viewQueryService;
    private final SendVbdiDocumentUseCase sendVbdiDocumentUseCase;
    private final ListVbdiSendLogUseCase listVbdiSendLogUseCase;
    private final GanVbdiDocumentUseCase ganVbdiDocumentUseCase;
    private final GanVanBanLienQuanUseCase ganVanBanLienQuanUseCase;
    private final XoaVanBanLienQuanUseCase xoaVanBanLienQuanUseCase;
    private final TraLoiVanBanDenUseCase traLoiVanBanDenUseCase;
    private final XoaTraLoiVanBanDenUseCase xoaTraLoiVanBanDenUseCase;
    private final AddVbdiUserActionLogUseCase addVbdiUserActionLogUseCase;
    private final ListVbdiUserActionLogUseCase listVbdiUserActionLogUseCase;
    private final AddVbdiSeenDocumentUseCase addVbdiSeenDocumentUseCase;
    private final AddVbdiImportantDocumentUseCase addVbdiImportantDocumentUseCase;
    private final ChuyenXemDeBietVbdiUseCase chuyenXemDeBietVbdiUseCase;

    @PostMapping("/SoanThao")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Soạn thảo văn bản đi (BL-220 DoD 1)",
        description = "form-data field động (subject, unit_in_charge, ...) + meta "
            + "date_fields/number_fields/richtext_fields (BL-101). Trạng thái khởi tạo DRAFT")
    public ResponseEntity<ApiResponse<UUID>> soanThao(@RequestParam Map<String, String> params) {
        return created(soanThaoVbdiUseCase.execute(buildSoanThaoRequest(params)));
    }

    // Yeu cau nguoi dung 28/08/2026 ("xem lai khi hieu chinh va thao tac luu dang goi api domino,
    // port luon") - xem javadoc HieuChinhVbdiUseCase. id lay tu PATH (khac SoanThao, khong co id) -
    // form-data field dong CHI gom field DA DOI (FE tu tinh diff truoc khi goi, dung cach
    // ChiTietModal.jsx#onFinish dang lam qua getChangedFieldDetails).
    @PostMapping("/HieuChinh/{id}")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Hiệu chỉnh văn bản đi (partial update)",
        description = "form-data field động CHỈ gồm field đã đổi (merge lên dynamic_fields hiện có, "
            + "không replace toàn bộ) + meta date_fields/number_fields/richtext_fields (BL-101)")
    public ResponseEntity<ApiResponse<UUID>> hieuChinh(
            @PathVariable UUID id, @RequestParam Map<String, String> params) {
        return ok(hieuChinhVbdiUseCase.execute(buildHieuChinhRequest(id, params)));
    }

    private HieuChinhVbdiRequest buildHieuChinhRequest(UUID id, Map<String, String> params) {
        var request = new HieuChinhVbdiRequest();
        request.setId(id);
        request.setSubject(firstNonBlank(params.get("subject"), params.get("title")));
        request.setStatus(params.get("status"));
        request.setDateFields(params.get("date_fields"));
        request.setNumberFields(params.get("number_fields"));
        request.setRichtextFields(params.get("richtext_fields"));

        Map<String, String> fields = new java.util.LinkedHashMap<>(params);
        for (String metaKey : List.of(
                "subject", "title", "status", "date_fields", "number_fields", "richtext_fields")) {
            fields.remove(metaKey);
        }
        request.setFields(fields);
        return request;
    }

    // Yeu cau nguoi dung 28/08/2026 ("ghi log chinh sua thi tao ra bang rieng tham khao them nhu
    // du thao da lam") - mirror DuThaoController#luuVetHieuChinh/vetHieuChinh, xem javadoc
    // AddVbdiEditHistoryUseCase/ListVbdiEditHistoryUseCase.
    @PostMapping("/LuuVetHieuChinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi vết hiệu chỉnh field/tệp đính kèm của văn bản đi",
        description = "Mỗi lần Lưu có field đổi (hoặc thêm/xoá tệp đính kèm) ghi 1 dòng "
            + "{field:{oldvalue,newvalue}} vào bảng vbdi_edit_history_documents (bảng lưu vết "
            + "RIÊNG). user_creator do SERVER tự gán theo tài khoản đang đăng nhập")
    public ResponseEntity<ApiResponse<UUID>> luuVetHieuChinh(
            @Valid @RequestBody AddVbdiEditHistoryRequest request) {
        return created(addVbdiEditHistoryUseCase.execute(request));
    }

    @PostMapping("/VetHieuChinh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử hiệu chỉnh theo văn bản đi cha (tương thích qlvb/danhmuc/GetRes)",
        description = "Đúng khuôn item legacy {id,parentid,form=log,form_edit,edit_obj,creator,"
            + "user_creator,created} để VetHieuChinh.jsx render; thứ tự TĂNG DẦN theo tạo ra")
    public ResponseEntity<ApiResponse<PaginationResponse<Map<String, Object>>>> vetHieuChinh(
            @Valid @RequestBody ListVbdiEditHistoryRequest request) {
        PaginationResponse<Map<String, Object>> result = listVbdiEditHistoryUseCase.execute(request);
        // Khong dung BaseController.ok(...): overload ok(PaginationResponse) tra
        // ApiResponse<List<T>>, trong khi FE doc data.items - can giu ApiResponse<PaginationResponse>.
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private SoanThaoVbdiRequest buildSoanThaoRequest(Map<String, String> params) {
        var request = new SoanThaoVbdiRequest();
        request.setSubject(firstNonBlank(params.get("subject"), params.get("title")));
        request.setStatus(params.get("status"));
        request.setDateFields(params.get("date_fields"));
        request.setNumberFields(params.get("number_fields"));
        request.setRichtextFields(params.get("richtext_fields"));

        // 28/08/2026 ("Chọn sổ văn bản", cùng khuôn VbdenController#buildTiepNhanRequest): "sổ văn
        // bản" chọn khi soạn thảo (document_registers dùng chung, BL-116) — meta riêng, không phải
        // field động.
        String documentRegisterId = params.get("document_register_id");
        if (documentRegisterId != null && !documentRegisterId.isBlank()) {
            try {
                request.setDocumentRegisterId(java.util.UUID.fromString(documentRegisterId.trim()));
            } catch (IllegalArgumentException ignored) {
                // document_register_id khong hop le (khong phai UUID) - bo qua, coi nhu khong chon so
                // (giu hanh vi cu, khong chan tao van ban chi vi meta nay sai dinh dang).
            }
        }

        // Bugfix 24/08/2026 (đồng bộ VbdenController): "document_type" là field nghiệp vụ thật của
        // form vbdi ("Loại văn bản" — Công văn/Báo cáo/..., xem SubFormThongTinCoBan.jsx name=
        // "document_type"), khác hoàn toàn documentType/DOCUMENT_TYPE ở tầng entity (hardcode "vbdi"
        // trong SoanThaoVbdiUseCase, không đọc từ params) — không được strip khỏi fields động nữa.
        Map<String, String> fields = new java.util.LinkedHashMap<>(params);
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

    @DeleteMapping("/{id}")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Xóa văn bản đi (soft delete, 11/09/2026)",
        description = "Port qlvb/delete (databasetype=vbdi, deleteoption=trash — Domino gốc CŨNG chỉ "
            + "chuyển vào thùng rác, không xoá cứng). CHỈ soft delete (markAsDeleted — giữ nguyên dữ "
            + "liệu, đánh dấu deletedOn/deletedBy) — xem javadoc DeleteVbdiDocumentUseCase. KHÔNG có "
            + "endpoint xoá vĩnh viễn cho vbdi (ngoài phạm vi yêu cầu — nếu cần, theo tiền lệ "
            + "DocumentsController#hardDeleteDocument, route /permanent riêng, role ADMIN riêng)")
    public ResponseEntity<ApiResponse<UUID>> deleteVbdi(
            @Parameter(description = "Document ID (văn bản đi)", required = true) @PathVariable("id") UUID id) {
        return ok(deleteVbdiDocumentUseCase.execute(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Khôi phục văn bản đi đã xoá (thùng rác, 11/09/2026)",
        description = "Port VBDIButtonActionPage.jsx#handleRestore (case \"recover\", qlvb/vbdi/"
            + "recoverdocumentout) — đảo ngược DeleteVbdiDocumentUseCase (markAsDeleted -> restore), "
            + "xem javadoc RestoreVbdiDocumentUseCase")
    public ResponseEntity<ApiResponse<UUID>> restoreVbdi(
            @Parameter(description = "Document ID (văn bản đi)", required = true) @PathVariable("id") UUID id) {
        return ok(restoreVbdiDocumentUseCase.execute(id));
    }

    @PostMapping("/TrinhKy")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Trình ký văn bản đi (BL-220 DoD 2)",
        description = "Không dùng Workflow module (scaffold chưa triển khai, phát hiện từ BL-211) — "
            + "tạo bản ghi vbdi_res (đóng vai trò 'nhiệm vụ ký duyệt') + chuyển trạng thái văn bản "
            + "sang PENDING_SIGNATURE")
    public ResponseEntity<ApiResponse<UUID>> trinhKy(@Valid @RequestBody TrinhKyVbdiRequest request) {
        return created(trinhKyVbdiUseCase.execute(request));
    }

    @PostMapping("/BanHanh")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Ban hành văn bản đi (BL-220 DoD 3)",
        description = "Yêu cầu văn bản đã trình ký (PENDING_SIGNATURE). Sinh document_number chính "
            + "thức (BL-104) + set promulgation_date + chuyển trạng thái ISSUED")
    public ResponseEntity<ApiResponse<String>> banHanh(@Valid @RequestBody BanHanhVbdiRequest request) {
        return created(banHanhVbdiUseCase.execute(request));
    }

    @PostMapping(value = "/KySo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Ký số văn bản đi (BL-221 DoD 1)",
        description = "Backend KHÔNG tự ký số — người dùng ký số cục bộ bằng USB token/HSM (tích "
            + "hợp bên thứ ba, ngoài phạm vi BL-221) rồi upload file PDF ĐÃ KÝ tại đây; use case chỉ "
            + "lưu file (tệp chính) + đánh dấu dakyso/dakycoquan trên văn bản, đúng "
            + "AddSignAttachment() thật (không tự xác thực lại — xác thực là hành động riêng, xem "
            + "POST /api/v1/documents/{documentId}/files/{fileId}/verify-signature)")
    public ResponseEntity<ApiResponse<UUID>> kySo(
            @Parameter(description = "Document ID (văn bản đi)", required = true) @RequestParam UUID id,
            @Parameter(description = "File PDF đã ký số", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Ký cơ quan (dakycoquan)") @RequestParam(value = "coQuan", defaultValue = "false") boolean coQuan) {
        // try-with-resources: đóng stream sau khi upload xong — stream còn mở giữ khoá tệp tạm multipart
        // trên Windows, Tomcat không xoá được ("Failed to deleted temporary file used for part [file]").
        try (java.io.InputStream fileStream = file.getInputStream()) {
            var request = new KySoVbdiRequest();
            request.setId(id);
            request.setFileContent(fileStream);
            request.setFileName(file.getOriginalFilename());
            request.setMimeType(file.getContentType());
            request.setFileSize(file.getSize());
            request.setCoQuan(coQuan);
            return created(kySoVbdiUseCase.execute(request));
        } catch (Exception ex) {
            return badRequest(Result.failure("Lỗi xử lý tệp tin: " + ex.getMessage()));
        }
    }

    @PostMapping("/GanKemTep")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đính kèm ý kiến (vết quá trình xử lý) văn bản đi (15/09/2026)",
        description = "Ghi 1 vết vbdi_res (action_type=gankemtep) mang ý kiến — creator do SERVER tự "
            + "gán theo tài khoản đang đăng nhập, KHÔNG nhận từ client. KHÔNG mutate field nào trên "
            + "văn bản đi cha (khác TrinhKy — không đổi trạng thái). Dùng sau khi ký số qua "
            + "POST /api/v1/vbdi/KySo để ghi lại lịch sử \"đã sửa/ký tệp\", hiện ở tab Quá trình xử lý.")
    public ResponseEntity<ApiResponse<UUID>> ganKemTep(@Valid @RequestBody GanKemTepVbdiRequest request) {
        return created(ganKemTepVbdiUseCase.execute(request));
    }

    @PostMapping("/GuiEmail")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Gửi văn bản đi qua email công vụ (BL-221 DoD 3)",
        description = "Yêu cầu văn bản đã ban hành (ISSUED). Không tìm thấy bằng chứng SMTP thật "
            + "trong source Domino (chỉ có cơ chế 'mail-in' định tuyến nội bộ giữa các database NSF, "
            + "khác bản chất với gửi email) — đây là thiết kế mới cho Spring Boot, cần cấu hình SMTP "
            + "relay thật (spring.mail.*) trước khi dùng cho nghiệp vụ thật. Đính kèm toàn bộ file "
            + "đã upload của văn bản")
    public ResponseEntity<ApiResponse<Void>> guiEmail(@Valid @RequestBody GuiEmailVbdiRequest request) {
        return ok(guiEmailVbdiUseCase.execute(request));
    }

    @PostMapping("/XemChiTiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết văn bản đi (đọc bảng riêng vbdi_documents)",
        description = "Tương thích payload legacy qlvb/get?databasetype=vbdi ({id, items}). KHÔNG dùng "
            + "GET /api/v1/documents/{id} chung — vbdi đã tách sang bảng vật lý độc lập "
            + "vbdi_documents (xem VbdiDocument), use case đọc bảng documents sẽ không bao giờ thấy "
            + "văn bản đi nữa. items=\"getall\" trả về tất cả field (tĩnh + động), hoặc "
            + "items=\"field1;field2\" lọc theo danh sách field")
    public ResponseEntity<ApiResponse<Map<String, Object>>> xemChiTiet(
            @RequestBody GetVbdiDetailRequest request) {
        return ok(getVbdiDetailUseCase.execute(request));
    }

    @PostMapping("/GetView")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách văn bản đi theo view (BL-220 DoD 4)",
        description = "Uỷ quyền cho ViewQueryService (BL-102) — vwvandi_chobanhanh (đã trình ký, chờ "
            + "ban hành), vwvandi_dabanhanh (đã ban hành) — lọc theo status tĩnh, khác cơ chế "
            + "user_process của vbden")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getView(
            @Valid @RequestBody ViewQueryRequest request) {
        return ok(viewQueryService.execute(DB_TYPE, request));
    }

    @PostMapping("/SendDocument")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Gửi văn bản đi tới nơi nhận (28/08/2026, ghi Nhật ký gửi nhận)",
        description = "Tương thích Ý NGHĨA (không tương thích payload byte-for-byte) với "
            + "qlvb/vbdi?action=senddocument|resenddocument. Ghi 1 dòng/1 nơi nhận vào bảng RIÊNG "
            + "vbdi_send_log_documents (4 nhóm: coordinator/internalUnit/contactUnit/externalUnit), "
            + "dedup theo identifier khi sendType=senddocument (mặc định). Yêu cầu văn bản đã ISSUED. "
            + "sendType cũng nhận 3 giá trị 'thu hồi' (29/08/2026, port RevokeDocumentOut.java/"
            + "RevokeAllDocumentOut.java, xem javadoc VbdiSendType): thuhoivanban/laylaivanban (thu "
            + "hồi 1 phần theo nơi nhận đã chọn) và thuhoitoanbo (thu hồi toàn bộ, client tự đọc lại "
            + "toàn bộ nơi nhận hiện tại qua SendLog rồi gửi kèm). Và giá trị UnitRevoke (29/08/2026, "
            + "port SendDocument.java actionname=UnitRevoke): gửi thông báo 'thay thế/thu hồi' tới "
            + "nơi nhận khi văn bản đã được gắn link thay thế qua POST /GanVanBanDi. KHÔNG tích hợp "
            + "thông báo realtime/"
            + "gateway mail-in/reader ACL thật (xem javadoc SendVbdiDocumentUseCase về các gap đã ghi "
            + "nhận có chủ đích) — trả về số dòng đã ghi")
    public ResponseEntity<ApiResponse<Integer>> sendDocument(@Valid @RequestBody SendVbdiDocumentRequest request) {
        return created(sendVbdiDocumentUseCase.execute(request));
    }

    @PostMapping("/SendLog")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Nhật ký gửi nhận theo văn bản đi (28/08/2026)",
        description = "Đọc lại bảng vbdi_send_log_documents theo documentId, mới nhất trước "
            + "({id,parentid,send_type,recipient_group,recipient_identifier,recipient_name,mailin,"
            + "status,content,user_creator,created})")
    public ResponseEntity<ApiResponse<PaginationResponse<Map<String, Object>>>> sendLog(
            @Valid @RequestBody ListVbdiSendLogRequest request) {
        PaginationResponse<Map<String, Object>> result = listVbdiSendLogUseCase.execute(request);
        // Khong dung BaseController.ok(PaginationResponse) chung (overload do tra ve
        // ApiResponse<List<T>>) - giu nguyen kieu PaginationResponse de FE doc data.items, dung
        // tien le DuThaoController#vetHieuChinh.
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/GanVanBanDi")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Gắn văn bản đi thay thế/thu hồi (29/08/2026)",
        description = "Port DocumentOutProcesses.java#add_Document_Revoke (Domino, qlvb/vbdi/"
            + "adddocrevoke). Liên kết văn bản B (documentId, đang mở) tới văn bản A "
            + "(replacesDocumentId, văn bản bị thay thế/thu hồi) — truyền replacesDocumentId=null để "
            + "gỡ liên kết. CHỈ lưu tham chiếu UUID (field động replaces_document_id trên B, xem "
            + "javadoc GanVbdiDocumentUseCase) — KHÔNG sao chép snapshot dữ liệu A như Domino gốc, "
            + "FE tự gọi lại XemChiTiet để lấy dữ liệu A mới nhất khi cần hiển thị")
    public ResponseEntity<ApiResponse<UUID>> ganVanBanDi(@Valid @RequestBody GanVbdiDocumentRequest request) {
        return ok(ganVbdiDocumentUseCase.execute(request));
    }

    @PostMapping("/GanVanBanLienQuan")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Gán văn bản đến/văn bản đi liên quan (modal \"Gán văn bản đến\"/\"Gán văn "
            + "bản đi\" ở chi tiết văn bản đi, 11/09/2026)",
        description = "DÙNG CHUNG cho action ganvanbanden/ganvanbandi (GanVanBanModal.jsx) — port "
            + "field vanban_lienquan_obj (qlvb/add?databasetype=vbdi). Xác nhận qua đọc trực tiếp "
            + "TDDocument.java: KHÔNG có nhánh actionname riêng, field lưu GENERIC, KHÔNG write-back "
            + "lên văn bản được tham chiếu, KHÔNG đụng user_process/tạo document_process con (khác "
            + "documentin_obj/ganduthaotraloi) — xem javadoc GanVanBanLienQuanUseCase")
    public ResponseEntity<ApiResponse<UUID>> ganVanBanLienQuan(
            @Valid @RequestBody GanVanBanLienQuanRequest request) {
        return ok(ganVanBanLienQuanUseCase.execute(request));
    }

    @PostMapping("/XoaVanBanLienQuan")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Xóa văn bản liên quan (danh sách \"Văn bản kèm theo\" ở chi tiết văn bản đi, 11/09/2026)",
        description = "Port VanBanLienQuanList.jsx#handleDelete — SERVER tự loại đúng 1 phần tử theo "
            + "documentId khỏi vanban_lienquan_obj (KHÔNG tin nguyên mảng đã lọc sẵn từ FE như Domino "
            + "gốc, tránh ghi đè mất thay đổi song song) — xem javadoc XoaVanBanLienQuanUseCase")
    public ResponseEntity<ApiResponse<UUID>> xoaVanBanLienQuan(
            @Valid @RequestBody XoaVanBanLienQuanRequest request) {
        return ok(xoaVanBanLienQuanUseCase.execute(request));
    }

    @PostMapping("/TraLoiVanBanDen")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Trả lời văn bản đến (modal \"Trả lời văn bản đến\" ở chi tiết văn bản đi, 09/09/2026)",
        description = "Port GanVanBanModal.jsx#btnActionTraLoiVanBanDen (Domino qlvb/add lưu "
            + "documentin_obj + qlvb/vbden/CapNhatVanBanDen?action=ganduthaotraloi cho MỖI văn bản đến "
            + "chọn). Với mỗi văn bản đến: nếu đang có user_process thì chuyển acting username từ "
            + "user_process/user_returned sang user_processing (xác nhận qua đọc trực tiếp Domino "
            + "taoduthao_hoso_phieutrinh — action không ảnh hưởng logic, cùng thuật toán TaoDuThaoVBdenUseCase), "
            + "LUÔN tạo 1 bản ghi vbden_res (action=ganduthaotraloi) — xem javadoc TraLoiVanBanDenUseCase")
    public ResponseEntity<ApiResponse<UUID>> traLoiVanBanDen(@Valid @RequestBody TraLoiVanBanDenRequest request) {
        return ok(traLoiVanBanDenUseCase.execute(request));
    }

    @PostMapping("/XoaTraLoiVanBanDen")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Xóa văn bản đến khỏi danh sách \"Trả lời cho văn bản đến\" (11/09/2026)",
        description = "Port TraLoiChoVanBanDen.jsx#handleDelete — SERVER tự loại đúng 1 phần tử theo "
            + "documentId khỏi documentin_obj (KHÔNG tin nguyên mảng đã lọc sẵn từ FE như Domino gốc). "
            + "CHỦ Ý KHÔNG hoàn tác user_process/vbden_res đã tạo khi TraLoiVanBanDen (đúng Domino gốc, "
            + "xem javadoc XoaTraLoiVanBanDenUseCase)")
    public ResponseEntity<ApiResponse<UUID>> xoaTraLoiVanBanDen(
            @Valid @RequestBody XoaTraLoiVanBanDenRequest request) {
        return ok(xoaTraLoiVanBanDenUseCase.execute(request));
    }

    @PostMapping("/CapNhatVanBanDi")
    @PreAuthorize(CAP_NHAT_VBDI_ROLES)
    @Operation(summary = "Cập nhật văn bản đi (ghi vết \"Quá trình xử lý\" lên văn bản đến liên kết, 11/09/2026)",
        description = "Port action Domino \"capnhatvanbandi\" (qlvb/vbden/CapNhatVanBanDen?"
            + "databasetype=vbden&action=capnhatvanbandi) — gọi MỖI KHI lưu văn bản đi bình thường "
            + "(KHÁC \"Vào sổ\", đã port ở CapNhatKetQuaXuLyUseCase/action=vaosovanbandi) và văn bản "
            + "đi đó có documentin_obj. KHÔNG có case switch riêng ở Domino gốc (rơi default, không "
            + "đổi field nghiệp vụ trên văn bản đến) NHƯNG preamble servlet LUÔN tạo 1 bản ghi "
            + "vbden_res — xem javadoc CapNhatVanBanDiUseCase. Server tự đọc lại documentin_obj đã lưu "
            + "trên văn bản đi (KHÔNG nhận lại từ client)")
    public ResponseEntity<ApiResponse<UUID>> capNhatVanBanDi(
            @Valid @RequestBody CapNhatVanBanDiRequest request) {
        return ok(capNhatVanBanDiUseCase.execute(request));
    }

    @PostMapping("/LogUserAction")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi 1 dòng Lịch sử thao tác văn bản (30/08/2026)",
        description = "Port Domino action=loguseraction (LogUserAction.java#setLogUserAction). "
            + "username SERVER-AUTHORITATIVE qua CurrentUserProvider — fullname/unit/identifier là "
            + "field trình bày, chấp nhận từ client (đúng mức tin cậy Domino gốc). Không yêu cầu "
            + "role cập nhật vbdi vì đây là log side-effect đi kèm nhiều hành động khác nhau, không "
            + "phải 1 hành động nghiệp vụ độc lập")
    public ResponseEntity<ApiResponse<UUID>> logUserAction(@Valid @RequestBody AddVbdiUserActionLogRequest request) {
        return created(addVbdiUserActionLogUseCase.execute(request));
    }

    @PostMapping("/SeenDocument")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã xem văn bản đi (09/09/2026)",
        description = "Port hợp nhất Domino qlvb/add&actionname=addlogseendocument (TDDocument.java) "
            + "VÀ nhánh key==\"\" của qlvb/vbdi/seendocumentout (DocumentOutProcesses.java#"
            + "Seen_document) — id là UUID Postgres nên cả 2 route Domino gốc không đọc được, phải "
            + "port riêng (xem javadoc AddVbdiSeenDocumentUseCase). Gọi khi FE mở xem chi tiết văn bản "
            + "đi. username SERVER-AUTHORITATIVE qua CurrentUserProvider. Không yêu cầu role cập nhật "
            + "vbdi (cùng lý do LogUserAction — log side-effect đi kèm hành động xem, không phải 1 "
            + "hành động nghiệp vụ độc lập)")
    public ResponseEntity<ApiResponse<UUID>> seenDocument(@Valid @RequestBody AddVbdiSeenDocumentRequest request) {
        return ok(addVbdiSeenDocumentUseCase.execute(request));
    }

    @PostMapping("/AddImportant")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu/bỏ đánh dấu văn bản đi quan trọng - gắn sao (21/09/2026)",
        description = "Port qlvb/vbdi/addimportant?databasetype=vbdi "
            + "(DocumentOutProcesses.java#HandleAddImportant) - flag RIÊNG CHO TỪNG NGƯỜI DÙNG "
            + "(field multivalue MarkImportant trên văn bản, xem javadoc "
            + "AddVbdiImportantDocumentUseCase). username SERVER-AUTHORITATIVE qua "
            + "CurrentUserProvider. Không yêu cầu role cập nhật vbdi - hành động cá nhân, không phải "
            + "sửa nội dung văn bản.")
    public ResponseEntity<ApiResponse<UUID>> addImportant(
            @Valid @RequestBody AddVbdiImportantDocumentRequest request) {
        return ok(addVbdiImportantDocumentUseCase.execute(request));
    }

    @PostMapping("/ChuyenXemDeBiet")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển xem để biết văn bản đi (14/09/2026)",
        description = "Port Domino qlvb/vbdi/coordinatedtransfer?action=xemdebiet "
            + "(DocumentOutProcesses.java#HandleCoordinatedTransfer, chỉ nhánh xemdebiet — route FE "
            + "vbdi/xuly/nhandebiet, xem javadoc ChuyenXemDeBietVbdiUseCase). username người thực hiện "
            + "SERVER-AUTHORITATIVE qua CurrentUserProvider.")
    public ResponseEntity<ApiResponse<UUID>> chuyenXemDeBiet(@Valid @RequestBody ChuyenXemDeBietVbdiRequest request) {
        return ok(chuyenXemDeBietVbdiUseCase.execute(request));
    }

    @PostMapping("/GetLogUserAction")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đọc Lịch sử thao tác văn bản theo văn bản đi (30/08/2026)",
        description = "Port Domino action=getloguseraction (LogUserAction.java#getLogUserAction). "
            + "Trả mảng PHẲNG (không bọc PaginationResponse, đúng shape Domino gốc) — mỗi phần tử là "
            + "1 người dùng, mỗi loại thao tác là 1 field chứa danh sách timestamp nối ';' (đã tự "
            + "nhóm lại từ bảng phẳng vbdi_user_action_logs, xem javadoc ListVbdiUserActionLogUseCase)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLogUserAction(
            @Valid @RequestBody ListVbdiUserActionLogRequest request) {
        return ok(listVbdiUserActionLogUseCase.execute(request));
    }
}
