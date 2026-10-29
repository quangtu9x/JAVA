package com.td.application.duthao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotifier;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 19/10/2026 — port Domino {@code ChuyenPhoiHopDuThao.java} (BE_THANHHOA services/vanbanduthao): người đang
 * được xin ý kiến phối hợp chuyển tiếp cho người khác.
 *
 * <ul>
 *   <li>{@code coordinator}/{@code user_coordinator}: gộp người mới (không trùng); người đang thao tác ra khỏi
 *   danh sách chờ ({@code joinValue(..., removeValue=username)}).</li>
 *   <li>{@code coordinator_obj}: gộp theo {@code id} (không trùng).</li>
 *   <li>{@code user_coordinated}: thêm người đang thao tác (sang tab "Đã phối hợp").</li>
 *   <li>Vết {@code draft_process}: opinion "Chuyển phối hợp", action_type {@code chuyenphoihop}, receiver = tên
 *   người được chuyển.</li>
 * </ul>
 *
 * <p>Khác có chủ ý so với Domino: (1) {@code joinValue} Domino thêm NGUYÊN chuỗi "a;b" như 1 phần tử — ở đây
 * tách đúng từng tài khoản; (2) chỉ người ĐANG trong danh sách chờ ({@code user_coordinator}) mới được chuyển
 * (Domino không kiểm, ai gọi cũng thêm được người phối hợp); (3) ủy quyền: người ủy quyền (đã xác minh) vào
 * {@code user_coordinated_movement} qua {@link DuThaoCoordinatorDelegations} — như Lưu dự thảo/Chuyển xử lý vbden.
 * Quyền đọc người mới: {@link AuthorAclFieldsService} (thay {@code createDocumentReaderAuthor} Domino).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChuyenPhoiHopDuThaoUseCase {

    static final String DOCUMENT_TYPE = "duthao";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy dự thảo";
    private static final String NOT_COORDINATOR_MESSAGE = "Bạn không còn trong danh sách phối hợp của dự thảo này";
    private static final String EMPTY_RECEIVER_MESSAGE = "Chưa chọn người nhận phối hợp";
    private static final String COORDINATOR_FIELD = "coordinator";
    private static final String USER_COORDINATOR_FIELD = "user_coordinator";
    private static final String USER_COORDINATED_FIELD = "user_coordinated";
    private static final String COORDINATOR_OBJ_FIELD = "coordinator_obj";
    private static final String LIST_SEPARATOR = ";";
    private static final String PROCESS_OPINION = "Chuyển phối hợp";
    private static final String PROCESS_ACTION = "chuyenphoihop";
    private static final Set<String> AUTHOR_TOUCHED_FIELDS = Set.of(USER_COORDINATOR_FIELD, USER_COORDINATED_FIELD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final DraftProcessActorFields draftProcessActorFields;
    private final DuThaoCoordinatorDelegations coordinatorDelegations;
    private final RealtimeNotifier realtimeNotifier;

    public Result<UUID> execute(ChuyenPhoiHopDuThaoRequest request) {
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(draftOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        List<String> newUsers = splitMembers(request.getUserCoordinator());
        if (newUsers.isEmpty()) {
            return Result.failure(EMPTY_RECEIVER_MESSAGE);
        }
        DuThaoDocument draft = draftOptional.get();
        String actingUsername = currentUserProvider.currentUsername().orElse("system");
        Map<String, Object> before = buildSnapshot(draft);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));

        Object rawCoordinator = fields.get(USER_COORDINATOR_FIELD);
        Object rawCoordinated = fields.get(USER_COORDINATED_FIELD);
        List<String> waiting = toList(rawCoordinator);
        if (waiting.stream().noneMatch(u -> u.equalsIgnoreCase(actingUsername))) {
            return Result.failure(NOT_COORDINATOR_MESSAGE);
        }
        List<String> coordinated = toList(rawCoordinated);
        boolean waitingAsString = !(rawCoordinator instanceof List<?>);
        boolean coordinatedAsString = rawCoordinated == null ? waitingAsString : !(rawCoordinated instanceof List<?>);

        waiting.removeIf(u -> u.equalsIgnoreCase(actingUsername));
        newUsers.forEach(u -> addUnique(waiting, u));
        addUnique(coordinated, actingUsername);

        List<String> names = toList(fields.get(COORDINATOR_FIELD));
        splitMembers(request.getCoordinator()).forEach(n -> addUnique(names, n));

        fields.put(COORDINATOR_FIELD, String.join("; ", names));
        fields.put(USER_COORDINATOR_FIELD, waitingAsString ? String.join(LIST_SEPARATOR, waiting) : waiting);
        fields.put(USER_COORDINATED_FIELD, coordinatedAsString ? String.join(LIST_SEPARATOR, coordinated) : coordinated);
        List<Map<String, Object>> incomingObj = parseObjArray(request.getCoordinatorObj());
        fields.put(COORDINATOR_OBJ_FIELD, toJson(mergeById(parseObjArray(fields.get(COORDINATOR_OBJ_FIELD)), incomingObj)));
        // Ủy quyền: chỉ xét các phần tử MỚI chuyển lần này.
        Map<String, Object> delegationView = new LinkedHashMap<>(fields);
        delegationView.put(COORDINATOR_OBJ_FIELD, incomingObj);
        coordinatorDelegations.apply(delegationView);
        if (delegationView.get(DuThaoCoordinatorDelegations.USER_COORDINATED_MOVEMENT_FIELD) != null) {
            fields.put(DuThaoCoordinatorDelegations.USER_COORDINATED_MOVEMENT_FIELD,
                delegationView.get(DuThaoCoordinatorDelegations.USER_COORDINATED_MOVEMENT_FIELD));
        }

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        draft.replaceDynamicFields(toJson(fields));
        DuThaoDocument saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        createProcessRecord(saved.getId(), request, newUsers, actingUsername);
        // DraftInitNotification#DraftingCoordinatorInit: báo người phối hợp mới (Domino action "chuyenduthao4"
        // - FE không có nhãn, dùng "chuyenphoihop" = "chuyển phối hợp").
        realtimeNotifier.notify(RealtimeNotification.of(ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE, "chuyenphoihop",
                saved.getId(), saved.getTitle())
            .to(newUsers)
            .exclude(actingUsername));
        return Result.success(saved.getId());
    }

    // Domino: doc_res form=draft_process, opinion "Chuyển phối hợp", receiver=_coordinator, action_type chuyenphoihop.
    // Lỗi ghi vết không làm hỏng giao dịch chính (cùng nguyên tắc XuLyDuThaoUseCase#createDraftProcessRecord).
    private void createProcessRecord(UUID draftId, ChuyenPhoiHopDuThaoRequest request, List<String> newUsers, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle(PROCESS_OPINION);
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("parentid", draftId.toString());
            processFields.put("action_type", PROCESS_ACTION);
            processFields.put("opinion", PROCESS_OPINION);
            processFields.put("creator_status", "");
            if (request.getCoordinator() != null && !request.getCoordinator().isBlank()) {
                processFields.put("receiver", request.getCoordinator().trim());
            }
            processFields.put("receiver_username", String.join(LIST_SEPARATOR, newUsers));
            draftProcessActorFields.put(processFields, actingUsername);
            processRequest.setFields(processFields);
            addDraftProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // vết là bản ghi lịch sử, không phải điều kiện để chuyển phối hợp thành công
        }
    }

    static List<Map<String, Object>> mergeById(List<Map<String, Object>> existing, List<Map<String, Object>> incoming) {
        List<Map<String, Object>> merged = new ArrayList<>(existing);
        for (Map<String, Object> item : incoming) {
            Object id = item.get("id");
            boolean duplicate = id != null && merged.stream().anyMatch(e -> id.toString().equals(String.valueOf(e.get("id"))));
            if (!duplicate) {
                merged.add(item);
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseObjArray(Object value) {
        try {
            if (value instanceof List<?> list) {
                return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
            }
            String json = value == null ? "" : value.toString().trim();
            if (!json.startsWith("[")) {
                return List.of();
            }
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> toList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> addUnique(result, item == null ? null : item.toString()));
        } else if (value != null) {
            splitMembers(value.toString()).forEach(item -> addUnique(result, item));
        }
        return result;
    }

    private static List<String> splitMembers(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split(LIST_SEPARATOR)) {
            addUnique(result, part);
        }
        return result;
    }

    private static void addUnique(List<String> target, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && target.stream().noneMatch(t -> t.equalsIgnoreCase(trimmed))) {
            target.add(trimmed);
        }
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
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
            return value instanceof Map ? "{}" : "[]";
        }
    }
}
