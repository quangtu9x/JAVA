package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotifier;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * N18 (04/09/2026 — bổ sung theo yêu cầu người dùng: "nút Lấy lại VB: chưa port sang JAVA"): tương
 * đương {@code VanThuThuHoiVanBan.java} thật (action {@code laylaivanban}) — nút "Lấy lại VB"
 * ({@code VBDenButtonActionVT.jsx#btnLayLaiVanBan}, chỉ hiện với người có mặt trong {@code author}
 * — xem javadoc {@code ChuyenXuLyVbdenUseCase#accumulateAuthors}).
 *
 * <p><b>Khác {@link ThuHoiVanBanUseCase}</b> (N11, "Thu hồi" — thu hồi TOÀN BỘ người/đơn vị do CHÍNH
 * người thao tác giao qua các lần chuyển xử lý TRƯỚC ĐÓ, dựa trên snapshot {@code _obj}): hành động
 * này ("Lấy lại VB") thu hồi CẢ NHÁNH TỔ CHỨC dưới người thao tác — bất kỳ ai đang xử lý (không cần
 * do chính người này giao) mà thuộc cùng nhánh tổ chức (identifiers chứa identifier của người thao
 * tác) đều bị thu hồi, cộng thêm 1 nhánh ĐẶC BIỆT: nếu văn bản được TẠO trong CHÍNH tổ chức của người
 * thao tác ({@code identifier_creator == identifier}) thì RESET TOÀN BỘ routing (xoá sạch
 * assigner/distributor/process_obj/coordinate_obj/to_read_obj...), coi như văn bản CHƯA từng được
 * phân công.</p>
 *
 * <p><b>Thuật toán ({@code VanThuThuHoiVanBan.java} thật)</b>:</p>
 * <ol>
 *   <li>Gộp {@code user_process}+{@code user_coordinate}+{@code user_to_read}+{@code user_track}
 *   thành 1 danh sách ứng viên. Nếu {@code level} (độ sâu tổ chức) của người thao tác là {@code "1"}
 *   (cấp cao nhất) — thu hồi TẤT CẢ ứng viên KHÔNG điều kiện. Ngược lại, chỉ thu hồi ứng viên có
 *   {@code identifiers} (chuỗi phân cấp tổ chức) CHỨA identifier (phẳng) của người thao tác — cùng
 *   thuật toán tra cứu đã dùng ở {@link CapNhatKetQuaXuLyUseCase}.</li>
 *   <li>Loại các ứng viên bị thu hồi khỏi CẢ 4 field ({@code user_process}/{@code user_coordinate}/
 *   {@code user_to_read}/{@code user_track}) — KHÔNG đụng {@code unit_process}/{@code unit_coordinate}/
 *   {@code unit_to_read} (Domino gốc cũng không đụng).</li>
 *   <li>Nếu {@code identifier_creator} == identifier người thao tác — reset {@code user_distributor}/
 *   {@code user_assigner}/{@code assigner}/{@code distributor}/{@code distributor_opinion}/
 *   {@code distributor_position}/{@code assigner_opinion}/{@code process_obj}/{@code coordinate_obj}/
 *   {@code to_read_obj}/{@code noinhanchutri}/{@code lanhdaochutri}/{@code assigner_delegation_obj}
 *   về rỗng.</li>
 *   <li>Thêm người thao tác vào {@code user_process}+{@code user_recalled}, loại khỏi
 *   {@code user_delivered}. Xoá {@code processed_end_date} nếu có. Tính lại {@code status}
 *   ({@code tinhSoNgayConLai<0 → "1"}, ngược lại {@code "2"} — cùng công thức
 *   {@code determineStatus(remainingDays, hasPendingAssignee=true, hasProcessed=false)} đã dùng ở
 *   {@link XuLyLaiUseCase}, bugfix D.35+04/09 — thiếu {@code deadline_date} → {@code remainingDays=0},
 *   KHÔNG bỏ qua).</li>
 *   <li>Xoá node sơ đồ xử lý ({@link ProcessDiagramService#removeNode}) cho MỖI username vừa bị thu
 *   hồi — hạ tầng có sẵn từ R8.</li>
 *   <li>Tạo 1 bản ghi {@code vbden_res} MỚI ({@code action="laylaivanban"}, {@code opinion}=lý do,
 *   {@code creator}/{@code position} hiển thị, {@code identifier} resolve server-side).</li>
 * </ol>
 *
 * <p><b>Bổ sung 08/09/2026 — giai đoạn 2 {@code vbden.forward_unit}</b> (yêu cầu người dùng: "giai đoạn
 * 1 cơ bản đã xong, làm giai đoạn 2 LayLaiVanBanUseCase/ThuHoiVanBanUseCase"): NGOÀI việc thu hồi cục
 * bộ ở trên, nếu văn bản này đã từng "chuyển xử lý" sang đơn vị khác tenant ({@code forwarded_units},
 * xem javadoc {@code ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant}) thì CASCADE revoke sang
 * TẤT CẢ bản sao đó — xem javadoc {@link PublishForwardUnitRevokeUseCase} về phạm vi (cascade TOÀN BỘ
 * entry còn hiệu lực, không lọc thêm theo nhánh tổ chức — lý do ghi rõ ở đó).</p>
 *
 * <p><b>CHƯA port (ghi nhận có chủ đích, không suy đoán thêm)</b>: (1) Nhánh tra node sơ đồ xử lý
 * theo IDENTIFIER người thao tác ({@code removeNodeChildrenByUsername(root, connection.user_token
 * .identifier)}, dòng 117 thật) — Domino gốc dùng nhánh này khi 1 node sơ đồ được tạo với
 * {@code username} = identifier ĐƠN VỊ (quy ước Domino cũ trước khi có "Người đại diện nhận"); mô
 * hình sơ đồ Java hiện tại KHÔNG BAO GIỜ dùng identifier làm username node (luôn là username thật
 * của người đại diện/người cụ thể — xem {@code ChuyenXuLyVbdenUseCase#recordProcessDiagramNodes}),
 * nên nhánh này không có dữ liệu để khớp — bỏ qua, chỉ dùng nhánh "removeUsers()" (đã port đầy đủ ở
 * trên). (2) Gửi mailin liên-server cho đơn vị NGOÀI hệ thống ({@code get_mailin_by_QTXL}/
 * {@code sendMailinList}) — cùng gap R9 đã ghi nhận ở nhiều use case khác (không có hạ tầng liên-server
 * Domino tương đương). (3) Snapshot lịch sử {@code processListQuyTrinhId} (gộp {@code _obj} từ CÁC
 * bản ghi quá trình xử lý lịch sử liên quan đến node bị ẩn trên sơ đồ) — bản ghi mới chỉ ghi
 * {@code opinion} đơn giản, không gộp lại lịch sử chi tiết từng lần chuyển trước đó (dữ liệu hiển thị
 * phụ, không ảnh hưởng field điều khiển hàng đợi).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LayLaiVanBanUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_LAY_LAI_VAN_BAN = "laylaivanban";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String USER_TRACK_FIELD = "user_track";
    private static final String USER_RECALLED_FIELD = "user_recalled";
    private static final String USER_DELIVERED_FIELD = "user_delivered";
    private static final String IDENTIFIER_CREATOR_FIELD = "identifier_creator";
    private static final String DEADLINE_DATE_FIELD = "deadline_date";
    private static final String PROCESSED_END_DATE_FIELD = "processed_end_date";
    // Domino: gia tri level dac biet ("cap cao nhat") -> thu hoi TAT CA khong dieu kien, xem
    // VanThuThuHoiVanBan.java#findUser_revoked dong 236-237.
    private static final String LEVEL_RECLAIM_ALL = "1";
    // 12 field bi xoa trang khi identifier_creator == identifier nguoi thao tac (VanThuThuHoiVanBan
    // .java dong 121-138) - coi nhu van ban CHUA tung duoc phan cong.
    private static final List<String> ASSIGNER_RESET_FIELDS = List.of(
        "user_distributor", "user_assigner", "assigner", "distributor", "distributor_opinion",
        "distributor_position", "assigner_opinion", "process_obj", "coordinate_obj", "to_read_obj",
        "noinhanchutri", "lanhdaochutri", "assigner_delegation_obj");
    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final Set<String> AUTHOR_TOUCHED_FIELDS = Set.of(
        USER_PROCESS_FIELD, USER_COORDINATE_FIELD, USER_TO_READ_FIELD, USER_TRACK_FIELD,
        USER_RECALLED_FIELD, USER_DELIVERED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbdenStatusService vbdenStatusService;
    private final ProcessDiagramService processDiagramService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    // Bo sung 08/09/2026 (giai doan 2 vbden.forward_unit - xem javadoc lop).
    private final PublishForwardUnitRevokeUseCase publishForwardUnitRevokeUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final RealtimeNotifier realtimeNotifier;

    public Result<UUID> execute(LayLaiVanBanRequest request) {
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

        Set<String> revoked = updateParentDocument(parent, actingUser, actingUsername, request.getOpinion());
        removeDiagramNodes(request.getParentid(), revoked);

        var result = createProcessingRecord(request, actingUsername);
        if (result.isSuccess()) {
            // VanThuThuHoiVanBan.java#sendSocket ("laylaivanban"): báo người bị lấy lại.
            realtimeNotifier.notify(RealtimeNotification.of(DOCUMENT_TYPE, "laylaivanban", parent.getId(),
                    java.util.Objects.toString(dynamicDocumentFieldsService.parseDynamicFields(
                        parent.getDynamicFieldsJson()).get("code"), null))
                .to(revoked)
                .exclude(actingUsername)
                .opinion(request.getOpinion()));
        }
        return result;
    }

    // Domino VanThuThuHoiVanBan.java: removeUsers()+findUser_revoked() (nhanh sơ đồ theo identifier
    // KHÔNG port - xem javadoc lớp) + recallVBden() + reset assigner (dòng 116-139).
    private Set<String> updateParentDocument(
            BusinessDocument parent, AppUser actingUser, String actingUsername, String opinion) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        String identifierUser = nullToEmpty(actingUser.getIdentifier());
        String level = nullToEmpty(getUserOrganizationProfileUseCase.buildProfileForUser(actingUser).getLevel());
        boolean reclaimAll = LEVEL_RECLAIM_ALL.equals(level);

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        List<String> userTrack = toStringList(fields.get(USER_TRACK_FIELD));

        Set<String> candidates = new LinkedHashSet<>(userProcess);
        candidates.addAll(userCoordinate);
        candidates.addAll(userToRead);
        candidates.addAll(userTrack);

        Set<String> revoked = new LinkedHashSet<>();
        for (String username : candidates) {
            if (reclaimAll) {
                revoked.add(username);
                continue;
            }
            AppUser candidate = appUserRepository.findByUsernameAndDeletedOnIsNull(username).orElse(null);
            if (candidate == null) {
                continue;
            }
            String identifiersUser = getUserOrganizationProfileUseCase.buildProfileForUser(candidate).getIdentifiers();
            if (!identifierUser.isBlank() && identifiersUser.contains(identifierUser + ";")) {
                revoked.add(username);
            }
        }

        userProcess.removeAll(revoked);
        userCoordinate.removeAll(revoked);
        userToRead.removeAll(revoked);
        userTrack.removeAll(revoked);
        fields.put(USER_PROCESS_FIELD, userProcess);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);
        fields.put(USER_TO_READ_FIELD, userToRead);
        fields.put(USER_TRACK_FIELD, userTrack);

        String identifierCreator = asString(fields.get(IDENTIFIER_CREATOR_FIELD));
        if (!identifierCreator.isBlank() && identifierCreator.equals(identifierUser)) {
            for (String resetField : ASSIGNER_RESET_FIELDS) {
                fields.put(resetField, "");
            }
        }

        List<String> userProcessAfterReset = toStringList(fields.get(USER_PROCESS_FIELD));
        addOne(userProcessAfterReset, actingUsername);
        fields.put(USER_PROCESS_FIELD, userProcessAfterReset);
        List<String> userRecalled = toStringList(fields.get(USER_RECALLED_FIELD));
        addOne(userRecalled, actingUsername);
        fields.put(USER_RECALLED_FIELD, userRecalled);
        List<String> userDelivered = toStringList(fields.get(USER_DELIVERED_FIELD));
        userDelivered.remove(actingUsername);
        fields.put(USER_DELIVERED_FIELD, userDelivered);
        fields.remove(PROCESSED_END_DATE_FIELD);

        // Bo sung 08/09/2026 (giai doan 2 vbden.forward_unit - xem javadoc lop): cascade sang cac ban
        // sao da "chuyen xu ly" cho don vi khac tenant - PHAI goi TRUOC replaceDynamicFields de co
        // "revoked=true" duoc luu CUNG 1 giao dich voi thay doi routing chinh.
        publishForwardUnitRevokeUseCase.cascadeRevoke(
            fields, parent.getId(), actingUsername, opinion, ACTION_LAY_LAI_VAN_BAN);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));
        recomputeStatus(parent, fields);

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        return revoked;
    }

    // Bugfix 04/09/2026 (cung mau hinh da ap dung o XuLyLaiUseCase/CapNhatKetQuaXuLyUseCase): thieu
    // deadline_date -> remainingDays=0 (KHONG bo qua/giu status cu).
    private void recomputeStatus(BusinessDocument parent, Map<String, Object> fields) {
        LocalDate deadlineDate = parseDate(asString(fields.get(DEADLINE_DATE_FIELD)));
        int remainingDays = deadlineDate != null
            ? vbdenStatusService.computeRemainingDays(deadlineDate, null)
            : 0;
        String newStatus = vbdenStatusService.determineStatus(remainingDays, true, false);
        parent.update(null, null, newStatus, null, null, null, null);
    }

    private void removeDiagramNodes(UUID parentId, Set<String> revokedUsernames) {
        for (String username : revokedUsernames) {
            try {
                processDiagramService.removeNode(parentId, DOCUMENT_TYPE, username);
            } catch (Exception ex) {
                log.warn("Khong the xoa node so do xu ly cho van ban {} (username {}): {}",
                    parentId, username, ex.getMessage());
            }
        }
    }

    // Domino createDocQTXL() (dong 391-408, action="laylaivanban").
    private Result<UUID> createProcessingRecord(LayLaiVanBanRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_LAY_LAI_VAN_BAN);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_LAY_LAI_VAN_BAN);
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
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
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

    private void addOne(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
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
