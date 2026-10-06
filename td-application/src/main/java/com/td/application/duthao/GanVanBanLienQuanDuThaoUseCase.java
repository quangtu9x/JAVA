package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documentlinks.RelatedDocumentSnapshots;
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
    private final RelatedDocumentSnapshots relatedDocumentSnapshots;

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

        // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): moi phan tu =
        // 1 lien ket duthao_kemtheo_{vbden|vbdi} theo database_type (ban chup o link_data), khong ghi
        // vanban_lienquan_obj vao dynamic_fields. Dedup theo id; phan tu id khong phai UUID bi bo qua.
        int added = 0;
        for (Map<String, Object> item : request.getRelatedDocuments()) {
            if (item == null) {
                continue;
            }
            Object databaseType = item.get("database_type");
            var type = RelatedDocumentSnapshots.relatedType(
                RelatedDocumentSnapshots.SOURCE_DUTHAO, databaseType == null ? null : databaseType.toString());
            if (relatedDocumentSnapshots.linkItem(type, draft.getId(), item)) {
                added++;
            }
        }
        if (added > 0) {
            documentCacheService.evict(draft.getId());
            documentCacheService.evictAllListCaches();
            log.info("Gan {} van ban lien quan vao du thao {}", added, draft.getId());
        }
        return Result.success(draft.getId());
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
