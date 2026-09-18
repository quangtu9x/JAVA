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
 * N20 (07/09/2026 — "Cập nhật kết quả phối hợp xử lý văn bản", 1 trong 3 action còn lại của
 * {@code ChoYKienModal.jsx} chưa port, xem log tiến độ 04/09/2026 của
 * {@code docs/analysis/02-checklist-port-vbden-con-lai.md}): tương đương {@code CapNhatVanBanDen.java}
 * thật, hàm {@code capnhatphoihop()} (dòng 425-451).
 *
 * <p><b>Thuật toán ({@code capnhatphoihop()} thật)</b>: CHỈ đụng {@code user_coordinate}/
 * {@code user_coordinated} — KHÔNG giống {@link KetThucXuLyUseCase}/{@link CapNhatKetQuaXuLyUseCase}
 * (không tính lại {@code status}, không đụng {@code user_process}/{@code user_processing}):</p>
 * <ol>
 *   <li>Đọc {@code user_coordinate} hiện có trên văn bản cha. NẾU RỖNG — không làm gì thêm (Domino
 *   gốc: toàn bộ khối {@code if(!userphoihop.equals(""))} bị bỏ qua, {@code msg.setStatus} không được
 *   gọi lại trong nhánh này nên vẫn coi là thành công theo trạng thái mặc định — port ở đây LUÔN trả
 *   {@code success} vì bản ghi quá trình xử lý bên dưới vẫn được tạo bất kể nhánh này chạy hay không).</li>
 *   <li>NẾU CÒN GIÁ TRỊ: loại {@code actingUsername} khỏi {@code user_coordinate}, thêm vào
 *   {@code user_coordinated} (không trùng lặp — đánh dấu "đã phối hợp xong"), lưu văn bản cha.</li>
 * </ol>
 *
 * <p><b>Tạo bản ghi {@code vbden_res} — LUÔN LUÔN, không điều kiện</b>: khác {@code capnhatphoihop()}
 * (hàm action-specific), việc tạo document con {@code vbden_res} thực ra xảy ra ở
 * {@code CapNhatVanBanDen.CapNhatThongTin()} — đoạn {@code docQTXL = CreateOrUpdateDocument(); ...
 * docQTXL.save();} chạy TRƯỚC switch-case, cho MỌI action (kể cả khi {@code user_coordinate} rỗng) —
 * cùng quy ước đã áp dụng ở {@link KetThucXuLyUseCase#createProcessingRecord}/
 * {@link XuLyLaiUseCase} (đọc field {@code opinion}/{@code creator}/{@code position} người dùng gửi
 * kèm, ghi {@code action="capnhatphoihop"}, {@code nguoi_thuc_hien}=actingUsername,
 * {@code identifier} resolve server-side).</p>
 *
 * <p><b>KHÔNG port</b> (đúng quyết định R11 đã ghi ở {@code LuuVanBanDenUseCase} — không có khái niệm
 * tương đương/không cần thiết): {@code TDInit.setViewError} (badge/counter Redis) và
 * {@code TDInit.sendSocketNotSave} (thông báo WebSocket realtime cho người chủ trì biết có người vừa
 * phối hợp xong — R10, cùng kết luận "chưa port" đã áp dụng cho mọi use case anh em khác trong module
 * này).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatPhoiHopUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_CAP_NHAT_PHOI_HOP = "capnhatphoihop";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String USER_COORDINATE_FIELD = "user_coordinate";
    private static final String USER_COORDINATED_FIELD = "user_coordinated";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(
        USER_COORDINATE_FIELD, USER_COORDINATED_FIELD);

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(CapNhatPhoiHopRequest request) {
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

        updateParentDocumentIfCoordinating(parent, actingUsername);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino capnhatphoihop() dong 426-449: chi chay khi user_coordinate KHONG rong.
    private void updateParentDocumentIfCoordinating(BusinessDocument parent, String actingUsername) {
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> userCoordinate = toStringList(fields.get(USER_COORDINATE_FIELD));
        if (userCoordinate.isEmpty()) {
            return;
        }

        var before = buildSnapshot(parent);

        userCoordinate.remove(actingUsername);
        fields.put(USER_COORDINATE_FIELD, userCoordinate);
        addOne(fields, USER_COORDINATED_FIELD, actingUsername);

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino CapNhatThongTin(): docQTXL = CreateOrUpdateDocument()... docQTXL.save() - chay TRUOC
    // switch-case cho MOI action, dung ten field Java thuc su dung (nguoi_thuc_hien, khong phai
    // user_creator), cung quy uoc KetThucXuLyUseCase/XuLyLaiUseCase.
    private Result<UUID> createProcessingRecord(CapNhatPhoiHopRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(ACTION_CAP_NHAT_PHOI_HOP);
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", ACTION_CAP_NHAT_PHOI_HOP);
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
