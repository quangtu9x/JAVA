package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * N19 (04/09/2026 — "Tạo mới văn bản dự thảo", nút "Tạo dự thảo"): tương đương 1 PHẦN
 * {@code CapNhatVanBanDen.java} thật, hàm {@code taoduthao_hoso_phieutrinh()} (dòng 309-332), CHỈ
 * cho action {@code taoduthao} (xem lý do thu hẹp phạm vi bên dưới — trong Domino hàm này dùng
 * CHUNG cho 4 action {@code taoduthao}/{@code taophieutrinh}/{@code ganduthaotraloi}/{@code taohoso}).
 *
 * <p><b>Thuật toán ({@code taoduthao_hoso_phieutrinh()} thật)</b>:</p>
 * <ol>
 *   <li>Đọc {@code user_process} trên CHÍNH văn bản đến gốc (không phải bản ghi {@code vbden_res} mới
 *   tạo) — nếu RỖNG thì KHÔNG làm gì cả (không lưu, không đổi trạng thái xử lý) — giữ nguyên điều
 *   kiện lạ này của bản gốc dù không rõ lý do (có thể chỉ để phòng thủ trường hợp dữ liệu hỏng).</li>
 *   <li>Nếu KHÔNG rỗng: loại {@code actingUsername} khỏi {@code user_process} và {@code user_returned}
 *   (không điều kiện — loại dù có mặt hay không, no-op nếu không có), thêm {@code actingUsername} vào
 *   {@code user_processing} (không trùng lặp) — đánh dấu "người này đang SOẠN dự thảo trả lời", khác
 *   trạng thái "đang CHỜ xử lý" ({@code user_process}).</li>
 *   <li>Tạo bản ghi {@code vbden_res} mới ({@code action="taoduthao"}, {@code opinion}, {@code creator}/
 *   {@code position} hiển thị, {@code identifier} resolve server-side, kèm {@code res_obj} tham chiếu
 *   văn bản dự thảo vừa tạo — cùng quy ước {@link CapNhatKetQuaXuLyUseCase}/{@link KetThucXuLyUseCase}).
 *   Domino ghi bản ghi này ở phần MỞ ĐẦU dùng chung cho MỌI action ({@code CreateOrUpdateDocument()},
 *   chạy TRƯỚC switch-case) — Java gộp vào 1 bước cho gọn, không đổi kết quả.</li>
 * </ol>
 *
 * <p><b>Thu hẹp phạm vi CÓ CHỦ ĐÍCH (chỉ port {@code action=taoduthao})</b>: hàm Domino thật dùng
 * CHUNG logic này cho 3 action khác — {@code taophieutrinh} ("tạo phiếu trình", module phiếu trình),
 * {@code ganduthaotraloi} ("gán dự thảo có sẵn làm trả lời", vẫn đang gọi endpoint Domino cũ ở
 * {@code DuThaoActionModal.jsx} dòng ~571), {@code taohoso} ("tạo hồ sơ", module hồ sơ công việc) —
 * CHƯA xác nhận các module này đã port sang Java hay chưa nên KHÔNG mở rộng theo — chỉ khi có yêu cầu
 * cụ thể mới xét thêm (tránh đoán). Do đó action luôn CỐ ĐỊNH {@code "taoduthao"}, không nhận từ FE.</p>
 *
 * <p><b>CHƯA port (ghi nhận có chủ đích, không suy đoán thêm — xem
 * {@code docs/analysis/02-checklist-port-vbden-con-lai.md})</b>:</p>
 * <ul>
 *   <li>{@code TDInit.InitGiam} (giảm counter badge thông báo qua Redis) — thuần cơ chế thông báo/cache,
 *   không có khái niệm tương đương và KHÔNG cần port (cùng kết luận R11 đã ghi ở nhiều use case khác).</li>
 *   <li>Bước lưu 2 lần trên bản ghi {@code vbden_res} mới tạo ({@code parentid_send}, ACL
 *   reader/author theo {@code identifiers} — {@code CreateOrUpdateDocument()} thật, xem javadoc/kết
 *   quả điều tra) — {@code parentid_send} không có bằng chứng FE Java đọc lại, ACL Notes không có khái
 *   niệm tương đương trong Postgres.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaoDuThaoVBdenUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_TAO_DU_THAO = "taoduthao";
    // 06/10/2026 (cutover FE phieu trinh): Domino dung CHUNG logic nay cho "taophieutrinh" - mo rong
    // whitelist (cac action con lai ganduthaotraloi/taohoso van chua mo).
    public static final String ACTION_TAO_PHIEU_TRINH = "taophieutrinh";
    private static final java.util.Set<String> ALLOWED_ACTIONS = java.util.Set.of(ACTION_TAO_DU_THAO, ACTION_TAO_PHIEU_TRINH);

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_RETURNED_FIELD = "user_returned";
    private static final String USER_PROCESSING_FIELD = "user_processing";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_RETURNED_FIELD, USER_PROCESSING_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(TaoDuThaoVBdenRequest request) {
        String action = request.getAction() == null || request.getAction().isBlank()
            ? ACTION_TAO_DU_THAO : request.getAction().trim().toLowerCase();
        if (!ALLOWED_ACTIONS.contains(action)) {
            return Result.failure("Action khong ho tro: " + request.getAction());
        }
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

        updateParentDocument(parent, actingUsername);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino taoduthao_hoso_phieutrinh() dong 309-332.
    private void updateParentDocument(BusinessDocument parent, String actingUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        // Domino: if(!user_process.equals("")) { ... } — chỉ mutate khi field ĐANG có giá trị (kiểm
        // tra TRƯỚC khi loại actingUsername ra, không phải "actingUsername có trong đó hay không").
        if (userProcess.isEmpty()) {
            return;
        }

        var before = buildSnapshot(parent);
        List<String> userReturned = toStringList(fields.get(USER_RETURNED_FIELD));
        List<String> userProcessing = toStringList(fields.get(USER_PROCESSING_FIELD));

        userProcess.remove(actingUsername);
        userReturned.remove(actingUsername);
        if (!userProcessing.contains(actingUsername)) {
            userProcessing.add(actingUsername);
        }

        fields.put(USER_PROCESS_FIELD, userProcess);
        fields.put(USER_RETURNED_FIELD, userReturned);
        fields.put(USER_PROCESSING_FIELD, userProcessing);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino TaoQTXL() (qua CreateOrUpdateDocument(), chay truoc switch-case): parentid/action/
    // opinion/creator/position/res_obj - dung ten field Java thuc su dung, cung quy uoc
    // CapNhatKetQuaXuLyUseCase/KetThucXuLyUseCase.
    private Result<UUID> createProcessingRecord(TaoDuThaoVBdenRequest request, String actingUsername) {
        String action = request.getAction() == null || request.getAction().isBlank()
            ? ACTION_TAO_DU_THAO : request.getAction().trim().toLowerCase();
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(action);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", action);
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
        addResObjIfPresent(childFields, request);
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    private void addResObjIfPresent(Map<String, String> childFields, TaoDuThaoVBdenRequest request) {
        if (request.getResObjId() == null) {
            return;
        }
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("id", request.getResObjId().toString());
        node.put("dbpath", request.getResObjDbpath() != null ? request.getResObjDbpath() : "");
        node.put("code", request.getResObjCode() != null ? request.getResObjCode() : "");
        childFields.put("res_obj", node.toString());
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

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
