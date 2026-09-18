package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.vbcddh.AddVbcddhDynamicDocumentUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BL-211 (VBDen — Xử lý & Chuyển tiếp) — DoD (4): chuyển văn bản đến thành văn bản chỉ đạo điều
 * hành. Domino thật ({@code GuiSangVBCDDH.java}) tạo document mới trong database {@code vbcddh}
 * riêng (sao chép field cụ thể: {@code trichyeu}←subject, {@code hanxuly_date}←deadline,
 * {@code ykien}, {@code idvbden} trỏ ngược) rồi đánh dấu {@code assignment_of_directive_document=1}
 * + {@code idvbcd} (liên kết xuôi) trên văn bản đến gốc. Module VBCDDH đầy đủ (entity/controller
 * riêng) là BL-250, **chưa xây LÚC NÀY** — đúng rủi ro đã ghi trong backlog ("tạm thời dùng direct
 * DB"), nên document CHA ("văn bản CĐĐH") vẫn tạo qua hạ tầng Dynamic Data Engine chung (BL-101,
 * {@code documents} table) — KHÔNG đổi lại sang {@code vbcddh_documents} (BL-250) ở đây, ngoài phạm
 * vi bugfix này (đổi bảng lưu của document CHA là rủi ro riêng, cần xác nhận thêm).
 *
 * <p><b>Sửa 17/09/2026 (đóng gap "chưa replicate TaoNhiemVu()")</b>: trước bản vá này, use case CHỈ
 * tạo document CHA rồi LƯU TẠM {@code listChuTri}/{@code listPhoiHop} thô lên chính document đó (field
 * {@code list_chu_tri}/{@code list_phoi_hop}) — KHÔNG tạo document con {@code nhiemvu} nào, khiến tab
 * "Danh sách nhiệm vụ" (xem {@code ViewQueryService#VIEW_VBCDDH_NV_BY_IDVANBAN}) LUÔN rỗng cho vbden
 * chuyển qua đường này. Nay port ĐẦY ĐỦ {@code TaoNhiemVu()} thật ({@code GuiSangVBCDDH.java} dòng
 * 682-908) — tạo 1 document {@code nhiemvu} (bảng {@code vbcddh_documents}, BL-250, qua
 * {@link AddVbcddhDynamicDocumentUseCase} — ĐÚNG bảng vật lý mà
 * {@code com.td.application.vbcddh.PhanCongNhiemVuUseCase}/{@code ViewQueryService} đọc, KHÁC bảng
 * {@code documents} của document CHA) cho MỖI phần tử trong
 * {@code listChuTri}, với {@code idvbden} trỏ về văn bản đến gốc — field khoá mà bugfix 16/09/2026 của
 * {@code ViewQueryService#VIEW_VBCDDH_NV_BY_IDVANBAN} vừa thêm vào UNION khoá tìm kiếm.</p>
 *
 * <p><b>Thuật toán {@code TaoNhiemVu()} thật</b> — CHỈ chạy khi {@code listChuTri} không rỗng (đúng
 * Domino: {@code if(!listchutri.equals(""))}); với MỖI phần tử ({@link NhiemVuCdDhAssigneeDto}):</p>
 * <ol>
 *   <li>Field chung cho MỌI nhiệm vụ tạo trong 1 lần gọi: {@code form=nhiemvu},
 *   {@code tinhtrang="Chưa thực hiện"}, {@code creator}/{@code user_creator}/{@code created_by_user}/
 *   {@code nguoitheodoi(hide)}/{@code nguoiphutrach(hide)} = NGƯỜI ĐANG THAO TÁC (server-resolve qua
 *   {@link CurrentUserProvider}, KHÔNG tin client), {@code unit_creator}/{@code unit_creator_id} =
 *   đơn vị cụ thể nhất của người đó (phòng ban→đơn vị→cơ quan, đúng thứ tự ưu tiên Domino),
 *   {@code coquanid} = cơ quan người đó, {@code sokyhieu}/{@code ngayky_date}/{@code loaivanban}/
 *   {@code unit_in_charge}/{@code linhvuc}/{@code trichyeu}/{@code noidung} sao chép từ văn bản đến
 *   GỐC (KHÔNG phải document CHA vbcddh vừa tạo — đúng Domino đọc {@code docDocument}, biến văn bản
 *   đến), {@code hanxuly_date} = {@code request.thoiHan} nếu có, {@code user_nguoichuyencddh} =
 *   {@code ";"+username+";"}, {@code create_date}/{@code ngaygui_date}/{@code posteddate} = hôm nay,
 *   {@code idvbden} = id văn bản đến gốc.</li>
 *   <li>Field riêng theo LOẠI phần tử: có {@code identifier} (giao ĐƠN VỊ) →
 *   {@code donvithuchien}/{@code donvithuchienhide} = {@code name}/{@code identifier}; nếu kèm
 *   {@code receiver}/{@code userReceiver} (đại diện cụ thể của đơn vị) → GHI ĐÈ
 *   {@code chuyenvienthuchien}/{@code chuyenvienthuchienhide} bằng {@code receiver}/
 *   {@code userReceiver}. Có {@code username} (giao thẳng CÁ NHÂN, không có {@code identifier}) →
 *   {@code chuyenvienthuchien}/{@code chuyenvienthuchienhide} = {@code name}/{@code username}.</li>
 *   <li>Field "phối hợp" ({@code donviphoihop(hide)}/{@code chuyenvienphoihop(hide)}) — GOM 1 LẦN từ
 *   TOÀN BỘ {@code listPhoiHop} (không phải theo từng phần tử {@code listChuTri}), áp dụng GIỐNG HỆT
 *   lên MỌI nhiệm vụ tạo trong lần gọi này — đúng Domino ({@code donviphoihop}/{@code chuyenvienphoihop}
 *   tính TRƯỚC vòng lặp {@code listChuTri}, dùng lại y nguyên trong từng lượt lặp).</li>
 * </ol>
 *
 * <p><b>Đơn giản hoá CÓ CHỦ ĐÍCH so với Domino (không suy đoán thêm, ghi rõ)</b>:</p>
 * <ul>
 *   <li>KHÔNG tra cứu lại {@code vwuserbyusername} để lấy position/unit của {@code receiver} —
 *   TIN TRỰC TIẾP {@code receiver}/{@code userReceiver} client gửi (client đã có UI chọn người, cùng
 *   nguyên tắc "trust identifier đã resolve" áp dụng ở nhiều use case khác module này).</li>
 *   <li>KHÔNG tạo các field {@code *_obj} (snapshot JSON đầy đủ từng người/đơn vị,
 *   {@code chuyenvienthuchien_obj}/{@code donvithuchien_obj}/{@code donviphoihop_obj}/
 *   {@code chuyenvienphoihop_obj}) — KHÔNG có bằng chứng FE/view Java nào đọc lại các field này cho
 *   tab "Danh sách nhiệm vụ" (xem cột hiển thị {@code tableconfigNhiemVu.jsx}), tránh phức tạp hoá
 *   không cần thiết.</li>
 *   <li>KHÔNG replicate {@code LuuVet()} (tạo vết {@code tiendonhiemvu} NGAY LÚC tạo nhiệm vụ) — vết
 *   tiến độ đã có cơ chế port riêng qua {@code CapNhatTienDoUseCase} (BL-250) khi người dùng THỰC SỰ
 *   cập nhật tiến độ; tạo 1 vết "khởi tạo" rỗng ngay từ đầu chỉ là audit trail bổ sung, không ảnh
 *   hưởng tới việc tab "Danh sách nhiệm vụ" hiển thị đúng dữ liệu.</li>
 *   <li>KHÔNG replicate {@code capbanhanh}/{@code capbanhanh_id}/{@code capbanhanh_identifier} (đơn vị
 *   BAN HÀNH theo cấp {@code level} người thao tác, có 1 nhánh {@code level==1} dùng UNID Domino CỐ
 *   ĐỊNH không có tương đương Postgres) — KHÔNG field Java nào ({@code ViewQueryService}/
 *   {@code VbcddhDocumentRepository}) đọc field này, khác {@code coquanid} (CÓ dùng, nhóm view
 *   {@code vwNhiem_Vu_Ca_Nhan_Co_Quan*}) — đã port field đó.</li>
 *   <li>KHÔNG đồng bộ lại {@code nguoichidao}/{@code nguoichidaohide} — Domino thật CŨNG comment out
 *   2 dòng này trong chính {@code TaoNhiemVu()} (dòng 861-862), xác nhận KHÔNG set lúc tạo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GuiSangVbcddhUseCase {

    public static final String SOURCE_DOCUMENT_TYPE = "vbden";
    public static final String TARGET_DOCUMENT_TYPE = "vbcddh";
    static final String NHIEMVU_FORM = "nhiemvu";
    static final String DEFAULT_TINHTRANG = "Chưa thực hiện";

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final String FALLBACK_USERNAME = "system";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    public Result<UUID> execute(GuiSangVbcddhRequest request) {
        var sourceOptional = documentRepository.findById(request.getId());
        if (sourceOptional.isEmpty() || sourceOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var source = sourceOptional.get();
        if (!SOURCE_DOCUMENT_TYPE.equalsIgnoreCase(source.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> sourceFields = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());

        var targetResult = createVbcddhDocument(source, sourceFields, request);
        if (!targetResult.isSuccess()) {
            return targetResult;
        }

        markSourceConvertedToVbcddh(source, sourceFields, targetResult.getData());

        // Domino: if(!listchutri.equals("")) TaoNhiemVu(...) - CHI chay khi co chu tri.
        if (!request.getListChuTri().isEmpty()) {
            taoNhiemVu(source, sourceFields, request);
        }

        return targetResult;
    }

    // Domino: vbcddhDatabase.createDocument() - sao chep trichyeu/unit_in_charge/hanxuly_date/ykien,
    // idvbden tro nguoc ve van ban den goc (GuiSangVBCDDH.java). KHONG con luu tam list_chu_tri/
    // list_phoi_hop len day nua - da thay bang document nhiemvu THAT qua taoNhiemVu() (xem javadoc lop).
    private Result<UUID> createVbcddhDocument(
            BusinessDocument source, Map<String, Object> sourceFields, GuiSangVbcddhRequest request) {
        var targetRequest = new AddDynamicDocumentRequest();
        targetRequest.setTitle(source.getTitle());
        targetRequest.setDocumentType(TARGET_DOCUMENT_TYPE);

        Map<String, String> targetFields = new LinkedHashMap<>();
        targetFields.put("idvbden", source.getId().toString());
        targetFields.put("trichyeu", source.getTitle());
        putIfPresent(targetFields, "unit_in_charge", sourceFields.get("unit_in_charge"));
        putIfPresent(targetFields, "code", sourceFields.get("code"));
        if (request.getThoiHan() != null && !request.getThoiHan().isBlank()) {
            targetFields.put("hanxuly_date", request.getThoiHan());
        }
        if (request.getYKien() != null && !request.getYKien().isBlank()) {
            targetFields.put("ykien", request.getYKien());
        }
        targetRequest.setFields(targetFields);

        return addDynamicDocumentUseCase.execute(targetRequest);
    }

    // Domino: assignment_of_directive_document="1" + idvbcd tren chinh docVBden (GuiSangVBCDDH.java).
    private void markSourceConvertedToVbcddh(BusinessDocument source, Map<String, Object> sourceFields, UUID vbcddhId) {
        var before = buildSnapshot(source);
        Map<String, Object> updatedFields = new LinkedHashMap<>(sourceFields);
        updatedFields.put("assignment_of_directive_document", "1");
        updatedFields.put("idvbcd", vbcddhId.toString());

        source.replaceDynamicFields(toJson(updatedFields));
        var saved = documentRepository.save(source);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino TaoNhiemVu() (GuiSangVBCDDH.java dong 682-908) - xem javadoc lop ve thuat toan day du +
    // cac diem don gian hoa co chu dich.
    private void taoNhiemVu(BusinessDocument source, Map<String, Object> sourceFields, GuiSangVbcddhRequest request) {
        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        AppUser actingUser = appUserRepository.findByUsernameAndDeletedOnIsNull(actingUsername).orElse(null);
        String fullName = actingUser != null && actingUser.getFullName() != null && !actingUser.getFullName().isBlank()
            ? actingUser.getFullName() : actingUsername;
        UserOrganizationProfileDto profile = actingUser != null
            ? getUserOrganizationProfileUseCase.buildProfileForUser(actingUser) : null;

        String today = DATE_FORMATTER.format(LocalDate.now());
        PhoiHopAggregation phoiHop = aggregatePhoiHop(request.getListPhoiHop());

        for (NhiemVuCdDhAssigneeDto assignee : request.getListChuTri()) {
            if (assignee == null) {
                continue;
            }
            createSingleNhiemVu(source, sourceFields, request, assignee, actingUsername, fullName, profile, today, phoiHop);
        }
    }

    private void createSingleNhiemVu(
            BusinessDocument source, Map<String, Object> sourceFields, GuiSangVbcddhRequest request,
            NhiemVuCdDhAssigneeDto assignee, String actingUsername, String fullName,
            UserOrganizationProfileDto profile, String today, PhoiHopAggregation phoiHop) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("form", NHIEMVU_FORM);
        fields.put("tinhtrang", DEFAULT_TINHTRANG);
        fields.put("creator", fullName);
        fields.put("user_creator", actingUsername);
        fields.put("created_by_user", actingUsername);
        fields.put("nguoitheodoi", fullName);
        fields.put("nguoitheodoihide", actingUsername);
        fields.put("nguoiphutrach", fullName);
        fields.put("nguoiphutrachhide", actingUsername);
        if (profile != null) {
            putIfPresent(fields, "unit_creator", resolveMostSpecificUnitName(profile));
            putIfPresent(fields, "unit_creator_id", resolveMostSpecificUnitId(profile));
            putIfPresent(fields, "coquanid", profile.getAgencyId());
        }
        putIfPresent(fields, "sokyhieu", sourceFields.get("code"));
        putIfPresent(fields, "ngayky_date", sourceFields.get("promulgation_date"));
        if (request.getThoiHan() != null && !request.getThoiHan().isBlank()) {
            fields.put("hanxuly_date", request.getThoiHan());
        }
        putIfPresent(fields, "loaivanban", sourceFields.get("document_type"));
        putIfPresent(fields, "unit_in_charge", sourceFields.get("unit_in_charge"));
        putIfPresent(fields, "linhvuc", sourceFields.get("sector"));
        fields.put("trichyeu", source.getTitle() == null ? "" : source.getTitle());
        fields.put("noidung", source.getTitle() == null ? "" : source.getTitle());
        fields.put("user_nguoichuyencddh", ";" + actingUsername + ";");
        fields.put("create_date", today);
        fields.put("ngaygui_date", today);
        fields.put("posteddate", today);
        fields.put("idvbden", source.getId().toString());

        applyAssigneeFields(fields, assignee);
        applyPhoiHopFields(fields, phoiHop);

        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(assignee.getName() != null ? assignee.getName() : source.getTitle());
        childRequest.setDocumentType(TARGET_DOCUMENT_TYPE);
        childRequest.setFields(fields);

        var result = addVbcddhDynamicDocumentUseCase.execute(childRequest);
        if (!result.isSuccess()) {
            log.warn("Khong tao duoc nhiem vu CDDH cho van ban den {} (nguoi/don vi thuc hien: {}): {}",
                source.getId(), assignee.getName(), result.getError());
        }
    }

    // Domino dong 871-885: co "username" (khong co "identifier") = giao thang ca nhan; co
    // "identifier" = giao don vi, "receiver"/"userReceiver" (dai dien don vi do) GHI DE chuyenvienthuchien.
    private void applyAssigneeFields(Map<String, String> fields, NhiemVuCdDhAssigneeDto assignee) {
        boolean giaoCaNhan = isBlank(assignee.getIdentifier()) && !isBlank(assignee.getUsername());
        if (giaoCaNhan) {
            putIfPresent(fields, "chuyenvienthuchien", assignee.getName());
            putIfPresent(fields, "chuyenvienthuchienhide", assignee.getUsername());
            return;
        }
        putIfPresent(fields, "donvithuchien", assignee.getName());
        putIfPresent(fields, "donvithuchienhide", assignee.getIdentifier());
        if (!isBlank(assignee.getUserReceiver()) && !isBlank(assignee.getReceiver())) {
            fields.put("chuyenvienthuchien", assignee.getReceiver());
            fields.put("chuyenvienthuchienhide", assignee.getUserReceiver());
        }
    }

    private void applyPhoiHopFields(Map<String, String> fields, PhoiHopAggregation phoiHop) {
        if (!phoiHop.donViPhoiHop().isEmpty()) {
            fields.put("donviphoihop", String.join(";", phoiHop.donViPhoiHop()));
            fields.put("donviphoihophide", String.join(";", phoiHop.donViPhoiHopHide()));
        }
        if (!phoiHop.chuyenVienPhoiHop().isEmpty()) {
            fields.put("chuyenvienphoihop", String.join(";", phoiHop.chuyenVienPhoiHop()));
            fields.put("chuyenvienphoihophide", String.join(";", phoiHop.chuyenVienPhoiHopHide()));
        }
    }

    // Domino dong 694-787: gom 1 LAN tu TOAN BO listphoihop (khong phai theo tung phan tu listchutri),
    // ap dung GIONG HET len MOI nhiem vu tao trong lan goi nay.
    private PhoiHopAggregation aggregatePhoiHop(List<NhiemVuCdDhAssigneeDto> listPhoiHop) {
        Set<String> donViPhoiHop = new LinkedHashSet<>();
        Set<String> donViPhoiHopHide = new LinkedHashSet<>();
        Set<String> chuyenVienPhoiHop = new LinkedHashSet<>();
        Set<String> chuyenVienPhoiHopHide = new LinkedHashSet<>();

        for (NhiemVuCdDhAssigneeDto entry : listPhoiHop) {
            if (entry == null) {
                continue;
            }
            boolean giaoCaNhan = isBlank(entry.getIdentifier()) && !isBlank(entry.getUsername());
            if (giaoCaNhan) {
                addIfPresent(chuyenVienPhoiHop, entry.getName());
                addIfPresent(chuyenVienPhoiHopHide, entry.getUsername());
                continue;
            }
            addIfPresent(donViPhoiHop, entry.getName());
            addIfPresent(donViPhoiHopHide, entry.getIdentifier());
            if (!isBlank(entry.getUserReceiver())) {
                addIfPresent(chuyenVienPhoiHop, entry.getReceiver());
                addIfPresent(chuyenVienPhoiHopHide, entry.getUserReceiver());
            }
        }
        return new PhoiHopAggregation(donViPhoiHop, donViPhoiHopHide, chuyenVienPhoiHop, chuyenVienPhoiHopHide);
    }

    private record PhoiHopAggregation(
        Set<String> donViPhoiHop, Set<String> donViPhoiHopHide,
        Set<String> chuyenVienPhoiHop, Set<String> chuyenVienPhoiHopHide) {
    }

    // Domino: unit_creator = phongban != "" ? phongban : donvi != "" ? donvi : coquan != "" ? coquan : "".
    private String resolveMostSpecificUnitName(UserOrganizationProfileDto profile) {
        if (!isBlank(profile.getDepartmentName())) {
            return profile.getDepartmentName();
        }
        if (!isBlank(profile.getUnitName())) {
            return profile.getUnitName();
        }
        return profile.getAgencyName();
    }

    private String resolveMostSpecificUnitId(UserOrganizationProfileDto profile) {
        if (!isBlank(profile.getDepartmentId())) {
            return profile.getDepartmentId();
        }
        if (!isBlank(profile.getUnitId())) {
            return profile.getUnitId();
        }
        return profile.getAgencyId();
    }

    private void addIfPresent(Set<String> target, String value) {
        if (!isBlank(value)) {
            target.add(value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void putIfPresent(Map<String, String> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            target.put(key, value.toString());
        }
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
