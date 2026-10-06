package com.td.application.vbdi;

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
import com.td.domain.documents.BusinessDocument;
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
 * 09/09/2026 (yêu cầu người dùng: modal "Trả lời văn bản đến" tại chi tiết văn bản đi — chưa cutover
 * FE, Java chưa có): port {@code GanVanBanModal.jsx#btnActionTraLoiVanBanDen} (2 lời gọi Domino tuần
 * tự: {@code qlvb/add?id={vbdiId}} lưu {@code documentin_obj}, rồi
 * {@code qlvb/vbden/CapNhatVanBanDen?action=ganduthaotraloi} cho MỖI văn bản đến chọn).
 *
 * <p><b>Phần vbdi</b>: nối thêm (dedup theo {@code id}) các văn bản đến được chọn vào field động
 * {@code documentin_obj} (JSON array) trên chính văn bản đi — snapshot hiển thị client-supplied, xem
 * javadoc {@link TraLoiVanBanDenRequest#getDocumentIn()}.</p>
 *
 * <p><b>Phần vbden — port {@code CapNhatVanBanDen.java#taoduthao_hoso_phieutrinh("ganduthaotraloi")}
 * (dòng 309-332)</b>: xác nhận qua đọc TRỰC TIẾP Domino gốc — tham số {@code action} truyền vào hàm
 * đó KHÔNG hề được dùng trong thân hàm (chỉ xuất hiện ở 1 dòng comment đã tắt), tức {@code taoduthao}/
 * {@code taophieutrinh}/{@code ganduthaotraloi}/{@code taohoso} chạy Y HỆT NHAU — ĐÚNG thuật toán đã
 * port cho {@code taoduthao} ở {@link com.td.application.vbden.TaoDuThaoVBdenUseCase}, chỉ khác
 * {@code action}/{@code res_obj} tham chiếu tới VĂN BẢN ĐI (không phải văn bản dự thảo mới tạo). Với
 * MỖI văn bản đến trong {@code documentIn}:</p>
 * <ol>
 *   <li>Đọc {@code user_process} trên CHÍNH văn bản đến — RỖNG thì KHÔNG làm gì (giữ nguyên điều kiện
 *   lạ của bản gốc, không suy đoán thêm lý do).</li>
 *   <li>KHÔNG rỗng: loại {@code actingUsername} khỏi {@code user_process} VÀ {@code user_returned}
 *   (no-op nếu không có mặt), thêm {@code actingUsername} vào {@code user_processing} (dedup) — đánh
 *   dấu "đang xử lý" (soạn trả lời), khác "đang chờ xử lý".</li>
 *   <li>LUÔN tạo 1 bản ghi {@code vbden_res} mới ({@code action="ganduthaotraloi"}) — Domino tạo
 *   {@code docQTXL} ở PHẦN ĐẦU {@code CapNhatThongTin()} chạy TRƯỚC switch-case, KHÔNG điều kiện theo
 *   {@code user_process} — LUÔN chạy dù bước 1-2 có no-op hay không.</li>
 * </ol>
 *
 * <p><b>{@code opinion}/{@code res_obj} — khác {@code TaoDuThaoVBdenUseCase}</b>: Domino FE
 * ({@code GanVanBanModal.jsx} dòng 291-308, {@code global.jsx#capnhatVBden}) truyền {@code opinion}
 * = TÊN/SỐ HIỆU văn bản đi ({@code resdata.name}) — dùng field "opinion" để ghi CHÚ THÍCH "trả lời
 * bởi văn bản đi nào", KHÔNG phải ý kiến người dùng nhập — server tự tính từ văn bản đi đã tải (field
 * động {@code code_full}, fallback {@code title}), KHÔNG nhận từ FE (chặt hơn Domino 1 chút, tránh
 * thêm field client không cần thiết cho action này). {@code res_obj} = {@code {id, dbpath, code}} của
 * văn bản đi — {@code dbpath} luôn rỗng (khái niệm Domino/ACL, không áp dụng Postgres).</p>
 *
 * <p><b>Xử lý TỪNG văn bản đến độc lập</b>: 1 văn bản đến không tìm thấy/sai loại/đã xoá KHÔNG chặn
 * các văn bản khác lẫn phần vbdi (khác Domino — mỗi lần "Đồng ý" ở Domino chỉ có 1 request/1 văn bản
 * đến do FE lặp tuần tự — ở đây 1 request xử lý cả danh sách, cần cô lập lỗi từng phần tử).</p>
 *
 * <p><b>CHƯA port</b> (ngoài phạm vi yêu cầu — {@code ganvanbanden}/{@code ganvanbandi}, 2 action anh
 * em CÙNG modal, dùng field {@code vanban_lienquan_obj} thay vì {@code documentin_obj}, KHÔNG đụng
 * {@code user_process}/tạo {@code vbden_res} — vẫn gọi Domino nguyên trạng).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TraLoiVanBanDenUseCase {

    public static final String ACTION_GAN_DU_THAO_TRA_LOI = "ganduthaotraloi";
    private static final String VBDEN_DOCUMENT_TYPE = "vbden";
    private static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    private static final String CODE_FULL_FIELD = "code_full";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_RETURNED_FIELD = "user_returned";
    private static final String USER_PROCESSING_FIELD = "user_processing";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_RETURNED_FIELD, USER_PROCESSING_FIELD);

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final RelatedDocumentSnapshots relatedDocumentSnapshots;

    public Result<UUID> execute(TraLoiVanBanDenRequest request) {
        if (request == null || request.getVbdiId() == null) {
            return Result.failure("Thieu van ban di can tra loi");
        }
        if (request.getDocumentIn() == null || request.getDocumentIn().isEmpty()) {
            return Result.failure("Chua chon van ban den can tra loi");
        }

        var vbdiOptional = vbdiDocumentRepository.findById(request.getVbdiId());
        if (vbdiOptional.isEmpty() || vbdiOptional.get().isDeleted()
                || !"vbdi".equalsIgnoreCase(vbdiOptional.get().getDocumentType())) {
            return Result.failure("Khong tim thay van ban di");
        }
        var vbdiDocument = vbdiOptional.get();

        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (isBlank(actingUsername)) {
            return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
        }

        List<UUID> vbdenIds = updateDocumentInObj(vbdiDocument, request.getDocumentIn());
        if (vbdenIds.isEmpty()) {
            return Result.failure("Danh sach van ban den khong hop le");
        }

        String vbdiDisplayCode = resolveVbdiDisplayCode(vbdiDocument);
        for (UUID vbdenId : vbdenIds) {
            try {
                updateVbdenParent(vbdenId, actingUsername);
            } catch (Exception ex) {
                log.warn("Khong cap nhat duoc van ban den {} khi tra loi tu van ban di {}",
                    vbdenId, vbdiDocument.getId(), ex);
            }
            var childResult = createProcessingRecord(vbdenId, actingUsername, request, vbdiDocument, vbdiDisplayCode);
            if (!childResult.isSuccess()) {
                log.warn("Khong tao duoc ban ghi qua trinh xu ly cho van ban den {} (tra loi tu vbdi {}): {}",
                    vbdenId, vbdiDocument.getId(), childResult.getError());
            }
        }

        return Result.success(vbdiDocument.getId());
    }

    // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): moi phan tu
    // "Tra loi cho van ban den" = 1 lien ket vbdi_traloi_vbden (ban chup phan tu o link_data), KHONG ghi
    // documentin_obj vao dynamic_fields nua - API dung lai field tu bang (RelatedDocumentSnapshots).
    // Dedup theo id van ban den (lien ket dang co giu nguyen ban chup cu, nhu truoc). Tra ve MOI id hop le
    // trong request (ke ca da co) - side effect van ban den chay lai nhu Domino.
    private List<UUID> updateDocumentInObj(VbdiDocument vbdiDocument, List<Map<String, Object>> documentIn) {
        List<UUID> vbdenIds = new ArrayList<>();
        for (Map<String, Object> item : documentIn) {
            UUID vbdenId = parseUuid(item == null ? null : item.get("id"));
            if (vbdenId == null) {
                continue;
            }
            vbdenIds.add(vbdenId);
            relatedDocumentSnapshots.linkItem(DocumentLinkType.VBDI_TRALOI_VBDEN, vbdiDocument.getId(), item);
        }
        if (!vbdenIds.isEmpty()) {
            documentCacheService.evict(vbdiDocument.getId());
            documentCacheService.evictAllListCaches();
        }
        return vbdenIds;
    }

    // Domino CapNhatVanBanDen.java#taoduthao_hoso_phieutrinh() dong 309-332 - dung THUAT TOAN da xac
    // nhan/port o TaoDuThaoVBdenUseCase#updateParentDocument (action khong anh huong logic ham nay).
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
        // Domino: if(!user_process.equals("")) { ... } - chi mutate khi field DANG co gia tri (kiem
        // tra TRUOC khi loai actingUsername ra, khong phai "actingUsername co trong do hay khong").
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
    // co rong hay khong) - dung ten field Java thuc su dung, cung quy uoc TaoDuThaoVBdenUseCase/
    // ChuyenXuLyVbdenUseCase.
    private Result<UUID> createProcessingRecord(
            UUID vbdenId, String actingUsername, TraLoiVanBanDenRequest request,
            VbdiDocument vbdiDocument, String vbdiDisplayCode) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_GAN_DU_THAO_TRA_LOI);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", vbdenId.toString());
        childFields.put("action", ACTION_GAN_DU_THAO_TRA_LOI);
        childFields.put("nguoi_thuc_hien", actingUsername);
        String displayCreator = request.getCreatorName() != null && !request.getCreatorName().isBlank()
            ? request.getCreatorName() : actingUsername;
        childFields.put("creator", displayCreator);
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            childFields.put("position", request.getPosition());
        }
        if (!vbdiDisplayCode.isBlank()) {
            childFields.put("opinion", vbdiDisplayCode);
        }
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));

        ObjectNode resObj = OBJECT_MAPPER.createObjectNode();
        resObj.put("id", vbdiDocument.getId().toString());
        resObj.put("dbpath", "");
        resObj.put("code", vbdiDisplayCode);
        childFields.put("res_obj", resObj.toString());

        childRequest.setFields(childFields);
        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    private String resolveVbdiDisplayCode(VbdiDocument vbdiDocument) {
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(vbdiDocument.getDynamicFieldsJson());
        Object codeFull = fields.get(CODE_FULL_FIELD);
        if (codeFull != null && !codeFull.toString().isBlank()) {
            return codeFull.toString();
        }
        return vbdiDocument.getTitle() == null ? "" : vbdiDocument.getTitle();
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
