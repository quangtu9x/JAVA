package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * N15 (03/09/2026 — bổ sung theo yêu cầu người dùng, xem {@code docs/analysis/02-checklist-port-
 * vbden-con-lai.md}): tương đương {@code CapNhatVanBanDen.java} thật, hàm
 * {@code vanthuketthucxuly()} (nút "Cập nhật kết quả xử lý" — {@code VBDenButtonActionVT.jsx#
 * btn_vanthu_ketthuc_xuly}, mở {@code ChoYKienModal.jsx} với {@code action="vanthuketthucxuly"}).
 *
 * <p><b>Thuật toán ({@code vanthuketthucxuly()} dòng 236-308 thật)</b>:</p>
 * <ol>
 *   <li>Gộp {@code user_process} + {@code user_processing} thành 1 danh sách ứng viên.</li>
 *   <li>Với MỖI ứng viên, tra {@code identifiers} (chuỗi phân cấp tổ chức LÁ→GỐC — tương đương
 *   {@link GetUserOrganizationProfileUseCase#buildProfileForUser}) — nếu chuỗi đó CHỨA identifier
 *   (phẳng, {@link AppUser#getIdentifier()}) của người văn thư đang thao tác, HOẶC văn bản có
 *   {@code identifier_creator} == identifier văn thư VÀ không phải văn bản nội bộ ({@code internal
 *   != "1"}) — thì ứng viên đó bị loại khỏi {@code user_process}/{@code user_processing}/
 *   {@code user_coordinate}/{@code user_to_read}/{@code user_distributor}, thêm vào
 *   {@code user_processed} (đã "xử lý xong đợt này", cùng đơn vị với văn thư).</li>
 *   <li>Tính lại {@code status} theo công thức chung ({@link VbdenStatusService}, giống các use case
 *   khác).</li>
 *   <li>Nhánh đặc biệt (Domino dòng 278-286): nếu văn bản KHÔNG nội bộ VÀ {@code level} (độ sâu tổ
 *   chức) của văn thư là {@code "1"} hoặc {@code "3"} (2 cấp được Domino hardcode — không suy đoán
 *   thêm ý nghĩa, giữ nguyên magic string gốc) — coi như xử lý xong TOÀN BỘ ở cấp này: xoá sạch
 *   {@code user_process}/{@code user_processing}, ép {@code status} = {@code "3"} (quá hạn) hoặc
 *   {@code "4"} (đúng hạn) theo số ngày còn lại, GHI ĐÈ lên kết quả tính ở bước 3.
 *   <b>Bugfix 14/09/2026</b> (báo lỗi người dùng: văn bản biến mất khỏi MỌI danh sách sau hành động
 *   này, tài khoản demo2) — Domino gốc "xoá sạch" ở đây KHÔNG kèm điều kiện, kể cả với những ứng viên
 *   bước 2 vừa BỎ QUA (khác đơn vị văn thư, không được thêm vào {@code user_processed}) — verbatim
 *   port ban đầu làm y hệt, khiến người dùng đó biến mất khỏi CẢ 6 field/mọi view cá nhân cùng lúc
 *   (không còn cách nào tìm lại văn bản). Sửa: trước khi xoá, đưa NHỮNG AI CÒN LẠI trong
 *   {@code user_process}/{@code user_processing} vào {@code user_processed} — giữ đúng ý định "chốt
 *   xong toàn bộ ở cấp này" mà không làm mất dấu vết. Áp dụng cho MỌI văn bản đi qua tổ hợp điều kiện
 *   này (không phải sửa riêng 1 văn bản).</li>
 *   <li>Tạo bản ghi {@code vbden_res} mới ({@code action="vanthuketthucxuly"}, {@code opinion},
 *   {@code creator}/{@code position} hiển thị, {@code identifier} resolve server-side — cùng quy ước
 *   {@link ChuyenXuLyVbdenUseCase}).</li>
 * </ol>
 *
 * <p><b>Bổ sung 14/09/2026 (yêu cầu người dùng "làm luôn chức năng Gắn văn bản trả lời ở modal này
 * luôn") — port {@code processResObj()}/{@code res_obj}</b> ("gắn văn bản trả lời", tab "Sổ văn bản
 * đi"/"Văn bản đi liên quan" trong {@code ChoYKienModal.jsx}, xem {@link VbdenReplyDocumentRef}) —
 * CÙNG cơ chế/lý do đã ghi ở javadoc {@link KetThucXuLyUseCase}: lưu nguyên mảng {@code resObj} vào
 * {@code res_obj} trên bản ghi {@code vbden_res} (chỉ để hiển thị lại), dùng
 * {@code promulgationDate} của phần tử CUỐI làm mốc {@code ngaybanhanh} thay {@code @Today} khi tính
 * {@code remainingDays} (ở CẢ {@link #recomputeStatus} lẫn {@link #forceStatusByDeadline}, đúng
 * Domino thật — {@code ngaybanhanh} là biến CHIA SẺ giữa {@code processResObj()} và
 * {@code capNhatTrangThaiVBden()} sau đó). KHÔNG port ghi ngược {@code documentins_obj} lên văn bản đi
 * — field này write-only, không nơi nào (Domino lẫn FE) đọc lại, xác nhận qua grep toàn bộ 2 repo.</p>
 *
 * <p>Cũng KHÔNG xoá node sơ đồ xử lý — Domino gốc không đụng {@code process_diagrams} ở hành động này
 * (khác {@link ThuHoiVanBanUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatKetQuaXuLyUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_VANTHU_KETTHUC_XULY = "vanthuketthucxuly";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_PROCESSING_FIELD = "user_processing";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String USER_DISTRIBUTOR_FIELD = "user_distributor";
    private static final String USER_PROCESSED_FIELD = "user_processed";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final String INTERNAL_FIELD = "internal";
    private static final String DEADLINE_DATE_FIELD = "deadline_date";
    // 2 gia tri Domino that hardcode o dieu kien "ket thuc toan bo cap nay" (CapNhatVanBanDen.java
    // dong 278) - khong tim thay tai lieu giai thich them y nghia, giu nguyen nhu ban goc.
    private static final String LEVEL_FORCE_CLEAR_1 = "1";
    private static final String LEVEL_FORCE_CLEAR_3 = "3";
    private static final String INTERNAL_TRUE = "1";
    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong" - field "user_*" ma use case nay
    // mutate (khong co request.getFields() chung, field co dinh theo thuat toan).
    private static final Set<String> AUTHOR_TOUCHED_FIELDS = Set.of(
        USER_PROCESS_FIELD, USER_PROCESSING_FIELD, USER_COORDINATE_FIELD, USER_TO_READ_FIELD,
        USER_DISTRIBUTOR_FIELD, USER_PROCESSED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbdenStatusService vbdenStatusService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(CapNhatKetQuaXuLyRequest request) {
        var parentOptional = documentRepository.findById(request.getParentid());
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var parent = parentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(parent.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }
        AppUser actingUser = appUserRepository.findByUsernameAndDeletedOnIsNull(actingUsername).orElse(null);
        if (actingUser == null) {
            return Result.failure("Khong tim thay ho so nguoi dung hien tai");
        }

        LocalDate ngayBanHanh = resolveNgayBanHanh(request.getResObj());
        updateParentDocument(parent, actingUser, ngayBanHanh);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino processResObj(): moi phan tu resObj co promulgation_date hop le se GHI DE ngaybanhanh -
    // chi gia tri phan tu CUOI con ton tai (dung Domino that).
    private LocalDate resolveNgayBanHanh(List<VbdenReplyDocumentRef> resObj) {
        if (resObj == null || resObj.isEmpty()) {
            return null;
        }
        LocalDate result = null;
        for (VbdenReplyDocumentRef ref : resObj) {
            LocalDate parsed = parseDate(ref == null ? null : ref.getPromulgationDate());
            if (parsed != null) {
                result = parsed;
            }
        }
        return result;
    }

    // Domino vanthuketthucxuly() dong 245-300. ngayBanHanh (bo sung 14/09/2026): moc so sanh thay
    // @Today khi tinh remainingDays neu resObj co gan van ban tra loi hop le - xem javadoc lop.
    private void updateParentDocument(BusinessDocument parent, AppUser actingUser, LocalDate ngayBanHanh) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        String identifierVanThu = nullToEmpty(actingUser.getIdentifier());
        String identifierCreator = asString(fields.get(IDENTIFIER_CREATOR_FIELD));
        String internalFlag = asString(fields.get(INTERNAL_FIELD));

        Set<String> candidates = new LinkedHashSet<>(toStringList(fields.get(USER_PROCESS_FIELD)));
        candidates.addAll(toStringList(fields.get(USER_PROCESSING_FIELD)));

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        List<String> userProcessing = toStringList(fields.get(USER_PROCESSING_FIELD));
        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        List<String> userDistributor = toStringList(fields.get(USER_DISTRIBUTOR_FIELD));
        List<String> userProcessed = toStringList(fields.get(USER_PROCESSED_FIELD));

        for (String username : candidates) {
            AppUser candidate = appUserRepository.findByUsernameAndDeletedOnIsNull(username).orElse(null);
            if (candidate == null) {
                continue;
            }
            String identifiersUser = getUserOrganizationProfileUseCase.buildProfileForUser(candidate).getIdentifiers();
            boolean cungDonVi = !identifierVanThu.isBlank() && identifiersUser.contains(identifierVanThu + ";");
            boolean vanBanNgoaiNoiBoCungDonVi = !identifierCreator.isBlank()
                && identifierCreator.equals(identifierVanThu) && !INTERNAL_TRUE.equals(internalFlag);
            if (!cungDonVi && !vanBanNgoaiNoiBoCungDonVi) {
                continue;
            }
            userProcess.remove(username);
            userProcessing.remove(username);
            userCoordinate.remove(username);
            userToRead.remove(username);
            userDistributor.remove(username);
            if (!userProcessed.contains(username)) {
                userProcessed.add(username);
            }
        }

        fields.put(USER_PROCESS_FIELD, userProcess);
        fields.put(USER_PROCESSING_FIELD, userProcessing);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);
        fields.put(USER_TO_READ_FIELD, userToRead);
        fields.put(USER_DISTRIBUTOR_FIELD, userDistributor);
        fields.put(USER_PROCESSED_FIELD, userProcessed);

        parent.replaceDynamicFields(toJson(fields));
        recomputeStatus(parent, fields, userProcess, userProcessing, userProcessed, ngayBanHanh);

        // Domino dong 278-286: "ket thuc toan bo" o cap nay - ep xoa sach 2 field dang xu ly + GHI DE
        // status vua tinh o tren, KHONG dieu kien theo con ai dang xu ly hay khong.
        String level = nullToEmpty(
            getUserOrganizationProfileUseCase.buildProfileForUser(actingUser).getLevel());
        boolean forceClear = !INTERNAL_TRUE.equals(internalFlag)
            && (LEVEL_FORCE_CLEAR_3.equals(level) || LEVEL_FORCE_CLEAR_1.equals(level));
        if (forceClear) {
            // Bugfix 14/09/2026 (báo lỗi người dùng: văn bản biến mất khỏi MỌI danh sách sau "Cập
            // nhật kết quả xử lý" - kiểm tra cụ thể văn bản aea34349-... tài khoản demo2). Nguyên
            // nhân: ứng viên KHÔNG khớp "cùng đơn vị"/"văn bản ngoài nội bộ cùng đơn vị" với văn thư
            // (vd. demo2 khác đơn vị văn thư skhcn) bị vòng lặp trên BỎ QUA (không thêm vào
            // user_processed), rồi bị xoá sạch khỏi user_process/user_processing NGAY DƯỚI ĐÂY hoàn
            // toàn KHÔNG điều kiện - mất dấu vết ở CẢ 5 field (user_process/user_processing/
            // user_coordinate/user_to_read/user_distributor/user_processed), nên KHÔNG còn xuất hiện
            // ở bất kỳ view cá nhân nào (mọi view - kể cả "cuacanhan" gộp 9 field - đều khoá theo tên
            // đăng nhập trong 1 trong các field động này, xem ViewQueryService). Đây là lỗi CHUNG, áp
            // dụng cho MỌI văn bản/người dùng đi qua đúng tổ hợp điều kiện này (văn thư level 1/3,
            // văn bản không nội bộ, có người xử lý khác đơn vị với văn thư) - KHÔNG phải lỗi dữ liệu
            // riêng 1 văn bản, nên sửa tại đây (áp dụng ngay cho MỌI lần gọi sau) thay vì sửa tay 1
            // bản ghi. Domino gốc port verbatim cũng "xoá sạch" y hệt (dòng 278-286) - nhưng đúng ý
            // định nghiệp vụ phải là "cấp trên chốt xong TOÀN BỘ ở cấp này", tức NHỮNG AI CÒN LẠI
            // (vòng lặp trên chưa kịp xử lý) coi như CŨNG đã xử lý xong đợt này, không phải "quên hẳn"
            // - đưa họ vào user_processed TRƯỚC KHI xoá, giữ đúng ngữ nghĩa "kết thúc" mà KHÔNG làm
            // văn bản biến mất khỏi mọi danh sách.
            Set<String> stillPending = new LinkedHashSet<>(userProcess);
            stillPending.addAll(userProcessing);
            for (String username : stillPending) {
                if (!userProcessed.contains(username)) {
                    userProcessed.add(username);
                }
            }
            fields.put(USER_PROCESS_FIELD, new ArrayList<String>());
            fields.put(USER_PROCESSING_FIELD, new ArrayList<String>());
            fields.put(USER_PROCESSED_FIELD, userProcessed);
            forceStatusByDeadline(parent, fields, ngayBanHanh);
        }

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Bugfix 04/09/2026 (cung phat hien voi KetThucXuLyUseCase, bao loi nguoi dung) - doc lai truc
    // tiep TDDocumentUlties.tinhSoNgayConLai() that (dong 2188-2215): BAT KY LOI NAO khi tinh so ngay
    // con lai (thieu/khong parse duoc deadline_date) -> tra ve 0 (KHONG bo qua/giu nguyen status cu
    // nhu truoc day) - capNhatTrangThaiVBden() (dong 2111-2186, goi tai vanthuketthucxuly() dong 277)
    // LUON ghi de "status" khong dieu kien.
    private void recomputeStatus(
            BusinessDocument parent, Map<String, Object> fields,
            List<String> userProcess, List<String> userProcessing, List<String> userProcessed,
            LocalDate ngayBanHanh) {
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        boolean hasPendingAssignee = !userProcess.isEmpty() || !userProcessing.isEmpty();
        boolean hasProcessed = !userProcessed.isEmpty();
        int remainingDays = deadlineDate != null
            ? vbdenStatusService.computeRemainingDays(deadlineDate, ngayBanHanh)
            : 0;
        String newStatus = vbdenStatusService.determineStatus(remainingDays, hasPendingAssignee, hasProcessed);
        parent.update(null, null, newStatus, null, null, null, null);
    }

    // Domino: status = int_songayconlai<0 ? "3" : "4" (luon coi la DA xu ly xong, khong doc lai cac
    // field khac - xem CapNhatVanBanDen.java dong 278-286). Bugfix 04/09/2026: dong 279 that goi
    // TDDocumentUlties.tinhSoNgayConLai() truc tiep - BAT KY LOI NAO (thieu/khong parse duoc
    // deadline_date) -> tra ve 0, VAN tiep tuc ghi status ("4" vi 0 khong am) - KHONG bo qua nhu truoc.
    private void forceStatusByDeadline(BusinessDocument parent, Map<String, Object> fields, LocalDate ngayBanHanh) {
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        int remainingDays = deadlineDate != null
            ? vbdenStatusService.computeRemainingDays(deadlineDate, ngayBanHanh)
            : 0;
        String newStatus = remainingDays < 0
            ? VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY : VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY;
        parent.update(null, null, newStatus, null, null, null, null);
    }

    // Domino TaoQTXL() (qua CreateOrUpdateDocument(), chay truoc switch-case): parentid/action/
    // opinion/creator/position - dung ten field Java thuc su dung, cung quy uoc ChuyenXuLyVbdenUseCase.
    private Result<UUID> createProcessingRecord(CapNhatKetQuaXuLyRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_VANTHU_KETTHUC_XULY);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_VANTHU_KETTHUC_XULY);
        childFields.put("nguoi_thuc_hien", actingUsername);
        String displayCreator = request.getCreatorName() != null && !request.getCreatorName().isBlank()
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        addResObjIfPresent(childFields, request.getResObj());
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    // Bo sung 14/09/2026 - xem javadoc lop (khong ghi documentins_obj len vbdi, chi luu audit tren
    // chinh ban ghi vbden_res, dung Ketquaxuly.jsx#build_res_obj doc lai qua "code").
    private void addResObjIfPresent(Map<String, String> childFields, List<VbdenReplyDocumentRef> resObj) {
        if (resObj == null || resObj.isEmpty()) {
            return;
        }
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        for (VbdenReplyDocumentRef ref : resObj) {
            if (ref == null) {
                continue;
            }
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("id", ref.getId() != null ? ref.getId().toString() : "");
            node.put("code", ref.getCode() != null ? ref.getCode() : "");
            // 05/10/2026: giữ "action" (FE mở chi tiết theo key này) - xem VbdenReplyDocumentRef#action.
            node.put("action", ref.resolvedAction());
            if (ref.getPromulgationDate() != null && !ref.getPromulgationDate().isBlank()) {
                node.put("promulgationDate", ref.getPromulgationDate());
            }
            if (ref.getUnitPublish() != null && !ref.getUnitPublish().isBlank()) {
                node.put("unitPublish", ref.getUnitPublish());
            }
            array.add(node);
        }
        if (!array.isEmpty()) {
            childFields.put("res_obj", array.toString());
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

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value != null) {
            for (String part : value.toString().split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DEADLINE_DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
