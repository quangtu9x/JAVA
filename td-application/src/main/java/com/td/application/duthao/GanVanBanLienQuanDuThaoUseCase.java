package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng "Văn bản kèm theo -> tương tự khi mở modal chọn văn bản và đồng ý
 * cũng chưa port"): port hợp nhất 2 action {@code ganvanbanden}/{@code ganvanbandi} của
 * {@code DuThaoActionModal.jsx#btnActionGanVanBanDen}/{@code btnActionGanVanBanDi} (Domino
 * {@code qlvb/add?id={draftId}&databasetype=duthao} lưu field {@code vanban_lienquan_obj}) — SAO
 * CHÉP Y HỆT thuật toán đã xác nhận + port cho văn bản đi ở
 * {@link com.td.application.vbdi.GanVanBanLienQuanUseCase} (đọc javadoc lớp đó để biết đầy đủ đối
 * chiếu Domino gốc: field {@code vanban_lienquan_obj} được lưu HOÀN TOÀN GENERIC, KHÔNG có
 * write-back nào lên văn bản được tham chiếu, KHÔNG đụng {@code user_process}, KHÔNG tạo bản ghi con
 * nào — khác hẳn {@link TraLoiVanBanDenDuThaoUseCase}), chỉ khác văn bản CHA là dự thảo
 * ({@code duthao_documents}) thay vì văn bản đi ({@code vbdi_documents}).
 *
 * <p>Dedup theo {@code id} (server tự dedup lại, không tin FE đã lọc trùng đúng) — gọi lại nhiều lần
 * chỉ nối thêm phần tử MỚI, không nhân đôi.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GanVanBanLienQuanDuThaoUseCase {

    private static final String DOCUMENT_TYPE = ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE;
    private static final String RELATED_FIELD = "vanban_lienquan_obj";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(GanVanBanLienQuanDuThaoRequest request) {
        if (request == null || request.getDraftId() == null) {
            return Result.failure("Thieu du thao can gan");
        }
        if (request.getRelatedDocuments() == null || request.getRelatedDocuments().isEmpty()) {
            return Result.failure("Chua chon van ban can gan");
        }

        var draftOptional = duThaoDocumentRepository.findById(request.getDraftId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(draftOptional.get().getDocumentType())) {
            return Result.failure("Khong tim thay du thao");
        }
        var draft = draftOptional.get();

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));
        List<Object> existingList = toObjectList(fields.get(RELATED_FIELD));
        Set<String> existingIds = new LinkedHashSet<>();
        for (Object item : existingList) {
            if (item instanceof Map<?, ?> map && map.get("id") != null) {
                existingIds.add(map.get("id").toString());
            }
        }

        var before = buildSnapshot(draft);
        int added = 0;
        for (Map<String, Object> item : request.getRelatedDocuments()) {
            Object rawId = item == null ? null : item.get("id");
            if (rawId == null || rawId.toString().isBlank()) {
                continue;
            }
            String idText = rawId.toString().trim();
            if (existingIds.add(idText)) {
                Map<String, Object> entry = new LinkedHashMap<>(item);
                entry.put("id", idText);
                existingList.add(entry);
                added++;
            }
        }

        if (added == 0) {
            // Toan bo da co san (dedup het) - khong co gi de luu, tra ve thanh cong (dung Domino goc,
            // khong bao loi khi bam lai "Dong y" voi cung 1 van ban da gan).
            return Result.success(draft.getId());
        }

        fields.put(RELATED_FIELD, existingList);
        draft.replaceDynamicFields(toJson(fields));
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Gan {} van ban lien quan vao du thao {}", added, saved.getId());
        return Result.success(saved.getId());
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private List<Object> toObjectList(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            result.addAll(list);
        }
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
