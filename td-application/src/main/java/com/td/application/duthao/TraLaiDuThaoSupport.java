package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.SoanThaoVbdiUseCase;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): logic DÙNG CHUNG cho {@link TraLaiDuThaoChoLanhDaoUseCase}/
 * {@link TraLaiDuThaoChoNguoiSoanThaoUseCase} — đối chiếu trực tiếp
 * {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#TraLaiDuThaoChoLanhDaoKyDuyet()}/
 * {@code TraLaiDuThaoChoNguoiSoanThao()} (dòng 177-400, 2 hàm GẦN NHƯ TRÙNG NHAU, chỉ khác dữ liệu
 * — xem {@link TraLaiDuThaoTarget}).
 *
 * <p><b>Mutation trên DỰ THẢO</b> (dòng 211-231/326-346): {@code return_document_out_for},
 * {@code return_document_out_for_user} (đọc field {@code target.receiverUsernameField} TRÊN CHÍNH
 * dự thảo — KHÔNG phải từ request), {@code status_pre} (backup trạng thái hiện tại),
 * {@code draft_end="0"}, quản lý {@code return_document_out_id} (nối thêm {@code document_out_id}
 * HIỆN CÓ trên dự thảo — KHÔNG phải {@code vbdiId} từ request — vào danh sách, dedupe bằng
 * {@code contains}, KHÔNG dedupe thật sự theo token như {@code VbdiEditHistorySync}, giữ ĐÚNG hành
 * vi Domino dù có thể sai sót với id là substring của id khác), xoá {@code document_out_id}, VÀ cột
 * tĩnh {@code status} → {@code target.targetDraftStatus}.</p>
 *
 * <p><b>Ghi vết {@code draft_process}</b> (dòng 233-242/348-356): qua
 * {@link AddDraftProcessDocumentUseCase}, {@code action_type=target.actionType},
 * {@code receiver}/{@code receiver_username} đọc từ field {@code target.receiverField}/
 * {@code target.receiverUsernameField} TRÊN DỰ THẢO.</p>
 *
 * <p><b>Mutation trên VĂN BẢN ĐI</b> (dòng 244-254/358-368): {@code log_giveback} — Domino
 * {@code replaceItemValue} GHI ĐÈ (KHÔNG append) 1 mảng JSON DUY NHẤT 1 phần tử mỗi lần gọi (đọc kỹ
 * dòng 244-251: {@code ArrayNode arrLog} tạo MỚI mỗi lần, không đọc lại giá trị cũ) —
 * {@code {user_created, created, creator_opinion, time_created}}; {@code creator_opinion}; cột
 * tĩnh {@code status} → {@link VbdiStatus#RETURNED_BY_CLERK} ("3", xem javadoc hằng số đó).</p>
 *
 * <p><b>CHƯA port</b> (cùng nguyên tắc đã áp dụng cho {@link ChuyenThanhVanBanDiUseCase}):
 * {@code TDInit.setViewError}/{@code sendSocketNotSave} (cache view + socket — hạ tầng realtime
 * chưa tồn tại), reindex Elasticsearch (chưa use case nào khác gọi).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TraLaiDuThaoSupport {

    private static final String NOT_FOUND_VBDI_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String NOT_FOUND_DRAFT_MESSAGE = "Không tìm thấy văn bản dự thảo";
    private static final String DRAFT_ID_FIELD = "draft_id";
    private static final String DOCUMENT_OUT_ID_FIELD = "document_out_id";
    private static final String RETURN_DOCUMENT_OUT_FOR_FIELD = "return_document_out_for";
    private static final String RETURN_DOCUMENT_OUT_FOR_USER_FIELD = "return_document_out_for_user";
    private static final String RETURN_DOCUMENT_OUT_ID_FIELD = "return_document_out_id";
    private static final String STATUS_PRE_FIELD = "status_pre";
    private static final String DRAFT_END_FIELD = "draft_end";
    private static final String LOG_GIVEBACK_FIELD = "log_giveback";
    private static final String CREATOR_OPINION_FIELD = "creator_opinion";
    private static final String PROCESS_PARENT_ID_FIELD = "parentid";
    private static final String PROCESS_ACTION_FIELD = "action_type";
    private static final String PROCESS_RECEIVER_FIELD = "receiver";
    private static final String PROCESS_RECEIVER_USERNAME_FIELD = "receiver_username";
    private static final String PROCESS_OPINION_FIELD = "opinion";
    private static final String PROCESS_CREATOR_FIELD = "creator";
    private static final String FALLBACK_USERNAME = "system";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    Result<UUID> execute(TraLaiDuThaoRequest request, TraLaiDuThaoTarget target) {
        if (request == null || request.getVbdiId() == null) {
            return Result.failure("Thiếu id văn bản đi");
        }
        var vbdiOptional = vbdiDocumentRepository.findById(request.getVbdiId());
        if (vbdiOptional.isEmpty() || vbdiOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_VBDI_MESSAGE);
        }
        var vbdi = vbdiOptional.get();
        if (!SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(vbdi.getDocumentType())) {
            return Result.failure(NOT_FOUND_VBDI_MESSAGE);
        }

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        UUID draftId = resolveDraftId(request, vbdiFields);
        if (draftId == null) {
            return Result.failure(NOT_FOUND_DRAFT_MESSAGE);
        }
        var draftOptional = duThaoDocumentRepository.findById(draftId);
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_DRAFT_MESSAGE);
        }
        var draft = draftOptional.get();
        if (!ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure(NOT_FOUND_DRAFT_MESSAGE);
        }

        String opinion = request.getOpinion() == null || request.getOpinion().isBlank()
            ? "Văn thư trả lại lúc " + TIMESTAMP_FORMATTER.format(LocalDateTime.now())
            : request.getOpinion().trim();
        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        UUID savedDraftId = updateDraft(draft, target, actingUsername);
        writeProcessTrail(draft, target, opinion, actingUsername);
        updateVbdi(vbdi, vbdiFields, opinion, actingUsername);

        return Result.success(savedDraftId);
    }

    // Domino dong 204-207/319-322: iddt rong thi tu doc draft_id tren chinh van ban di.
    private UUID resolveDraftId(TraLaiDuThaoRequest request, Map<String, Object> vbdiFields) {
        if (request.getDraftId() != null) {
            return request.getDraftId();
        }
        Object raw = vbdiFields.get(DRAFT_ID_FIELD);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID updateDraft(DuThaoDocument draft, TraLaiDuThaoTarget target, String actingUsername) {
        var before = buildSnapshot(draft);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));

        String receiverUsernameValue = asString(fields.get(target.receiverUsernameField));
        fields.put(RETURN_DOCUMENT_OUT_FOR_FIELD, target.returnDocumentOutForValue);
        fields.put(RETURN_DOCUMENT_OUT_FOR_USER_FIELD, receiverUsernameValue);
        fields.put(STATUS_PRE_FIELD, draft.getStatus());
        fields.put(DRAFT_END_FIELD, "0");

        // Domino dong 219-231/334-346: vbdiid o day la document_out_id HIEN CO tren du thao (KHONG
        // phai vbdiId tu request) - noi vao return_document_out_id, dedupe bang "contains" (giu
        // dung hanh vi that, du khong phai dedupe token chuan).
        String existingDocumentOutId = asString(fields.get(DOCUMENT_OUT_ID_FIELD));
        String returnListRaw = asString(fields.get(RETURN_DOCUMENT_OUT_ID_FIELD));
        String updatedReturnList;
        if (returnListRaw == null || returnListRaw.isBlank()) {
            updatedReturnList = existingDocumentOutId;
        } else if (existingDocumentOutId != null && !returnListRaw.contains(existingDocumentOutId)) {
            updatedReturnList = returnListRaw + ";" + existingDocumentOutId;
        } else {
            updatedReturnList = returnListRaw;
        }
        if (updatedReturnList != null) {
            fields.put(RETURN_DOCUMENT_OUT_ID_FIELD, updatedReturnList);
        }
        fields.remove(DOCUMENT_OUT_ID_FIELD);

        draft.replaceDynamicFields(toJson(fields));
        draft.update(null, null, target.targetDraftStatus, null, null, null, null);
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        return saved.getId();
    }

    // Loi ghi vet KHONG duoc lam hong nghiep vu chinh - dung nguyen tac da ap dung cho
    // ChuyenThanhVanBanDiUseCase#writeProcessTrail.
    private void writeProcessTrail(DuThaoDocument draft, TraLaiDuThaoTarget target, String opinion, String actingUsername) {
        try {
            Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
            String receiverValue = asString(draftFields.get(target.receiverField));
            String receiverUsernameValue = asString(draftFields.get(target.receiverUsernameField));

            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Xử lý dự thảo: " + target.actionType);
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put(PROCESS_PARENT_ID_FIELD, draft.getId().toString());
            processFields.put(PROCESS_ACTION_FIELD, target.actionType);
            putIfNotBlank(processFields, PROCESS_RECEIVER_FIELD, receiverValue);
            putIfNotBlank(processFields, PROCESS_RECEIVER_USERNAME_FIELD, receiverUsernameValue);
            processFields.put(PROCESS_OPINION_FIELD, opinion);
            processFields.put(PROCESS_CREATOR_FIELD, actingUsername);
            processRequest.setFields(processFields);

            addDraftProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ex) {
            log.warn("Ghi vet draft_process '{}' cho du thao {} that bai", target.actionType, draft.getId(), ex);
        }
    }

    private void updateVbdi(VbdiDocument vbdi, Map<String, Object> vbdiFields, String opinion, String actingUsername) {
        var before = buildSnapshot(vbdi);
        Map<String, Object> fields = new LinkedHashMap<>(vbdiFields);

        // Domino dong 244-251/358-365: replaceItemValue GHI DE (KHONG append) 1 mang JSON 1 phan
        // tu moi lan goi - xem javadoc lop.
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("user_created", actingUsername);
        logEntry.put("created", actingUsername);
        logEntry.put(CREATOR_OPINION_FIELD, opinion);
        logEntry.put("time_created", TIMESTAMP_FORMATTER.format(LocalDateTime.now()));
        fields.put(LOG_GIVEBACK_FIELD, toJson(List.of(logEntry)));
        fields.put(CREATOR_OPINION_FIELD, opinion);

        vbdi.replaceDynamicFields(toJson(fields));
        vbdi.update(null, null, VbdiStatus.RETURNED_BY_CLERK, null, null, null, null);
        var saved = vbdiDocumentRepository.save(vbdi);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private void putIfNotBlank(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
