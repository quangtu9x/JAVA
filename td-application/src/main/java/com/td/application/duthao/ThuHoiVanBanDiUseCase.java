package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.DeleteVbdiDocumentUseCase;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DoD (4): thu hồi văn bản đi chờ ban hành. Đúng cơ chế thật
 * ({@code DraftToDocumentOut.ReturnDocumentOutToDraft()}): xoá (mềm) văn bản đi liên kết, ghi lịch
 * sử {@code return_document_out_id} trên dự thảo, xoá {@code document_out_id}, chuyển dự thảo về
 * trạng thái {@link DuThaoStatus#DRAFTING} (Domino: {@code draft_end="0"}, mở lại dự thảo).
 *
 * <p><b>Khác biệt cố ý so với Domino thật</b>: DoD (4) ghi rõ "văn bản đi CHỜ BAN HÀNH" — nhưng
 * {@code ReturnDocumentOutToDraft()} thật KHÔNG kiểm tra trạng thái văn bản đi trước khi thu hồi
 * (có thể thu hồi cả văn bản đã ban hành, một lỗ hổng nghiệp vụ thật). Ở đây CHỦ ĐỘNG thêm guard
 * (chỉ cho phép thu hồi khi văn bản đi CHƯA {@link VbdiStatus#ISSUED}) — đúng nguyên văn tên gọi
 * DoD (4), không phải suy diễn thêm nghiệp vụ mới mà là siết chặt đúng theo chính tên DoD.
 *
 * <p><b>Bổ sung 27/08/2026 (yêu cầu người dùng "làm đầy đủ", cùng đợt với
 * {@link ChuyenThanhVanBanDiUseCase})</b>: ghi vết draft_process ({@code action_type=
 * thuhoichuyenvanthu}, KHÔNG ép {@code receiver} — khớp {@code DuThaoActionModal.jsx} chỉ ép
 * receiver="Văn thư" cho nhánh {@code chuyenvanthu}). KHÔNG cần xử lý tệp đính kèm ở đây — dự thảo
 * chưa từng bị di chuyển/xoá tệp lúc chuyển văn thư (chỉ COPY sang vbdi), nên tệp dự thảo vẫn còn
 * nguyên vẹn sau khi thu hồi — đúng hành vi Domino thật ({@code ReturnDocumentOutToDraft()} không
 * có logic tệp).</p>
 *
 * <p><b>Bugfix 27/08/2026 (tiếp, người dùng chỉ ra "đang thiếu thay đổi form")</b>: Domino dòng
 * 572-573 — SONG SONG với xoá (mềm) văn bản đi — còn đổi {@code form} thành
 * {@code <form_cũ>+"_Deleted"} trên CHÍNH document văn bản đi (KHÔNG phải trên dự thảo cha). Bản
 * port trước chỉ gọi {@link DeleteVbdiDocumentUseCase} (đặt {@code deleted_on}/{@code deleted_by} —
 * đã đủ để {@code isDeleted()} lọc đúng ở mọi truy vấn Java, tương đương Ý ĐỊNH của Domino) nhưng bỏ
 * sót việc đổi {@code form} như 1 field ĐỘNG — nếu có nơi khác (vd view/report) lọc theo giá trị
 * {@code form} thay vì cột {@code deleted_on}, văn bản đã thu hồi vẫn hiện `form="DocumentOut"` như
 * chưa từng bị xoá. Đã bổ sung {@link #markVbdiFormAsDeleted}, gọi SAU khi
 * {@link DeleteVbdiDocumentUseCase} thành công — không chặn nghiệp vụ chính nếu lỗi. KHÔNG port
 * phần {@code docvbdi_file.replaceItemValue("form", ...)} — Java không có document {@code _file}
 * riêng (tệp theo dõi qua {@code file_metadata}, không có field {@code form}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ThuHoiVanBanDiUseCase {

    public static final String DOCUMENT_TYPE = "duthao";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy dự thảo";
    private static final String NOT_LINKED_MESSAGE = "Dự thảo chưa được chuyển thành văn bản đi";
    private static final String VBDI_NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi liên kết";
    private static final String ALREADY_ISSUED_MESSAGE = "Văn bản đã ban hành, không thể thu hồi qua dự thảo";
    private static final String DOCUMENT_OUT_ID_FIELD = "document_out_id";
    private static final String RETURN_DOCUMENT_OUT_ID_FIELD = "return_document_out_id";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Cung field convention voi XuLyDuThaoUseCase#createDraftProcessRecord / ChuyenThanhVanBanDiUseCase.
    private static final String PROCESS_PARENT_ID_FIELD = "parentid";
    private static final String PROCESS_ACTION_FIELD = "action_type";
    private static final String PROCESS_STATUS_FIELD = "status";
    private static final String PROCESS_OLD_STATUS_FIELD = "oldstatus";
    private static final String PROCESS_OPINION_FIELD = "opinion";
    private static final String PROCESS_CREATOR_FIELD = "creator";
    private static final String ACTION_THU_HOI_CHUYEN_VAN_THU = "thuhoichuyenvanthu";
    private static final String FALLBACK_USERNAME = "system";
    // Domino dong 572: docvbdi.replaceItemValue("form", form_cu + "_Deleted") - xem javadoc lop,
    // muc bugfix "form".
    private static final String FORM_FIELD = "form";
    private static final String DELETED_SUFFIX = "_Deleted";

    // vbdi (van ban di lien ket) VAN nam o bang "documents" dung chung (khong chuyen sang
    // duthao_documents - chi cum du thao moi tach rieng, xem javadoc DuThaoDocument) - can CA 2
    // repository trong use case nay.
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DeleteVbdiDocumentUseCase deleteVbdiDocumentUseCase;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;
    // 25/09/2026: creator = tên đầy đủ, user_creator = tài khoản (xem javadoc DraftProcessActorFields).
    private final DraftProcessActorFields draftProcessActorFields;

    public Result<UUID> execute(ThuHoiVanBanDiRequest request) {
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var draft = draftOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        String vbdiIdStr = asString(draftFields.get(DOCUMENT_OUT_ID_FIELD));
        if (vbdiIdStr == null || vbdiIdStr.isBlank()) {
            return Result.failure(NOT_LINKED_MESSAGE);
        }

        UUID vbdiId;
        try {
            vbdiId = UUID.fromString(vbdiIdStr);
        } catch (IllegalArgumentException ex) {
            return Result.failure(VBDI_NOT_FOUND_MESSAGE);
        }

        var vbdiOptional = vbdiDocumentRepository.findById(vbdiId);
        if (vbdiOptional.isEmpty() || vbdiOptional.get().isDeleted()) {
            return Result.failure(VBDI_NOT_FOUND_MESSAGE);
        }
        if (VbdiStatus.ISSUED.equals(vbdiOptional.get().getStatus())) {
            return Result.failure(ALREADY_ISSUED_MESSAGE);
        }

        var deleteResult = deleteVbdiDocumentUseCase.execute(vbdiId);
        if (!deleteResult.isSuccess()) {
            return Result.failure(deleteResult.getError());
        }
        markVbdiFormAsDeleted(vbdiOptional.get());

        String previousStatus = draft.getStatus();
        reopenDraft(draft, draftFields, vbdiIdStr);
        writeProcessTrail(draft.getId(), previousStatus, request.getOpinion());

        return Result.success(draft.getId());
    }

    // Domino dong 572-573 - xem javadoc lop, muc bugfix "form". Loi KHONG chan nghiep vu chinh -
    // deleted_on/deleted_by cua DeleteVbdiDocumentUseCase da la nguon su that chinh cho isDeleted(),
    // day chi la field dong PHU de khop dung du lieu Domino thuc.
    private void markVbdiFormAsDeleted(VbdiDocument vbdi) {
        try {
            Map<String, Object> fields = new LinkedHashMap<>(
                dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson()));
            String currentForm = asString(fields.get(FORM_FIELD));
            fields.put(FORM_FIELD, (currentForm == null ? "" : currentForm) + DELETED_SUFFIX);
            vbdi.replaceDynamicFields(toJson(fields));
            vbdiDocumentRepository.save(vbdi);
        } catch (Exception ex) {
            log.error("Doi form thanh '_Deleted' cho van ban di {} that bai", vbdi.getId(), ex);
        }
    }

    // Xem javadoc lop, muc bo sung 27/08/2026 - loi KHONG chan nghiep vu chinh (dung nguyen tac
    // cua XuLyDuThaoUseCase#createDraftProcessRecord: vet la GHI LAI, khong phai dieu kien tien
    // quyet).
    private void writeProcessTrail(UUID draftId, String previousStatus, String opinion) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Xử lý dự thảo: " + ACTION_THU_HOI_CHUYEN_VAN_THU);
            processRequest.setStatus(DuThaoStatus.DRAFTING);

            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put(PROCESS_PARENT_ID_FIELD, draftId.toString());
            processFields.put(PROCESS_ACTION_FIELD, ACTION_THU_HOI_CHUYEN_VAN_THU);
            processFields.put(PROCESS_STATUS_FIELD, DuThaoStatus.DRAFTING);
            if (previousStatus != null && !previousStatus.isBlank()) {
                processFields.put(PROCESS_OLD_STATUS_FIELD, previousStatus);
            }
            if (opinion != null && !opinion.isBlank()) {
                processFields.put(PROCESS_OPINION_FIELD, opinion);
            }
            draftProcessActorFields.put(processFields, currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME));
            processRequest.setFields(processFields);

            addDraftProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ex) {
            log.error("Ghi vet draft_process 'thuhoichuyenvanthu' cho du thao {} that bai", draftId, ex);
        }
    }

    private void reopenDraft(DuThaoDocument draft, Map<String, Object> draftFields, String vbdiIdStr) {
        var before = buildSnapshot(draft);
        Map<String, Object> updatedFields = new LinkedHashMap<>(draftFields);

        List<String> returnHistory = toStringList(updatedFields.get(RETURN_DOCUMENT_OUT_ID_FIELD));
        if (!returnHistory.contains(vbdiIdStr)) {
            returnHistory.add(vbdiIdStr);
        }
        updatedFields.put(RETURN_DOCUMENT_OUT_ID_FIELD, String.join(";", returnHistory));
        updatedFields.remove(DOCUMENT_OUT_ID_FIELD);

        draft.replaceDynamicFields(toJson(updatedFields));
        draft.update(null, null, DuThaoStatus.DRAFTING, null, null, null, null);
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
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
        } else if (value instanceof String str && !str.isBlank()) {
            for (String part : str.split(";")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
