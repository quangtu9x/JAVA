package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * BL-240 (HoSoCV Core) — DoD (2): gắn văn bản đến/đi/dự thảo vào hồ sơ công việc. Đúng cơ chế thật
 * ({@code GanKemVanBan.XuLyGanKemVanBanVaoHoSoCV_Chung()}) — 1 field trên phía văn bản
 * ({@code field_in_van_ban}, luôn {@code document_profiles_id} trừ 2 ngoại lệ) + 1 field trên phía
 * hồ sơ ({@code field_in_ho_so}), cả 2 dạng chuỗi nối {@code ";"}, chọn theo {@code actionName}:
 *
 * <ul>
 *   <li>{@code gankemduthao}: {@code document_profiles_id} / {@code duthao_attached_id}</li>
 *   <li>{@code ganvanbandi}: {@code document_profiles_id} / {@code document_out_attached_id}</li>
 *   <li>{@code ganvanbanphathanh}: {@code issuance_document_profiles_id} /
 *       {@code document_out_attached_id}</li>
 *   <li>{@code ganvanbanden}: {@code document_profiles_id} / {@code document_in_attached_id}</li>
 *   <li>{@code ganvanbandenxuly}: {@code document_profiles_handling_id} /
 *       {@code document_in_attached_id}</li>
 *   <li>{@code gankemphieutrinh}: {@code document_profiles_id} /
 *       {@code submission_dossier_attached_id}</li>
 * </ul>
 *
 * <p><b>Sửa 01/09/2026</b>: gắn thêm giờ ĐỐI XỨNG với remove (ghi CẢ 2 phía cho MỌI actionName) —
 * xem lịch sử javadoc trước đây trong git.
 *
 * <p><b>Mở rộng — sửa BUG TRA SAI BẢNG (ưu tiên cao nhất theo
 * {@code docs/analysis/13-checklist-port-hosocv-domino-sang-java.md} Phần 3)</b>: bản trước đây tra
 * CẢ hồ sơ lẫn văn bản gắn kèm qua 1 {@link DocumentRepository} duy nhất (bảng {@code documents}
 * chung). Nhưng từ 25/08/2026, {@code duthao} và {@code vbdi} đã tách hẳn sang bảng vật lý riêng
 * ({@code duthao_documents}/{@code vbdi_documents}) — {@code documentRepository.findById(vanBanId)}
 * cho 3/6 actionName ({@code gankemduthao}/{@code ganvanbandi}/{@code ganvanbanphathanh}) LUÔN trả
 * rỗng, bị bỏ qua âm thầm (API vẫn trả {@code success} giả — gắn kèm không có tác dụng gì). Nay hồ
 * sơ công việc CŨNG đã tách bảng riêng ({@code hosocv_documents}), nên phải sửa CẢ 2 phía:
 * <ol>
 *   <li>Phía hồ sơ: tra qua {@link HoSoCVDocumentRepository} (không phải {@code DocumentRepository}).</li>
 *   <li>Phía văn bản: dispatch theo {@code actionName} → loại văn bản mong đợi
 *       ({@link #ACTION_DOCUMENT_TYPE_MAP}) → đúng repository ({@code duthao}→
 *       {@link DuThaoDocumentRepository}, {@code vbdi}→{@link VbdiDocumentRepository}, còn lại
 *       ({@code vbden}/{@code phieutrinh} — chưa có module riêng) → {@link DocumentRepository}).</li>
 * </ol>
 * Vì 4 kiểu entity ({@code HoSoCVDocument}/{@code DuThaoDocument}/{@code VbdiDocument}/
 * {@code BusinessDocument}) không chia sẻ interface chung cho {@code dynamicFieldsJson}, dùng 1
 * record nội bộ {@link VanBanHandle} (id + "đọc field động hiện tại" + "ghi lại") để
 * {@code updateVanBanLinkField}/{@code syncVbdiFromDraft} thao tác chung 1 công thức bất kể loại
 * bảng vật lý nào — {@code currentDynamicFieldsJson} là {@code Supplier} (không phải giá trị đã
 * chụp sẵn) để 2 lần gọi liên tiếp trên cùng 1 văn bản (field phía văn bản rồi
 * {@code hoso_attached_id}) đọc đúng trạng thái MỚI NHẤT sau lần ghi đầu, không bị lệch dữ liệu.
 *
 * <p>Không validate loại document của {@code vanBanId} có THẬT SỰ khớp {@code actionName} không
 * (Domino cũng không validate — tin tưởng FE gửi đúng loại/id); {@link #ACTION_DOCUMENT_TYPE_MAP}
 * chỉ quyết định TRA Ở BẢNG NÀO, không phải validate ngược lại.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GanKemVanBanUseCase {

    public static final String DOCUMENT_TYPE = "hosocv";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String INVALID_ACTION_NAME_MESSAGE = "Không xác định được loại gắn kèm (actionName)";
    private static final String REMOVE_ACTION = "remove";
    private static final String DUTHAO_ACTION_NAME = "gankemduthao";
    private static final String DUTHAO_DOCUMENT_OUT_ID_FIELD = "document_out_id";
    private static final String VBDI_FROM_DRAFT_FIELD = "document_profiles_from_draft_id";
    private static final String HOSO_ATTACHED_ID_FIELD = "hoso_attached_id";
    private static final String TYPE_DUTHAO = "duthao";
    private static final String TYPE_VBDI = "vbdi";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // actionName -> {field_in_van_ban, field_in_ho_so}
    private static final Map<String, String[]> ACTION_FIELD_MAP = Map.of(
        DUTHAO_ACTION_NAME, new String[]{"document_profiles_id", "duthao_attached_id"},
        "ganvanbandi", new String[]{"document_profiles_id", "document_out_attached_id"},
        "ganvanbanphathanh", new String[]{"issuance_document_profiles_id", "document_out_attached_id"},
        "ganvanbanden", new String[]{"document_profiles_id", "document_in_attached_id"},
        "ganvanbandenxuly", new String[]{"document_profiles_handling_id", "document_in_attached_id"},
        "gankemphieutrinh", new String[]{"document_profiles_id", "submission_dossier_attached_id"}
    );

    // actionName -> loai van ban MONG DOI (quyet dinh tra o repository/bang vat ly nao). "vbden"/
    // "phieutrinh" van nam bang "documents" dung chung (chua tach rieng - phieutrinh chua co module).
    private static final Map<String, String> ACTION_DOCUMENT_TYPE_MAP = Map.of(
        DUTHAO_ACTION_NAME, TYPE_DUTHAO,
        "ganvanbandi", TYPE_VBDI,
        "ganvanbanphathanh", TYPE_VBDI,
        "ganvanbanden", "vbden",
        "ganvanbandenxuly", "vbden",
        "gankemphieutrinh", "phieutrinh"
    );

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    /** Tay cam trung lap cho 1 van ban gan kem, bat ke nam o bang vat ly nao. */
    private record VanBanHandle(
        UUID id, Supplier<String> currentDynamicFieldsJson, Consumer<String> persistDynamicFields) {
    }

    public Result<UUID> execute(GanKemVanBanRequest request) {
        String normalizedAction = normalize(request.getActionName());
        String[] fieldPair = ACTION_FIELD_MAP.get(normalizedAction);
        if (fieldPair == null) {
            return Result.failure(INVALID_ACTION_NAME_MESSAGE);
        }
        String fieldInVanBan = fieldPair[0];
        String fieldInHoSo = fieldPair[1];

        var hosocvOptional = hoSoCVDocumentRepository.findById(request.getHosocvId());
        if (hosocvOptional.isEmpty() || hosocvOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(hosocvOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var hosocv = hosocvOptional.get();
        String hosocvIdStr = hosocv.getId().toString();

        boolean isRemove = REMOVE_ACTION.equalsIgnoreCase(request.getAction());
        boolean isDuThao = DUTHAO_ACTION_NAME.equals(normalizedAction);
        List<UUID> vanBanIds = request.getVanBanIds() == null ? List.of() : request.getVanBanIds();
        // Danh sach van ban da xu ly that su (chi tinh khi resolveVanBan tim thay dung bang) - khac
        // ban truoc day (documentRepository.findById lai khong bao gio thay 3/6 actionName do bug
        // tra sai bang).
        List<String> processedVanBanIds = new ArrayList<>();

        for (UUID vanBanId : vanBanIds) {
            Optional<VanBanHandle> vanBanHandleOptional = resolveVanBan(normalizedAction, vanBanId);
            if (vanBanHandleOptional.isEmpty()) {
                continue;
            }
            VanBanHandle vanBanHandle = vanBanHandleOptional.get();
            String vanBanIdStr = vanBanId.toString();

            updateVanBanLinkField(vanBanHandle, fieldInVanBan, hosocvIdStr, isRemove);
            updateVanBanLinkField(vanBanHandle, HOSO_ATTACHED_ID_FIELD, hosocvIdStr, isRemove);
            processedVanBanIds.add(vanBanIdStr);
            if (isDuThao) {
                syncVbdiFromDraft(vanBanHandle, hosocvIdStr, isRemove);
            }
        }

        if (!processedVanBanIds.isEmpty()) {
            if (isRemove) {
                removeManyFromLinkField(hosocv, fieldInHoSo, processedVanBanIds);
            } else {
                addManyToLinkField(hosocv, fieldInHoSo, processedVanBanIds);
            }
        }

        return Result.success(hosocv.getId());
    }

    // Dispatch theo loai van ban MONG DOI cua actionName (ACTION_DOCUMENT_TYPE_MAP) sang dung
    // repository/bang vat ly - day la phan sua BUG TRA SAI BANG (xem javadoc lop).
    private Optional<VanBanHandle> resolveVanBan(String actionName, UUID vanBanId) {
        String expectedType = ACTION_DOCUMENT_TYPE_MAP.getOrDefault(actionName, "");
        return switch (expectedType) {
            case TYPE_DUTHAO -> duThaoDocumentRepository.findById(vanBanId)
                .filter(document -> !document.isDeleted())
                .map(document -> new VanBanHandle(
                    document.getId(), document::getDynamicFieldsJson,
                    json -> {
                        document.replaceDynamicFields(json);
                        duThaoDocumentRepository.save(document);
                    }));
            case TYPE_VBDI -> vbdiDocumentRepository.findById(vanBanId)
                .filter(document -> !document.isDeleted())
                .map(document -> new VanBanHandle(
                    document.getId(), document::getDynamicFieldsJson,
                    json -> {
                        document.replaceDynamicFields(json);
                        vbdiDocumentRepository.save(document);
                    }));
            default -> documentRepository.findById(vanBanId)
                .filter(document -> !document.isDeleted())
                .map(document -> new VanBanHandle(
                    document.getId(), document::getDynamicFieldsJson,
                    json -> {
                        document.replaceDynamicFields(json);
                        documentRepository.save(document);
                    }));
        };
    }

    private void syncVbdiFromDraft(VanBanHandle duThaoHandle, String hosocvIdStr, boolean remove) {
        Map<String, Object> duThaoFields = dynamicDocumentFieldsService.parseDynamicFields(duThaoHandle.currentDynamicFieldsJson().get());
        String vbdiIdStr = asString(duThaoFields.get(DUTHAO_DOCUMENT_OUT_ID_FIELD));
        if (vbdiIdStr == null || vbdiIdStr.isBlank()) {
            return;
        }
        UUID vbdiId;
        try {
            vbdiId = UUID.fromString(vbdiIdStr.trim());
        } catch (IllegalArgumentException ex) {
            return;
        }
        vbdiDocumentRepository.findById(vbdiId)
            .filter(document -> !document.isDeleted())
            .ifPresent(document -> {
                VanBanHandle vbdiHandle = new VanBanHandle(
                    document.getId(), document::getDynamicFieldsJson,
                    json -> {
                        document.replaceDynamicFields(json);
                        vbdiDocumentRepository.save(document);
                    });
                updateVanBanLinkField(vbdiHandle, VBDI_FROM_DRAFT_FIELD, hosocvIdStr, remove);
            });
    }

    // Cong thuc chung cho MOI loai van ban (bat ke bang vat ly nao) - doc lai field dong MOI NHAT
    // qua currentDynamicFieldsJson() (Supplier, khong phai gia tri chup san) de 2 lan goi lien tiep
    // tren CUNG 1 van ban (fieldInVanBan roi HOSO_ATTACHED_ID_FIELD) thay doi dung tren trang thai
    // moi nhat, khong bi lech du lieu.
    private void updateVanBanLinkField(VanBanHandle handle, String fieldName, String value, boolean remove) {
        Map<String, Object> before = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(handle.currentDynamicFieldsJson().get()));
        List<String> values = toSemicolonList(before.get(fieldName));

        boolean changed;
        if (remove) {
            changed = values.remove(value);
        } else {
            changed = !values.contains(value);
            if (changed) {
                values.add(value);
            }
        }
        if (!changed) {
            return;
        }

        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put(fieldName, String.join(";", values));
        handle.persistDynamicFields().accept(toJson(after));
        documentCacheService.evict(handle.id());
        documentLogService.logUpdate(handle.id(), before, after);
    }

    // 01/09/2026: doi ung voi removeManyFromLinkField - ghi field phia HO SO khi gan them (xem
    // javadoc lop). Dedup rieng cho tung gia tri (khop XuLyGanKemDuThaoVaoHoSoVC() that: kiem tra
    // lastIndexOf truoc khi noi ";"). Hosocv gio la HoSoCVDocument (bang rieng hosocv_documents),
    // khong con dung chung BusinessDocument nua.
    private void addManyToLinkField(HoSoCVDocument document, String fieldName, List<String> valuesToAdd) {
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        List<String> values = toSemicolonList(fields.get(fieldName));
        boolean changed = false;
        for (String value : valuesToAdd) {
            if (!values.contains(value)) {
                values.add(value);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        var before = buildSnapshot(document);
        fields.put(fieldName, String.join(";", values));
        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private void removeManyFromLinkField(HoSoCVDocument document, String fieldName, List<String> valuesToRemove) {
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        List<String> values = toSemicolonList(fields.get(fieldName));
        boolean changed = values.removeAll(valuesToRemove);
        if (!changed) {
            return;
        }

        var before = buildSnapshot(document);
        fields.put(fieldName, String.join(";", values));
        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private List<String> toSemicolonList(Object value) {
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
