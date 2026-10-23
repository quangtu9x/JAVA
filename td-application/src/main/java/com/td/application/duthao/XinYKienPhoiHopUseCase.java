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
 *
 * <p><b>Cập nhật 26/09/2026 (yêu cầu người dùng "Cho ý kiến -> hoàn thiện gắn kèm, cutoverFE")</b>:
 * <ul>
 *   <li>Đọc được CẢ 2 dạng dữ liệu của {@code user_coordinator}/{@code user_coordinated}: JSON array
 *       (ghi bởi use case này) lẫn chuỗi scalar {@code "a;b"} (ghi bởi FE qua {@code Luu} —
 *       {@code ChiTietPage.jsx} đặt {@code ids.join(";")}; DB thật tenant skhcn đang toàn dạng
 *       chuỗi). Trước đây chỉ đọc List nên dạng chuỗi bị coi là rỗng: không bao giờ "phản hồi" được,
 *       không tạo vết, và còn GHI ĐÈ danh sách chờ thành {@code []}. Ghi lại giữ đúng dạng đã đọc.</li>
 *   <li>{@code action_type} của vết = {@code "ykienphoihop"} (đúng giá trị FE Domino
 *       {@code ChoYKienModal.jsx} gửi) — trước ghi nhầm {@code "chuyenphoihop"} (là hành động CHUYỂN
 *       phối hợp của {@code XuLyDuThaoUseCase}, khác nghĩa).</li>
 *   <li>Người dùng hiện tại phản hồi thì LUÔN tạo vết (Domino {@code CreateOrUpdateDocument()} luôn
 *       tạo), ý kiến trống thì mặc định {@code "Đã phối hợp"} (cùng mặc định FE Domino) — trả về
 *       {@code id} của VẾT để FE gắn tệp đính kèm (upload vào dự thảo cha kèm {@code processId} = id
 *       vết, cùng pattern {@link GanKemTepDuThaoUseCase}). Tạo vết lỗi thì trả lỗi (trước im lặng trả
 *       id dự thảo, FE sẽ gắn tệp nhầm vào processId = id dự thảo).</li>
 * </ul></p>
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
    private static final String PHOI_HOP_ACTION = "ykienphoihop";
    private static final String DEFAULT_OPINION = "Đã phối hợp";
    private static final String LIST_SEPARATOR = ";";
    // 1 trình ký, 3 đã ký chờ chuyển văn thư, 4 chuyển văn thư, 6 trình ký nháy, 7 kết thúc, 8 đã ban hành.
    private static final java.util.Set<String> CHO_Y_KIEN_CLOSED_STATUSES = java.util.Set.of(
        DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.SIGNED_WAITING_SUBMIT, DuThaoStatus.PUBLISHED,
        DuThaoStatus.PRE_SIGN_SUBMIT, DuThaoStatus.ENDED, DuThaoStatus.DOCUMENT_OUT_FINALIZED);
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
    // 25/09/2026: creator = tên đầy đủ, user_creator = tài khoản (xem javadoc DraftProcessActorFields).
    private final DraftProcessActorFields draftProcessActorFields;

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
        // 20/10/2026 (yêu cầu người dùng): dự thảo đã chuyển ký / đã ký / chuyển văn thư / kết thúc thì người
        // phối hợp không cho ý kiến nữa — cùng danh sách FE duThaoRoles.CHOYKIEN_HIDDEN_STATUSES (FE ẩn nút).
        // Chỉ chặn luồng CHO Ý KIẾN (không kèm thêm người phối hợp).
        boolean opinionOnly = request.getNguoiPhoiHop() == null || request.getNguoiPhoiHop().isEmpty();
        if (opinionOnly && CHO_Y_KIEN_CLOSED_STATUSES.contains(draft.getStatus())) {
            return Result.failure("Dự thảo đã chuyển ký/chuyển văn thư hoặc đã kết thúc, không thể cho ý kiến phối hợp.");
        }
        var before = buildSnapshot(draft);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));

        Object rawCoordinator = fields.get(USER_COORDINATOR_FIELD);
        Object rawCoordinated = fields.get(USER_COORDINATED_FIELD);
        List<String> coordinator = toStringList(rawCoordinator);
        List<String> coordinated = toStringList(rawCoordinated);
        // Field chưa có thì theo dạng của field còn lại (dữ liệu FE ghi luôn là chuỗi scalar).
        boolean coordinatorAsString = rawCoordinator instanceof String
            || (rawCoordinator == null && rawCoordinated instanceof String);
        boolean coordinatedAsString = rawCoordinated instanceof String
            || (rawCoordinated == null && rawCoordinator instanceof String);

        for (String nguoi : request.getNguoiPhoiHop()) {
            if (nguoi != null && !nguoi.isBlank() && !coordinator.contains(nguoi) && !coordinated.contains(nguoi)) {
                coordinator.add(nguoi);
            }
        }

        boolean responded = coordinator.remove(actingUsername);
        if (responded && !coordinated.contains(actingUsername)) {
            coordinated.add(actingUsername);
        }
        // 20/10/2026 (báo lỗi "Bạn không còn trong danh sách cho ý kiến phối hợp" khi cho ý kiến LẦN 2): người
        // đã cho ý kiến (đã ở user_coordinated) vẫn được cho thêm ý kiến — ghi thêm 1 vết, danh sách giữ nguyên.
        boolean addingOpinionAgain = !responded && coordinated.contains(actingUsername) && opinionOnly;

        fields.put(USER_COORDINATOR_FIELD, coordinatorAsString ? String.join(LIST_SEPARATOR, coordinator) : coordinator);
        fields.put(USER_COORDINATED_FIELD, coordinatedAsString ? String.join(LIST_SEPARATOR, coordinated) : coordinated);
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        draft.replaceDynamicFields(toJson(fields));
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        if (responded || addingOpinionAgain) {
            return createOpinionRecord(request, actingUsername);
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
        String opinion = request.getOpinion();
        childFields.put(OPINION_FIELD, opinion == null || opinion.isBlank() ? DEFAULT_OPINION : opinion.trim());
        draftProcessActorFields.put(childFields, actingUsername);
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
                addIfPresent(result, item == null ? null : item.toString());
            }
        } else if (value instanceof String text) {
            for (String item : text.split(LIST_SEPARATOR)) {
                addIfPresent(result, item);
            }
        }
        return result;
    }

    private void addIfPresent(List<String> target, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && !target.contains(trimmed)) {
            target.add(trimmed);
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
