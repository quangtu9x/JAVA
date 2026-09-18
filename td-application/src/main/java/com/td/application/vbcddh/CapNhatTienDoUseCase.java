package com.td.application.vbcddh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-250 (VBCDDH Core) — DoD (3): theo dõi/cập nhật tiến độ thực hiện nhiệm vụ. Domino thật tạo 1
 * document con trong database {@code _res} riêng (bằng chứng trực tiếp: {@code LuuVet()} trong
 * {@code ChuyenXuLyVBCDDH.java} tạo document {@code form="tiendonhiemvu"} trong
 * {@code vbcddh_resDatabase}, field {@code idnhiemvu} trỏ về nhiệm vụ cha) — đúng cơ chế
 * {@code vbden}/{@code vbden_res} (BL-210/BL-211) đã dùng, nên tái dùng nguyên vẹn kiến trúc đó.
 * Khi hành động là {@code capnhatketquaxuly} (kết thúc xử lý), set {@code tinhtrang="Đã thực hiện"}
 * trên nhiệm vụ rồi kích hoạt {@link VbcddhStatusRollupService} để lan trạng thái lên văn bản gốc.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatTienDoUseCase {

    public static final String DOCUMENT_TYPE = "vbcddh";
    public static final String CHILD_DOCUMENT_TYPE = "vbcddh_res";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy nhiệm vụ";
    private static final String FORM_NHIEMVU = "nhiemvu";
    private static final String ACTION_KET_QUA = "capnhatketquaxuly";
    private static final String TINHTRANG_FIELD = "tinhtrang";
    private static final String IDVANBAN_FIELD = "idvanban";
    private static final String CHUA_THUC_HIEN = "Chưa thực hiện";
    private static final String DANG_THUC_HIEN = "Đang thực hiện";
    private static final String DA_THUC_HIEN = "Đã thực hiện";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;
    private final VbcddhStatusRollupService statusRollupService;

    public Result<UUID> execute(CapNhatTienDoRequest request) {
        var nhiemVuOptional = vbcddhDocumentRepository.findById(request.getIdNhiemVu());
        if (nhiemVuOptional.isEmpty() || nhiemVuOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var nhiemVu = nhiemVuOptional.get();
        Map<String, Object> nhiemVuFields = dynamicDocumentFieldsService.parseDynamicFields(nhiemVu.getDynamicFieldsJson());
        if (!DOCUMENT_TYPE.equalsIgnoreCase(nhiemVu.getDocumentType())
                || !FORM_NHIEMVU.equalsIgnoreCase(asString(nhiemVuFields.get("form")))) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse("system");
        boolean isKetQua = ACTION_KET_QUA.equalsIgnoreCase(request.getAction());

        var childResult = createProgressRecord(request, actingUsername, isKetQua);
        if (!childResult.isSuccess()) {
            return childResult;
        }

        updateTinhTrangIfNeeded(nhiemVu, nhiemVuFields, isKetQua);

        String idVanBan = asString(nhiemVuFields.get(IDVANBAN_FIELD));
        if (idVanBan != null && !idVanBan.isBlank()) {
            try {
                statusRollupService.recomputeParentStatus(UUID.fromString(idVanBan));
            } catch (IllegalArgumentException ignored) {
                // idvanban không phải UUID hợp lệ (dữ liệu cũ/di trú) — bỏ qua rollup, không chặn cập nhật tiến độ.
            }
        }

        return Result.success(childResult.getData());
    }

    // Domino: ChuyenXuLyVBCDDH.LuuVet() — tạo document form=tiendonhiemvu/ketquaxulynhiemvu trong _res.
    private Result<UUID> createProgressRecord(CapNhatTienDoRequest request, String actingUsername, boolean isKetQua) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle(request.getAction());
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("idnhiemvu", request.getIdNhiemVu().toString());
        fields.put("form", isKetQua ? "ketquaxulynhiemvu" : "tiendonhiemvu");
        fields.put("creator", actingUsername);
        if (request.getYKien() != null && !request.getYKien().isBlank()) {
            fields.put("ykien", request.getYKien());
        }
        childRequest.setFields(fields);

        return addVbcddhDynamicDocumentUseCase.execute(childRequest);
    }

    private void updateTinhTrangIfNeeded(VbcddhDocument nhiemVu, Map<String, Object> nhiemVuFields, boolean isKetQua) {
        String currentTinhTrang = asString(nhiemVuFields.get(TINHTRANG_FIELD));
        String newTinhTrang;
        if (isKetQua) {
            newTinhTrang = DA_THUC_HIEN;
        } else if (CHUA_THUC_HIEN.equals(currentTinhTrang)) {
            newTinhTrang = DANG_THUC_HIEN;
        } else {
            return;
        }
        if (newTinhTrang.equals(currentTinhTrang)) {
            return;
        }

        var before = buildSnapshot(nhiemVu);
        Map<String, Object> updatedFields = new LinkedHashMap<>(nhiemVuFields);
        updatedFields.put(TINHTRANG_FIELD, newTinhTrang);
        nhiemVu.replaceDynamicFields(toJson(updatedFields));
        var saved = vbcddhDocumentRepository.save(nhiemVu);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(VbcddhDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
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
