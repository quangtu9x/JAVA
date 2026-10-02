package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
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
 * Modal "Trả lại văn bản" ({@code ModalTraLaiVanBan.jsx}, dropdown đổ từ {@link GetNguoiTraLaiUseCase})
 * — yêu cầu người dùng 14/09/2026 ("modal Trả lại văn bản -> chọn và đồng ý -> đang gọi api cũ
 * qlvb/vbden/TraLaiVanBan?databasetype=vbden -> port và cutoverFE"). Tương đương
 * {@code TraLaiVanBan.java} thật (KHÔNG phải {@code TraLaiVanBan2.java} — biến thể song song KHÔNG
 * được bất kỳ path/FE nào gọi tới, cùng kiểu "bản dự phòng chết" như {@code GetNguoiTraLai_1.java} đã
 * xác nhận ở lượt port trước — xác nhận qua {@code ServletFactory#getServletMatch}, path
 * {@code TraLaiVanBan} không hậu tố CHỈ khớp class gốc theo naming-convention routing).
 *
 * <p><b>Thuật toán ({@code TraLaiVanBan.TraLaiVanBan()} thật)</b>:</p>
 * <ol>
 *   <li>Loại NGƯỜI ĐANG THAO TÁC ({@code actingUsername}) khỏi {@code user_process} trên văn bản gốc
 *   — KHÔNG điều kiện (khác {@link TaoDuThaoVBdenUseCase}, không có guard "chỉ khi user_process đang
 *   có giá trị").</li>
 *   <li>Thêm NGƯỜI ĐƯỢC CHỌN TRẢ LẠI (field form {@code user_process} — LƯU Ý tên field form TRÙNG
 *   tên field field động khác trên văn bản, xem javadoc {@link TraLaiVanBanRequest}) vào
 *   {@code user_returned} (dedupe, không trùng — khớp
 *   {@code TDDocumentUlties#addToFieldListStringHideVBden}, field này KHÔNG nằm trong
 *   {@code relatedFields} map của hàm gốc nên không có loại trừ chéo nào áp dụng). {@code user_returned}
 *   là field ĐÃ CÓ Ý NGHĨA THẬT ở Java — view {@code vanbantralai} ({@link ViewQueryService}) khoá
 *   đúng field này, các use case khác ({@link TaoDuThaoVBdenUseCase}/{@link KetThucXuLyUseCase}/
 *   {@code TraLoiVanBanDenUseCase}) đều loại {@code actingUsername} khỏi field này khi người đó xử lý
 *   tiếp — vòng đời field nhất quán, không phải field mới bịa ra.</li>
 *   <li>Tạo 1 bản ghi {@code vbden_res} MỚI ({@code action="tralai"}, {@code opinion}=nội dung trả
 *   lại, {@code user_process_obj}=mảng 1 phần tử {@code {id:"", name:nameProcess, username:userProcess,
 *   position:""}} — snapshot NGƯỜI ĐƯỢC TRẢ LẠI, đúng {@code TaoQTXL()} thật).</li>
 *   <li>Xoá node sơ đồ xử lý ({@link ProcessDiagramService#removeNode}) của {@code actingUsername} —
 *   đúng {@code ClassTreeSoDo.removeNodeByNodeUserName(root, username, ...)} thật (Domino đọc/ghi
 *   riêng 1 "document sơ đồ" theo UNID văn bản cha trong database {@code vbden_res}; Java đã có hạ
 *   tầng tương đương từ R8 — bảng {@code process_diagrams}, xem {@code ThuHoiVanBanUseCase} dùng
 *   CÙNG cơ chế này).</li>
 * </ol>
 *
 * <p><b>KHÔNG port</b> (Domino gốc có, comment sẵn/không có bằng chứng FE cần — cùng kết luận R11 đã
 * ghi ở nhiều use case khác): thông báo WebSocket ({@code sendSocketNotSave}).</p>
 *
 * <p><b>Khác biệt có chủ đích</b>: KHÔNG tính lại {@code status} (Domino gốc cũng KHÔNG đụng field
 * này ở hàm {@code TraLaiVanBan()} — khác hẳn {@code ThuHoiVanBanUseCase}/{@code CapNhatKetQuaXuLyUseCase}
 * đều recompute status).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TraLaiVanBanUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_TRA_LAI = "tralai";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_PROCESS_FIELD = "user_process";
    private static final String USER_RETURNED_FIELD = "user_returned";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_PROCESS_FIELD, USER_RETURNED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final ProcessDiagramService processDiagramService;
    private final AuthorAclFieldsService authorAclFieldsService;
    private final com.td.application.sharedcore.UserFullNameResolver userFullNameResolver;

    public Result<UUID> execute(TraLaiVanBanRequest request) {
        if (request == null || request.getParentid() == null || isBlank(request.getUserProcess())) {
            return Result.failure("Thieu thong tin tra lai van ban");
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

        updateParentDocument(parent, actingUsername, request.getUserProcess());
        removeDiagramNode(parent.getId(), actingUsername);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino TraLaiVanBan(): removeToFieldListStringHideVBden(user_process, actingUsername) +
    // addToFieldListStringHideVBden(user_returned, form.user_process) - KHONG dieu kien.
    private void updateParentDocument(BusinessDocument parent, String actingUsername, String returnToUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));
        var before = buildSnapshot(parent);

        List<String> userProcess = toStringList(fields.get(USER_PROCESS_FIELD));
        userProcess.remove(actingUsername);
        fields.put(USER_PROCESS_FIELD, userProcess);

        List<String> userReturned = toStringList(fields.get(USER_RETURNED_FIELD));
        if (!userReturned.contains(returnToUsername)) {
            userReturned.add(returnToUsername);
        }
        fields.put(USER_RETURNED_FIELD, userReturned);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private void removeDiagramNode(UUID parentId, String actingUsername) {
        try {
            processDiagramService.removeNode(parentId, DOCUMENT_TYPE, actingUsername);
        } catch (Exception ex) {
            log.warn("Khong the xoa node so do xu ly cho van ban {} (username {}): {}",
                parentId, actingUsername, ex.getMessage());
        }
    }

    // Domino TaoQTXL(): ban ghi moi action=tralai, user_process_obj = snapshot 1 phan tu NGUOI DUOC
    // TRA LAI (khac cac use case khac - snapshot o day la NGUOI NHAN, khong phai nguoi bi thu hoi/
    // hoan tat).
    private Result<UUID> createProcessingRecord(TraLaiVanBanRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_TRA_LAI);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_TRA_LAI);
        // "nguoi_thuc_hien" = TÀI KHOẢN (lưu ẩn, nguồn tra cứu). 30/09/2026 (yêu cầu người dùng: cột "Người
        // thực hiện" ở Quá trình xử lý hiện tài khoản khi Trả lại): "creator" = TÊN ĐẦY ĐỦ tra từ hồ sơ,
        // đồng nhất các hành động khác (ChuyenXuLyVbdenUseCase...); không tra được thì giữ tài khoản.
        childFields.put("nguoi_thuc_hien", actingUsername);
        childFields.put("creator", userFullNameResolver.resolveForWrite(actingUsername, null));
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            childFields.put("opinion", request.getOpinion());
        }
        childFields.put("user_process_obj", toJsonArray(buildUserProcessObjSnapshot(request)));
        resolveIdentifierByLevelUseCase.resolveForCurrentUser()
            .filter(value -> !value.isBlank())
            .ifPresent(value -> childFields.put("identifier", value));
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
    }

    private ArrayNode buildUserProcessObjSnapshot(TraLaiVanBanRequest request) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        ObjectNode entry = OBJECT_MAPPER.createObjectNode();
        entry.put("id", "");
        entry.put("name", request.getNameProcess() != null ? request.getNameProcess() : "");
        entry.put("username", request.getUserProcess());
        entry.put("position", "");
        array.add(entry);
        return array;
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

    private String toJsonArray(ArrayNode value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
