package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * N20 (07/09/2026 — "Văn bản cần theo dõi", nút "Theo dõi"/"Bỏ theo dõi"/"Chuyển TDNV" ở
 * {@code toanbovanban/components/ItemsListNhiemVuCanTheoDoi.jsx} hàm {@code action_theodoi} — xem
 * mục "Xem theo nhóm"/"vanbancantheodoi" của
 * {@code docs/analysis/02-checklist-port-vbden-con-lai.md}): tương đương {@code TheoDoiNhiemVu.java}
 * thật.
 *
 * <p><b>Xác nhận quan trọng (đọc trực tiếp source)</b>: đây HOÀN TOÀN ĐỘC LẬP với module vbcddh
 * ("nhiệm vụ giao việc") dù tên gọi "nhiệm vụ" gây hiểu nhầm — {@code tasks} chỉ là 1 field List đơn
 * giản NGAY TRÊN CHÍNH văn bản đến, không tham chiếu document nào khác. View đọc tương ứng
 * ({@code vanbancantheodoi}, dxl 2178-2208) khoá theo field NÀY — xem
 * {@code ViewQueryService#VIEW_VBDEN_VANBANCANTHEODOI}.</p>
 *
 * <p><b>Thuật toán ({@code TheoDoiNhiemVu.java} thật)</b>:</p>
 * <ol>
 *   <li>{@code action="theodoi"}: thêm identifier PHẲNG của người thao tác
 *   ({@code connection.user_token.identifier} = {@code AppUser.getIdentifier()}, KHÔNG phải
 *   {@code getIdentifier_by_level()} — cùng phân biệt đã áp dụng ở {@link CapNhatKetQuaXuLyUseCase})
 *   vào field {@code tasks} trên văn bản cha.</li>
 *   <li>{@code action="huytheodoi"}: loại identifier đó khỏi {@code tasks}.</li>
 *   <li>{@code action="chuyentheodoi"}: Domino gốc — hàm RỖNG (không có code gì bên trong) — port
 *   ĐÚNG bằng no-op (không mutate parent), khác {@code capnhatphoihop}/... (không có nhánh nào để
 *   chạy).</li>
 *   <li>Identifier rỗng (chuyên viên thường không có identifier cấp phòng/đơn vị) → Domino
 *   ({@code addToFieldListStringHideVBden}/{@code removeToFieldListStringHideVBden}) tự
 *   early-return khi giá trị rỗng — port giữ ĐÚNG hành vi này (no-op, vẫn thành công, KHÔNG lỗi).</li>
 *   <li>Tạo bản ghi {@code vbden_res} — LUÔN LUÔN (đúng {@code CreateOrUpdateDocument()} chạy TRƯỚC
 *   switch-case cho MỌI action, cùng quy ước {@link CapNhatPhoiHopUseCase}) — FE gửi body RỖNG
 *   ({@code requestPOST_FORM(url, {})}) nên bản ghi này chỉ có {@code parentid}/{@code action}/
 *   {@code nguoi_thuc_hien} (không có {@code opinion}/{@code creator}/{@code position}).</li>
 * </ol>
 *
 * <p><b>KHÔNG port</b> (cùng kết luận R10/R11 đã áp dụng cho mọi use case anh em trong module này):
 * {@code TDInit.sendSocketNotSave} (thông báo WebSocket — Domino thật đã tự comment out sẵn).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TheoDoiNhiemVuUseCase {

    public static final String DOCUMENT_TYPE = "vbden";
    public static final String CHILD_DOCUMENT_TYPE = "vbden_res";
    public static final String ACTION_THEO_DOI = "theodoi";
    public static final String ACTION_HUY_THEO_DOI = "huytheodoi";
    public static final String ACTION_CHUYEN_THEO_DOI = "chuyentheodoi";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban den";
    private static final String TASKS_FIELD = "tasks";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(TheoDoiNhiemVuRequest request) {
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
        AppUser actingUser = appUserRepository.findByUsernameAndDeletedOnIsNull(actingUsername).orElse(null);
        if (actingUser == null) {
            return Result.failure("Khong tim thay ho so nguoi dung hien tai");
        }

        applyAction(parent, request.getAction(), actingUser);

        return createProcessingRecord(request, actingUsername);
    }

    // Domino: theodoi()/huytheodoi() dung "connection.user_token.identifier" (identifier PHANG cua
    // AppUser, KHONG phai getIdentifier_by_level()); chuyentheodoi() RONG - khong lam gi.
    private void applyAction(BusinessDocument parent, String action, AppUser actingUser) {
        String identifier = nullToEmpty(actingUser.getIdentifier());
        if (identifier.isBlank()) {
            // Domino: addToFieldListStringHideVBden/removeToFieldListStringHideVBden early-return khi
            // gia tri rong - khong mutate gi, van coi la thanh cong.
            return;
        }
        if (ACTION_THEO_DOI.equalsIgnoreCase(action)) {
            mutateTasks(parent, identifier, true);
        } else if (ACTION_HUY_THEO_DOI.equalsIgnoreCase(action)) {
            mutateTasks(parent, identifier, false);
        }
        // ACTION_CHUYEN_THEO_DOI hoac action la khac: Domino khong co nhanh nao chay - khong mutate gi.
    }

    private void mutateTasks(BusinessDocument parent, String identifier, boolean add) {
        var before = buildSnapshot(parent);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson()));

        List<String> tasks = toStringList(fields.get(TASKS_FIELD));
        boolean changed;
        if (add) {
            changed = !tasks.contains(identifier);
            if (changed) {
                tasks.add(identifier);
            }
        } else {
            changed = tasks.remove(identifier);
        }
        if (!changed) {
            return;
        }
        fields.put(TASKS_FIELD, tasks);
        parent.replaceDynamicFields(toJson(fields));

        var saved = documentRepository.save(parent);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino CapNhatThongTin(): docQTXL = CreateOrUpdateDocument()... docQTXL.save() - chay TRUOC
    // switch-case cho MOI action, cung quy uoc CapNhatPhoiHopUseCase/KetThucXuLyUseCase. FE gui body
    // RONG ({}) nen khong co opinion/creator/position de gan.
    private Result<UUID> createProcessingRecord(TheoDoiNhiemVuRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(request.getAction());
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> childFields = new LinkedHashMap<>();
        childFields.put("parentid", request.getParentid().toString());
        childFields.put("action", request.getAction());
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
