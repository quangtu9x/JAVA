package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.documentlinks.RelatedDocumentSnapshots;
import com.td.domain.documentlinks.DocumentLinkType;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng "Văn bản kèm theo -> ... chưa port"): port
 * {@code VanBanLienQuanList.jsx#handleDelete} khi mở TỪ chi tiết dự thảo — SAO CHÉP Y HỆT thuật toán
 * đã port cho văn bản đi ở {@link com.td.application.vbdi.XoaVanBanLienQuanUseCase} (đọc javadoc lớp
 * đó để biết đầy đủ đối chiếu Domino gốc), chỉ khác văn bản CHA giữ {@code vanban_lienquan_obj} là dự
 * thảo ({@code duthao_documents}) thay vì văn bản đi ({@code vbdi_documents}) — loại đúng 1 phần tử
 * khỏi {@code vanban_lienquan_obj} theo {@code documentId} (server tự tìm, không tin nguyên mảng FE
 * gửi lên).
 *
 * <p>Không tìm thấy phần tử cần xoá → vẫn trả THÀNH CÔNG (no-op, cùng quy ước
 * {@link com.td.application.vbdi.XoaVanBanLienQuanUseCase}/{@link XoaTraLoiVanBanDenDuThaoUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaVanBanLienQuanDuThaoUseCase {

    private static final String DOCUMENT_TYPE = ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE;
    private static final String RELATED_FIELD = "vanban_lienquan_obj";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final DocumentLinkService documentLinkService;

    public Result<UUID> execute(XoaVanBanLienQuanDuThaoRequest request) {
        if (request == null || request.getDraftId() == null) {
            return Result.failure("Thieu du thao can xoa van ban lien quan");
        }
        if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
            return Result.failure("Thieu van ban can xoa");
        }

        var draftOptional = duThaoDocumentRepository.findById(request.getDraftId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(draftOptional.get().getDocumentType())) {
            return Result.failure("Khong tim thay du thao");
        }
        var draft = draftOptional.get();

        // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): go lien ket
        // "Van ban kem theo" (duthao_kemtheo_*) = xoa mem 1 dong, khong ghi lai dynamic_fields. documentId khong phai UUID (phan tu
        // Domino cu) -> khong co lien ket nao, tra success nhu truoc (khong co gi de go).
        java.util.UUID targetId;
        try {
            targetId = java.util.UUID.fromString(request.getDocumentId().trim());
        } catch (IllegalArgumentException ex) {
            return Result.success(draft.getId());
        }
        boolean removed = false;
        for (DocumentLinkType type : RelatedDocumentSnapshots.relatedTypes(RelatedDocumentSnapshots.SOURCE_DUTHAO)) {
            removed |= documentLinkService.unlink(type, draft.getId(), targetId);
        }
        if (removed) {
            documentCacheService.evict(draft.getId());
            documentCacheService.evictAllListCaches();
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
