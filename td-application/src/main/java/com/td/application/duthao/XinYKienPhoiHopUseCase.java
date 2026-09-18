package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (2): xin ý kiến phối hợp. Đúng cơ chế thật ({@code ChoYKienPhoiHop
 * .XuLyYKienPhoiHop()}): 2 field {@code user_coordinator} (danh sách chờ ý kiến) và
 * {@code user_coordinated} (danh sách đã cho ý kiến) — người dùng hiện tại nếu đang trong danh sách
 * chờ sẽ được chuyển sang danh sách đã phản hồi.
 *
 * <p><b>Khác biệt cố ý so với Domino thật</b>: {@code ChoYKienPhoiHop.java} dòng 31 có lỗi rõ ràng
 * (append lại chính chuỗi {@code user_coordinated} đã có vào chính nó thay vì thêm username hiện
 * tại) — không replicate lỗi này vì sẽ phá vỡ đúng ý nghĩa field (theo dõi ai đã phản hồi); ở đây
 * thêm đúng username hiện tại vào {@code user_coordinated}, giữ nguyên Ý ĐỊNH nghiệp vụ thật.
 *
 * <p><b>Cập nhật 24/08/2026 (yêu cầu người dùng "tạo bảng riêng, để độc lập dữ liệu")</b>: dự thảo
 * chuyển sang bảng vật lý ĐỘC LẬP {@code duthao_documents} (qua {@link DuThaoDocumentRepository}/
 * {@link AddDuThaoDynamicDocumentUseCase}) thay vì bảng {@code documents} dùng chung.</p>
 *
 * <p><b>Cập nhật 25/08/2026 (người dùng chỉ ra: "khi người phối hợp cho ý kiến phối hợp cũng chính
 * là draft_process")</b>: TRƯỚC ĐÂY bản ghi con của luồng này được tạo thành 1 loại document RIÊNG
 * {@code document_type='duthao_res'} với field {@code nguoi_gop_y}/{@code y_kien}, lưu
 * {@code duthao_documents}. Đối chiếu Domino thật cho thấy SAI: {@code duthao_res} chỉ là TÊN
 * DATABASE trong Domino (DB chứa các bản document có {@code form="draft_process"}) — các luồng phối
 * hợp {@code ChuyenPhoiHopDuThao} (dòng 88-90: {@code form="draft_process"},
 * {@code opinion="Chuyển phối hợp"}) và {@code ChoYKienPhoiHop.XuLyYKienPhoiHop()} (qua base
 * {@code CreateOrUpdateDocument()}) đều ghi vào đó 1 VẾT DRAFT_PROCESS. Vậy bản ghi ý kiến phối hợp
 * PHẢI là {@code draft_process}, lưu vào bảng vật lý riêng {@code draft_process_documents} (migration
 * V1.0.30) qua {@link AddDraftProcessDocumentUseCase} — nhất quán với mọi vết xử lý khác
 * ({@code XuLyDuThaoUseCase}), field theo chuẩn draft_process ({@code parentid}/{@code action}/
 * {@code receiver_username}/{@code opinion}/{@code creator}). Không còn khái niệm {@code duthao_res}
 * như 1 {@code document_type} trong mã Java.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XinYKienPhoiHopUseCase {

    public static final String DOCUMENT_TYPE = "duthao";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy dự thảo";
    private static final String USER_COORDINATOR_FIELD = "user_coordinator";
    private static final String USER_COORDINATED_FIELD = "user_coordinated";
    private static final String PARENT_ID_FIELD = "parentid";
    private static final String RECEIVER_USERNAME_FIELD = "receiver_username";
    // Bugfix 26/08/2026 (port vwGroup_process_by_draft_id): khoa THAT SU Domino la "action_type"
    // (khong phai "action") - xem javadoc PROCESS_ACTION_FIELD tuong ung o XuLyDuThaoUseCase.
    private static final String ACTION_FIELD = "action_type";
    private static final String OPINION_FIELD = "opinion";
    private static final String CREATOR_FIELD = "creator";
    // Luồng phối hợp là 1 vết draft_process (xem javadoc lớp) — action cố định để đối chiếu Domino.
    private static final String PHOI_HOP_ACTION = "chuyenphoihop";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_COORDINATOR_FIELD, USER_COORDINATED_FIELD);

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(XinYKienPhoiHopRequest request) {
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var draft = draftOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse("system");
        var before = buildSnapshot(draft);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));

        List<String> coordinator = toStringList(fields.get(USER_COORDINATOR_FIELD));
        List<String> coordinated = toStringList(fields.get(USER_COORDINATED_FIELD));

        for (String nguoi : request.getNguoiPhoiHop()) {
            if (nguoi != null && !nguoi.isBlank() && !coordinator.contains(nguoi) && !coordinated.contains(nguoi)) {
                coordinator.add(nguoi);
            }
        }

        boolean responded = coordinator.remove(actingUsername);
        if (responded && !coordinated.contains(actingUsername)) {
            coordinated.add(actingUsername);
        }

        fields.put(USER_COORDINATOR_FIELD, coordinator);
        fields.put(USER_COORDINATED_FIELD, coordinated);
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        draft.replaceDynamicFields(toJson(fields));
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        if (responded && request.getOpinion() != null && !request.getOpinion().isBlank()) {
            var childResult = createOpinionRecord(request, actingUsername);
            if (childResult.isSuccess()) {
                return childResult;
            }
        }

        return Result.success(saved.getId());
    }

    // Domino: ChoYKienPhoiHop.XuLyYKienPhoiHop() (qua base CreateOrUpdateDocument) ghi vao database
    // "duthao_res" 1 document form="draft_process" - duthao_res chi la TEN DB, khong phai loai
    // document. O day ghi 1 VET DRAFT_PROCESS vao bang vat ly rieng draft_process_documents (qua
    // AddDraftProcessDocumentUseCase - tu ep document_type='draft_process'), field theo chuan
    // draft_process: parentid/action/receiver_username/opinion/creator.
    private Result<UUID> createOpinionRecord(XinYKienPhoiHopRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle("Ý kiến phối hợp");

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put(PARENT_ID_FIELD, request.getId().toString());
        childFields.put(ACTION_FIELD, PHOI_HOP_ACTION);
        childFields.put(RECEIVER_USERNAME_FIELD, actingUsername);
        childFields.put(OPINION_FIELD, request.getOpinion());
        childFields.put(CREATOR_FIELD, actingUsername);
        childRequest.setFields(childFields);

        return addDraftProcessDocumentUseCase.execute(childRequest);
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
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
        }
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
