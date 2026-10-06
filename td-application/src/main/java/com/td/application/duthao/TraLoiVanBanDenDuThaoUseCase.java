package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.documentlinks.RelatedDocumentSnapshots;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.vbden.AddVbdenResDocumentUseCase;
import com.td.application.vbdi.TraLoiVanBanDenUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng: modal "Trả lời cho văn bản đến" ở chi tiết dự thảo — báo network
 * thấy {@code qlvb/add?id=...&databasetype=duthao}, "lưu ý văn bản đi đã port chức năng liên quan
 * giống như vậy, tránh lỗi ở văn bản"): port {@code DuThaoActionModal.jsx#btnActionTraLoiVanBanDen}
 * (2 lời gọi Domino tuần tự: {@code qlvb/add?id={draftId}} lưu {@code documentin_obj}, rồi
 * {@code global.jsx#capnhatVBden(..., "ganduthaotraloi", ...)} — action này KHÔNG nằm trong nhánh đã
 * cutover {@code taoduthao} của {@code capnhatVBden}, vẫn rơi vào {@code qlvb/vbden/
 * CapNhatVanBanDen?action=ganduthaotraloi} cho MỖI văn bản đến đã chọn).
 *
 * <p>SAO CHÉP Y HỆT thuật toán đã xác nhận + port cho văn bản đi ở {@link TraLoiVanBanDenUseCase}
 * (đọc trực tiếp Domino gốc {@code CapNhatVanBanDen.java#taoduthao_hoso_phieutrinh("ganduthaotraloi")}
 * dòng 309-332 — xác nhận tham số {@code action} KHÔNG được dùng trong thân hàm nên
 * {@code taoduthao}/{@code taophieutrinh}/{@code ganduthaotraloi}/{@code taohoso} chạy Y HỆT NHAU) —
 * CHỈ khác văn bản CHA đang giữ {@code documentin_obj} là dự thảo ({@code duthao_documents}) thay vì
 * văn bản đi ({@code vbdi_documents}). Với MỖI văn bản đến trong {@link TraLoiVanBanDenDuThaoRequest
 * #getDocumentIn()}:</p>
 * <ol>
 *   <li>Đọc {@code user_process} trên CHÍNH văn bản đến — RỖNG thì KHÔNG làm gì (giữ nguyên điều
 *   kiện của bản gốc/bản vbdi, không suy đoán thêm lý do).</li>
 *   <li>KHÔNG rỗng: loại {@code actingUsername} khỏi {@code user_process} VÀ {@code user_returned}
 *   (no-op nếu không có mặt), thêm {@code actingUsername} vào {@code user_processing} (dedup).</li>
 *   <li>LUÔN tạo 1 bản ghi {@code vbden_res} mới ({@code action="ganduthaotraloi"}), KHÔNG điều kiện
 *   theo bước 1-2 — đúng Domino ({@code docQTXL} tạo ở PHẦN ĐẦU hàm, trước switch-case).</li>
 * </ol>
 *
 * <p>{@code opinion}/{@code res_obj} — Domino FE ({@code DuThaoActionModal.jsx} dòng ~566-577) truyền
 * {@code opinion} = {@code resdata.name} (dự thảo KHÔNG có số/ký hiệu chính thức như văn bản đi nên
 * dùng field {@code name} làm mã hiển thị) — server tự tính tương tự từ dự thảo đã tải (field động
 * {@code name}, fallback {@code title}), KHÔNG nhận từ FE (cùng quyết định đã áp dụng ở
 * {@link TraLoiVanBanDenUseCase}). {@code res_obj} = {@code {id, dbpath, code}} của dự thảo —
 * {@code dbpath} luôn rỗng (khái niệm Domino/ACL, không áp dụng Postgres).</p>
 *
 * <p><b>Xử lý TỪNG văn bản đến độc lập</b>: 1 văn bản đến không tìm thấy/sai loại/đã xoá KHÔNG chặn
 * các văn bản khác lẫn phần dự thảo (cùng quyết định {@link TraLoiVanBanDenUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TraLoiVanBanDenDuThaoUseCase {

    private static final String DOCUMENT_TYPE = ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE;
    private static final String VBDEN_DOCUMENT_TYPE = "vbden";
    private static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    private static final String NAME_FIELD = "name";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_RETURNED_FIELD = "user_returned";
    private static final String USER_PROCESSING_FIELD = "user_processing";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final Set<String> AUTHOR_TOUCHED_FIELDS = Set.of(
        USER_PROCESS_FIELD, USER_RETURNED_FIELD, USER_PROCESSING_FIELD);

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<UUID> execute(TraLoiVanBanDenDuThaoRequest request) {
        if (request == null || request.getDraftId() == null) {
            return Result.failure("Thieu du thao can tra loi");
        }
        if (request.getDocumentIn() == null || request.getDocumentIn().isEmpty()) {
            return Result.failure("Chua chon van ban den can tra loi");
        }

        var draftOptional = duThaoDocumentRepository.findById(request.getDraftId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(draftOptional.get().getDocumentType())) {
            return Result.failure("Khong tim thay du thao");
        }
        var draft = draftOptional.get();

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        List<UUID> vbdenIds = updateDocumentInObj(draft, request.getDocumentIn());
        if (vbdenIds.isEmpty()) {
            return Result.failure("Danh sach van ban den khong hop le");
        }

        String draftDisplayCode = resolveDraftDisplayCode(draft);
        for (UUID vbdenId : vbdenIds) {
            try {
                updateVbdenParent(vbdenId, actingUsername);
            } catch (Exception ex) {
                log.warn("Khong cap nhat duoc van ban den {} khi tra loi tu du thao {}",
                    vbdenId, draft.getId(), ex);
            }
            var childResult = createProcessingRecord(vbdenId, actingUsername, request, draft, draftDisplayCode);
            if (!childResult.isSuccess()) {
                log.warn("Khong tao duoc ban ghi qua trinh xu ly cho van ban den {} (tra loi tu du thao {}): {}",
                    vbdenId, draft.getId(), childResult.getError());
            }
        }

        return Result.success(draft.getId());
    }

    // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): moi phan tu =
    // 1 lien ket duthao_traloi_vbden (ban chup o link_data), khong ghi documentin_obj vao dynamic_fields -
    // cung quy uoc TraLoiVanBanDenUseCase#updateDocumentInObj (vbdi).
    private List<UUID> updateDocumentInObj(DuThaoDocument draft, List<Map<String, Object>> documentIn) {
        List<UUID> vbdenIds = new ArrayList<>();
        for (Map<String, Object> item : documentIn) {
            UUID vbdenId = parseUuid(item == null ? null : item.get("id"));
            if (vbdenId == null) {
                continue;
            }
            vbdenIds.add(vbdenId);
            relatedDocumentSnapshots.linkItem(DocumentLinkType.DUTHAO_TRALOI_VBDEN, draft.getId(), item);
        }
        if (!vbdenIds.isEmpty()) {
            documentCacheService.evict(draft.getId());
            documentCacheService.evictAllListCaches();
        }
        return vbdenIds;
    }

    // Domino CapNhatVanBanDen.java#taoduthao_hoso_phieutrinh() dong 309-332 - thuat toan da xac nhan/
    // port o TraLoiVanBanDenUseCase#updateVbdenParent (vbdi)/TaoDuThaoVBdenUseCase (action khong anh
    // huong logic ham nay).
    private void updateVbdenParent(UUID vbdenId, String actingUsername) {
        var parentOptional = documentRepository.findById(vbdenId);
        if (parentOptional.isEmpty() || parentOptional.get().isDeleted()
                || !VBDEN_DOCUMENT_TYPE.equalsIgnoreCase(parentOptional.get().getDocumentType())) {
            log.warn("Van ban den {} khong ton tai/sai loai - bo qua cap nhat user_process", vbdenId);
            return;
        }
        var parent = parentOptional.get();

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        // Domino: if(!user_process.equals("")) { ... } - chi mutate khi field DANG co gia tri.
        if (userProcess.isEmpty()) {
            return;
        }

        var before = buildParentSnapshot(parent);
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
        authorAclFieldsService.applyOnUpdate(VBDEN_DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildParentSnapshot(saved));
    }

    // Domino TaoQTXL() (qua CreateOrUpdateDocument(), chay truoc switch-case, LUON tao du user_process
    // co rong hay khong) - dung ten field Java thuc su dung, cung quy uoc TraLoiVanBanDenUseCase (vbdi).
    private Result<UUID> createProcessingRecord(
            UUID vbdenId, String actingUsername, TraLoiVanBanDenDuThaoRequest request,
            DuThaoDocument draft, String draftDisplayCode) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(TraLoiVanBanDenUseCase.ACTION_GAN_DU_THAO_TRA_LOI);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", vbdenId.toString());
        childFields.put("action", TraLoiVanBanDenUseCase.ACTION_GAN_DU_THAO_TRA_LOI);
        childFields.put("nguoi_thuc_hien", actingUsername);
        String displayCreator = request.getCreatorName() != null && !request.getCreatorName().isBlank()
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        if (!draftDisplayCode.isBlank()) {
            childFields.put("opinion", draftDisplayCode);
        }
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));

        ObjectNode resObj = OBJECT_MAPPER.createObjectNode();
        resObj.put("id", draft.getId().toString());
        resObj.put("dbpath", "");
        resObj.put("code", draftDisplayCode);
        childFields.put("res_obj", resObj.toString());

        childRequest.setFields(childFields);
        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    private String resolveDraftDisplayCode(DuThaoDocument draft) {
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        Object name = fields.get(NAME_FIELD);
        if (name != null && !name.toString().isBlank()) {
            return name.toString();
        }
        return draft.getTitle() == null ? "" : draft.getTitle();
    }

    private Map<String, Object> buildParentSnapshot(BusinessDocument document) {
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

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
