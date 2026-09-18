package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
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
 * Yêu cầu người dùng 15/09/2026 ("kiểm tra lại nghiệp vụ văn bản đến xem còn nghiệp vụ nào chưa
 * port thì port sang java và cutoverfe"): nút "Đánh dấu đã xem" ở tab "Xem để biết"
 * ({@code xemdebiet/components/ItemsActiveList.jsx} hàm {@code danhdau_daxem}) — tương đương
 * {@code CapNhatVanBanDen.java} thật, hàm {@code daxemdebiet()} (dòng 371-390).
 *
 * <p><b>Thuật toán ({@code daxemdebiet()} thật)</b>: CHỈ đụng {@code user_to_read}/
 * {@code user_to_readed} (cùng khuôn {@link CapNhatPhoiHopUseCase} — 1 field "đang chờ", 1 field
 * "đã xong"):</p>
 * <ol>
 *   <li>Đọc {@code user_to_read} hiện có trên văn bản cha. NẾU RỖNG — không làm gì thêm (Domino gốc:
 *   toàn bộ khối {@code if(!userxemdebiet.equals(""))} bị bỏ qua — port LUÔN trả {@code success} vì
 *   bản ghi quá trình xử lý bên dưới vẫn được tạo bất kể nhánh này chạy hay không, đúng
 *   {@link CapNhatPhoiHopUseCase}).</li>
 *   <li>NẾU CÒN GIÁ TRỊ: loại {@code actingUsername} khỏi {@code user_to_read}, thêm vào
 *   {@code user_to_readed} (không trùng lặp), lưu văn bản cha — field {@code user_to_readed} ĐÃ CÓ Ý
 *   NGHĨA THẬT, view {@code daxemdebiet} ({@link ViewQueryService#VIEW_VBDEN_DAXEMDEBIET}) khoá đúng
 *   field này.</li>
 * </ol>
 *
 * <p><b>Tạo bản ghi {@code vbden_res} — LUÔN LUÔN, không điều kiện</b>: giống mọi action khác của
 * {@code CapNhatVanBanDen.java} — {@code CapNhatThongTin()} chạy {@code CreateOrUpdateDocument()}
 * TRƯỚC switch-case cho MỌI action. FE gọi với body RỖNG ({@code requestPOST_FORM(url, {})}) nên bản
 * ghi này chỉ có {@code parentid}/{@code action}/{@code nguoi_thuc_hien} (không có
 * {@code opinion}/{@code creator}/{@code position}) — cùng khuôn {@link TheoDoiNhiemVuUseCase}.</p>
 *
 * <p><b>KHÔNG port</b> (đúng quyết định R10/R11 đã áp dụng cho mọi use case anh em khác trong module
 * này): comment sẵn trong Domino gốc (khối {@code TaoThongBao}/{@code sendSocketNotSave} bị comment
 * out hoàn toàn ở chính hàm {@code daxemdebiet()} thật, không phải quyết định của lần port này).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatDaXemDeBietUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_CAP_NHAT_DA_XEM_DE_BIET = "capnhatdaxemdebiet";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_TO_READ_FIELD = "user_to_read";
    private static final String USER_TO_READED_FIELD = "user_to_readed";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_TO_READ_FIELD, USER_TO_READED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(CapNhatDaXemDeBietRequest request) {
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

        updateParentDocumentIfPending(parent, actingUsername);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino daxemdebiet() dong 371-390: chi chay khi user_to_read KHONG rong.
    private void updateParentDocumentIfPending(BusinessDocument parent, String actingUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userToRead = toStringList(fields.get(USER_TO_READ_FIELD));
        if (userToRead.isEmpty()) {
            return;
        }

        var before = buildSnapshot(parent);

        userToRead.remove(actingUsername);
        fields.put(USER_TO_READ_FIELD, userToRead);
        addOne(fields, USER_TO_READED_FIELD, actingUsername);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino CapNhatThongTin(): docQTXL = CreateOrUpdateDocument()... docQTXL.save() - chay TRUOC
    // switch-case cho MOI action. FE gui body RONG ({}) nen khong co opinion/creator/position de gan,
    // cung khuon TheoDoiNhiemVuUseCase.
    private Result<UUID> createProcessingRecord(CapNhatDaXemDeBietRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_CAP_NHAT_DA_XEM_DE_BIET);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_CAP_NHAT_DA_XEM_DE_BIET);
        childFields.put("nguoi_thuc_hien", actingUsername);
        childRequest.setFields(childFields);

        return addVbdenResDocumentUseCase.execute(childRequest);
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

    private void addOne(Map<String, Object> fields, String fieldName, String value) {
        List<String> list = toStringList(fields.get(fieldName));
        if (!list.contains(value)) {
            list.add(value);
        }
        fields.put(fieldName, list);
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
