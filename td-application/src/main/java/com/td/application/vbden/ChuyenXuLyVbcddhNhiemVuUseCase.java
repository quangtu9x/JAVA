package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.vbcddh.AddVbcddhDynamicDocumentUseCase;
import com.td.application.vbcddh.VbcddhDocumentRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cutover 17/09/2026 (yêu cầu người dùng "vbden/xuly/xemdebiet -> Chuyển xem để biết -> Chuyển xử lý
 * đang gọi qlvb/vbden/ChuyenXuLyVBCDDH?databasetype=vbden -> port sang java"): tương đương Domino thật
 * {@code ChuyenXuLyVBCDDH.java} (KHÁC {@link GuiSangVbcddhUseCase} — use case đó TẠO nhiệm vụ vbcddh
 * lần đầu; use case này chỉ ĐỒNG BỘ LẠI nhiệm vụ vbcddh ĐÃ TỒN TẠI mỗi khi văn bản đến gốc được
 * "Chuyển xử lý" (phân công lại) lần nữa — FE gọi cả 2 endpoint tuỳ checkbox "Trực tiếp theo dõi xử
 * lý", xem {@code SubFormChuyenXuLy.jsx#handleChuyenCDDH}).
 *
 * <p><b>Thuật toán thật</b> ({@code ChuyenXuLyVBCDDH.java#ChuyenXuLyNhiemVu()/ChuyenXuLy()/LuuVet()}):</p>
 * <ol>
 *   <li>Gom {@code listChuTri} thành 1 chuỗi {@code chutris}/{@code userChutris} (nối ";", KHÔNG khử
 *   trùng — đúng Domino dòng 218-223); {@code chuyenDonViChuTri=true} nếu CÓ ÍT NHẤT 1 phần tử mang
 *   {@code identifier} (giao đơn vị) — 1 khi đã true thì giữ nguyên cho tới hết vòng lặp.</li>
 *   <li>Gom {@code listPhoiHop}: MỌI phần tử đều cộng vào {@code coordinator}/{@code userCoordinator}
 *   (dùng ghi vết {@code LuuVet}); phần tử KHÔNG {@code identifier} (giao thẳng cá nhân) → cộng vào
 *   {@code chuyenVienPhoiHop}/{@code Hide}; phần tử CÓ {@code identifier} (giao đơn vị) → cộng vào
 *   {@code donViPhoiHop}/{@code Hide}, kèm đại diện {@code receiver}/{@code userReceiver} (nếu có) →
 *   CŨNG cộng vào {@code chuyenVienPhoiHop}/{@code Hide} (đại diện đơn vị nhận việc).</li>
 *   <li>Tìm TOÀN BỘ nhiệm vụ vbcddh có {@code idvbden} = văn bản đến này (repository
 *   {@link VbcddhDocumentRepository#findByAnyFieldContainsMember}, form {@code nhiemvu}/
 *   {@code nhiemvu_chuaduyet} — đúng khoá {@code vwNhiem_Vu_By_Idvanban} thật).</li>
 *   <li>VỚI MỖI nhiệm vụ tìm được: nếu người đang thao tác ĐANG là người/đơn vị được giao thực hiện
 *   ({@code donvithuchienhide} khớp identifier-theo-cấp của người thao tác HOẶC khớp username, HOẶC
 *   {@code chuyenvienthuchienhide} chứa username) — GHI ĐÈ {@code donvithuchien(hide)}/
 *   {@code chuyenvienthuchien(hide)} (nếu {@code listChuTri} không rỗng) và
 *   {@code donviphoihop(hide)}/{@code chuyenvienphoihop(hide)} (nếu {@code listPhoiHop} không rỗng),
 *   thêm người thao tác vào {@code nguoichuyenxulyhide} (khử trùng, không phân biệt hoa/thường), rồi
 *   ghi 1 bản ghi tiến độ {@code vbcddh_res} (form {@code tiendonhiemvu}) — TÁI DÙNG kiến trúc
 *   {@code CapNhatTienDoUseCase} đã có.</li>
 *   <li>Cuối cùng, UNION lại (khử trùng, đúng {@code addValueToString(checkDuplicate=true)}) 4 cặp
 *   field ({@code donvithuchien/hide}, {@code chuyenvienthuchien/hide}, {@code donviphoihop/hide},
 *   {@code chuyenvienphoihop/hide}) từ TOÀN BỘ nhiệm vụ (không chỉ nhiệm vụ vừa reassign) và ghi đè lên
 *   văn bản CĐĐH cha ({@code idvbcd} trên chính văn bản đến — document {@code documentType=vbcddh} qua
 *   hạ tầng chung {@link DocumentRepository}, KHÔNG phải {@code vbcddh_documents}, xem javadoc
 *   {@link GuiSangVbcddhUseCase}) NẾU có thay đổi.</li>
 * </ol>
 *
 * <p><b>Đơn giản hoá CÓ CHỦ ĐÍCH (cùng nguyên tắc {@link GuiSangVbcddhUseCase})</b>:</p>
 * <ul>
 *   <li>KHÔNG tra cứu lại {@code vwuserbyusername} cho đại diện {@code receiver}/{@code userReceiver} —
 *   TIN TRỰC TIẾP giá trị client gửi.</li>
 *   <li>KHÔNG tạo các field {@code *_obj} (snapshot JSON) — không có bằng chứng FE đọc lại.</li>
 *   <li>KHÔNG xoá cache Redis kiểu Domino ({@code TDRedisConnection.deleteCache}) — Java dùng
 *   {@link DocumentCacheService#evict}/{@code evictAllListCaches} đã có sẵn cho từng document sửa.</li>
 *   <li>Điều kiện khớp "người đang sở hữu nhiệm vụ" dùng so khớp CÓ BAO BỌC ";" (tránh khớp nhầm chuỗi
 *   con) thay vì {@code lastIndexOf} thô của Domino — đúng tiền lệ cải tiến đã ghi ở
 *   {@code VbcddhDocumentRepository#findByFormAndAnyFieldContainsMember}, không phải suy đoán mới.</li>
 * </ul>
 *
 * <p>Best-effort: lỗi xảy ra khi đồng bộ 1 nhiệm vụ KHÔNG được để chặn toàn bộ request (đúng Domino
 * nuốt exception trong {@code ChuyenXuLy()}/{@code LuuVet()}) — log cảnh báo rồi tiếp tục nhiệm vụ
 * khác.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChuyenXuLyVbcddhNhiemVuUseCase {

    static final String VBCDDH_DOCUMENT_TYPE = "vbcddh";
    static final String VBCDDH_RES_DOCUMENT_TYPE = "vbcddh_res";
    private static final List<String> FORM_NHIEMVU = List.of("nhiemvu", "nhiemvu_chuaduyet");
    private static final int MAX_NHIEMVU_CANDIDATES = 500;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final String FALLBACK_USERNAME = "system";
    private static final String DEFAULT_YKIEN = "Giao chuyên viên thực hiện nhiệm vụ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String[] ROLLUP_FIELDS = {
        "donvithuchien", "donvithuchienhide", "chuyenvienthuchien", "chuyenvienthuchienhide",
        "donviphoihop", "donviphoihophide", "chuyenvienphoihop", "chuyenvienphoihophide",
    };

    private final DocumentRepository documentRepository;
    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;

    public Result<UUID> execute(ChuyenXuLyVbcddhNhiemVuRequest request) {
        var sourceOptional = documentRepository.findById(request.getId());
        if (sourceOptional.isEmpty() || sourceOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var source = sourceOptional.get();
        Map<String, Object> sourceFields = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        AppUser actingUser = appUserRepository.findByUsernameAndDeletedOnIsNull(actingUsername).orElse(null);
        String fullName = actingUser != null && actingUser.getFullName() != null && !actingUser.getFullName().isBlank()
            ? actingUser.getFullName() : actingUsername;
        String actingIdentifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().orElse(null);
        String ykien = request.getYKien() != null && !request.getYKien().isBlank() ? request.getYKien() : DEFAULT_YKIEN;

        ChuTriAggregation chuTri = aggregateChuTri(request.getListChuTri());
        PhoiHopAggregation phoiHop = aggregatePhoiHop(request.getListPhoiHop());

        var candidates = vbcddhDocumentRepository.findByAnyFieldContainsMember(
            VBCDDH_DOCUMENT_TYPE, List.of("idvbden"), request.getId().toString(),
            PageRequest.of(0, MAX_NHIEMVU_CANDIDATES));
        List<VbcddhDocument> nhiemVuList = candidates.getContent().stream()
            .filter(doc -> !doc.isDeleted())
            .filter(doc -> FORM_NHIEMVU.contains(
                asString(dynamicDocumentFieldsService.parseDynamicFields(doc.getDynamicFieldsJson()).get("form"))))
            .toList();

        boolean anyReassigned = false;
        for (VbcddhDocument nhiemVu : nhiemVuList) {
            try {
                if (reassignIfOwnedByActingUser(
                        nhiemVu, request, chuTri, phoiHop, actingUsername, fullName, actingIdentifier, ykien)) {
                    anyReassigned = true;
                }
            } catch (Exception ex) {
                log.warn("Khong dong bo duoc nhiem vu CDDH {} tu van ban den {}: {}",
                    nhiemVu.getId(), request.getId(), ex.getMessage());
            }
        }

        if (anyReassigned) {
            String idvbcd = asString(sourceFields.get("idvbcd"));
            if (idvbcd != null && !idvbcd.isBlank()) {
                try {
                    rollupToParentVbcddh(UUID.fromString(idvbcd), nhiemVuList);
                } catch (Exception ex) {
                    log.warn("Khong rollup duoc van ban CDDH {} tu van ban den {}: {}",
                        idvbcd, request.getId(), ex.getMessage());
                }
            }
        }

        return Result.success(request.getId());
    }

    // Domino: dieu kien khop dong nhiemvu.java dong 379-389 - nguoi dang thao tac dang la nguoi/don vi
    // duoc giao thuc hien nhiem vu nay.
    private boolean reassignIfOwnedByActingUser(
            VbcddhDocument nhiemVu, ChuyenXuLyVbcddhNhiemVuRequest request, ChuTriAggregation chuTri,
            PhoiHopAggregation phoiHop, String actingUsername, String fullName, String actingIdentifier,
            String ykien) {
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(nhiemVu.getDynamicFieldsJson());
        String donvithuchienhide = asString(fields.get("donvithuchienhide"));
        String chuyenvienthuchienhide = asString(fields.get("chuyenvienthuchienhide"));
        boolean owned = equalsIgnoreCase(donvithuchienhide, actingIdentifier)
            || equalsIgnoreCase(donvithuchienhide, actingUsername)
            || containsMember(chuyenvienthuchienhide, actingUsername);
        if (!owned) {
            return false;
        }

        Map<String, Object> updated = new LinkedHashMap<>(fields);
        boolean changed = false;

        if (!request.getListChuTri().isEmpty()) {
            if (chuTri.chuyenDonViChuTri()) {
                updated.put("donvithuchien", chuTri.chutris());
                updated.put("donvithuchienhide", chuTri.userChutris());
                if (!chuTri.chutriReceivers().isEmpty() && !chuTri.chutriUserReceivers().isEmpty()) {
                    updated.put("chuyenvienthuchien", chuTri.chutriReceivers());
                    updated.put("chuyenvienthuchienhide", chuTri.chutriUserReceivers());
                }
            } else {
                updated.put("chuyenvienthuchien", chuTri.chutris());
                updated.put("chuyenvienthuchienhide", chuTri.userChutris());
            }
            changed = true;
        }
        if (!request.getListPhoiHop().isEmpty()) {
            updated.put("donviphoihop", phoiHop.donViPhoiHop());
            updated.put("donviphoihophide", phoiHop.donViPhoiHopHide());
            updated.put("chuyenvienphoihop", phoiHop.chuyenVienPhoiHop());
            updated.put("chuyenvienphoihophide", phoiHop.chuyenVienPhoiHopHide());
            changed = true;
        }
        String nguoichuyenxulyhide = asString(fields.get("nguoichuyenxulyhide"));
        if (!containsMember(nguoichuyenxulyhide, actingUsername)) {
            updated.put("nguoichuyenxulyhide", isBlank(nguoichuyenxulyhide)
                ? actingUsername : nguoichuyenxulyhide + ";" + actingUsername);
            changed = true;
        }

        if (!changed) {
            return false;
        }

        var before = buildSnapshot(nhiemVu);
        nhiemVu.replaceDynamicFields(toJson(updated));
        var saved = vbcddhDocumentRepository.save(nhiemVu);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        createProgressRecord(saved.getId(), chuTri, phoiHop, fullName, actingUsername, ykien);
        return true;
    }

    // Domino: LuuVet() - tao 1 ban ghi vbcddh_res form=tiendonhiemvu, tai dung kien truc
    // CapNhatTienDoUseCase#createProgressRecord.
    private void createProgressRecord(
            UUID idNhiemVu, ChuTriAggregation chuTri, PhoiHopAggregation phoiHop,
            String fullName, String actingUsername, String ykien) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(AddDynamicDocumentRequest.labelTitle(ykien)); // 19/10/2026: ý kiến dài > 300 ký tự
        childRequest.setDocumentType(VBCDDH_RES_DOCUMENT_TYPE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("form", "tiendonhiemvu");
        fields.put("chuyenxuly", "1");
        fields.put("creator", fullName);
        fields.put("user_creator", actingUsername);
        fields.put("idnhiemvu", idNhiemVu.toString());
        if (!chuTri.chutris().isEmpty()) {
            fields.put("receiver", chuTri.chutris());
            fields.put("user_receiver", chuTri.userChutris());
        }
        if (!phoiHop.coordinator().isEmpty()) {
            fields.put("coordinator", phoiHop.coordinator());
            fields.put("user_coordinator", phoiHop.userCoordinator());
        }
        fields.put("ykien", ykien);
        childRequest.setFields(fields);

        var result = addVbcddhDynamicDocumentUseCase.execute(childRequest);
        if (!result.isSuccess()) {
            log.warn("Khong ghi duoc vet tien do nhiem vu {}: {}", idNhiemVu, result.getError());
        }
    }

    // Domino dong 342-463: union (khu trung) 8 field tren TOAN BO nhiem vu tim duoc, ghi de len van
    // ban CDDH cha (idvbcd) neu co thay doi.
    private void rollupToParentVbcddh(UUID idvbcd, List<VbcddhDocument> nhiemVuList) {
        var parentOptional = documentRepository.findById(idvbcd);
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()) {
            return;
        }
        var parent = parentOptional.get();
        Map<String, Object> parentFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());

        Map<String, Set<String>> unions = new LinkedHashMap<>();
        for (String field : ROLLUP_FIELDS) {
            unions.put(field, new LinkedHashSet<>());
        }
        for (VbcddhDocument nhiemVu : nhiemVuList) {
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(nhiemVu.getDynamicFieldsJson());
            for (String field : ROLLUP_FIELDS) {
                splitToSet(asString(fields.get(field)), unions.get(field));
            }
        }

        Map<String, Object> updated = new LinkedHashMap<>(parentFields);
        boolean changed = false;
        for (String field : ROLLUP_FIELDS) {
            String newValue = String.join(";", unions.get(field));
            String oldValue = asString(parentFields.get(field));
            if (!newValue.equals(isBlank(oldValue) ? "" : oldValue)) {
                updated.put(field, newValue);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        var before = buildSnapshot(parent);
        parent.replaceDynamicFields(toJson(updated));
        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino dong 199-256: gom listChuTri thanh 1 chuoi (noi ";", KHONG khu trung).
    private ChuTriAggregation aggregateChuTri(List<NhiemVuCdDhAssigneeDto> listChuTri) {
        StringBuilder chutris = new StringBuilder();
        StringBuilder userChutris = new StringBuilder();
        StringBuilder chutriReceivers = new StringBuilder();
        StringBuilder chutriUserReceivers = new StringBuilder();
        boolean chuyenDonViChuTri = false;

        for (NhiemVuCdDhAssigneeDto item : listChuTri) {
            if (item == null) {
                continue;
            }
            boolean hasIdentifier = !isBlank(item.getIdentifier());
            String key = hasIdentifier ? item.getIdentifier() : item.getUsername();
            if (hasIdentifier) {
                chuyenDonViChuTri = true;
            }
            if (isBlank(item.getName()) || isBlank(key)) {
                continue;
            }
            appendJoin(chutris, item.getName());
            appendJoin(userChutris, key);
            if (hasIdentifier && !isBlank(item.getReceiver()) && !isBlank(item.getUserReceiver())) {
                appendJoin(chutriReceivers, item.getReceiver());
                appendJoin(chutriUserReceivers, item.getUserReceiver());
            }
        }
        return new ChuTriAggregation(chuyenDonViChuTri, chutris.toString(), userChutris.toString(),
            chutriReceivers.toString(), chutriUserReceivers.toString());
    }

    // Domino dong 257-321 + coordinator dong 257-265: gom listPhoiHop.
    private PhoiHopAggregation aggregatePhoiHop(List<NhiemVuCdDhAssigneeDto> listPhoiHop) {
        StringBuilder coordinator = new StringBuilder();
        StringBuilder userCoordinator = new StringBuilder();
        StringBuilder donViPhoiHop = new StringBuilder();
        StringBuilder donViPhoiHopHide = new StringBuilder();
        StringBuilder chuyenVienPhoiHop = new StringBuilder();
        StringBuilder chuyenVienPhoiHopHide = new StringBuilder();

        for (NhiemVuCdDhAssigneeDto item : listPhoiHop) {
            if (item == null) {
                continue;
            }
            boolean hasIdentifier = !isBlank(item.getIdentifier());
            String key = hasIdentifier ? item.getIdentifier() : item.getUsername();
            boolean giaoNguoiPhoiHop = !hasIdentifier && !isBlank(item.getUsername());

            appendJoin(coordinator, item.getName());
            appendJoin(userCoordinator, key);

            if (giaoNguoiPhoiHop) {
                appendJoin(chuyenVienPhoiHop, item.getName());
                appendJoin(chuyenVienPhoiHopHide, key);
            } else {
                appendJoin(donViPhoiHop, item.getName());
                appendJoin(donViPhoiHopHide, key);
                if (!isBlank(item.getReceiver()) && !isBlank(item.getUserReceiver())) {
                    appendJoin(chuyenVienPhoiHop, item.getReceiver());
                    appendJoin(chuyenVienPhoiHopHide, item.getUserReceiver());
                }
            }
        }
        return new PhoiHopAggregation(coordinator.toString(), userCoordinator.toString(),
            donViPhoiHop.toString(), donViPhoiHopHide.toString(),
            chuyenVienPhoiHop.toString(), chuyenVienPhoiHopHide.toString());
    }

    private record ChuTriAggregation(
        boolean chuyenDonViChuTri, String chutris, String userChutris,
        String chutriReceivers, String chutriUserReceivers) {
    }

    private record PhoiHopAggregation(
        String coordinator, String userCoordinator,
        String donViPhoiHop, String donViPhoiHopHide,
        String chuyenVienPhoiHop, String chuyenVienPhoiHopHide) {
    }

    private void appendJoin(StringBuilder target, String value) {
        if (isBlank(value)) {
            return;
        }
        if (target.length() > 0) {
            target.append(";");
        }
        target.append(value);
    }

    private void splitToSet(String semicolonList, Set<String> target) {
        if (isBlank(semicolonList)) {
            return;
        }
        for (String part : semicolonList.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    // Bao boc ";" tranh khop nham chuoi con - dung tien le VbcddhDocumentRepository (khac Domino
    // lastIndexOf tho o ChuyenXuLyVBCDDH.java goc).
    private boolean containsMember(String semicolonList, String member) {
        if (isBlank(semicolonList) || isBlank(member)) {
            return false;
        }
        String bounded = ";" + semicolonList.trim() + ";";
        return bounded.toLowerCase().contains((";" + member.trim() + ";").toLowerCase());
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private Map<String, Object> buildSnapshot(VbcddhDocument document) {
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
