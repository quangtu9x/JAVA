package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documentlinks.DocumentLinkService;
import com.td.application.documentlinks.RelatedDocumentSnapshots;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
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
 * 11/09/2026 (yêu cầu người dùng "đã hiển thị danh sách, chức năng xóa ở danh sách chưa cutover FE"):
 * port {@code VanBanLienQuanList.jsx#handleDelete} cho danh sách "Văn bản kèm theo" thuộc văn bản đi
 * (nút xoá 1 dòng — {@link GanVanBanLienQuanUseCase} chỉ có chiều thêm, chưa có chiều xoá).
 *
 * <p>Domino gốc: FE tự lọc (filter) phần tử khỏi mảng {@code vanban_lienquan_obj} rồi GHI ĐÈ TOÀN BỘ
 * field qua {@code qlvb/add?id={id}} (JSON.stringify(updatedData)) — client-side filter, không có xác
 * thực lại phía server phần tử nào bị xoá. Ở đây SERVER tự loại đúng 1 phần tử theo {@code documentId}
 * (không tin nguyên mảng FE gửi lên — tránh 1 request cũ/stale ghi đè mất các thay đổi song song khác
 * lên field này), cùng nguyên tắc "server tự dedup/tự dựng lại field" đã áp dụng ở
 * {@link GanVanBanLienQuanUseCase}/{@link TraLoiVanBanDenUseCase}.</p>
 *
 * <p>Không tìm thấy phần tử cần xoá (đã bị xoá từ trước, hoặc {@code documentId} sai) → vẫn trả
 * THÀNH CÔNG (no-op) — đúng tinh thần "xoá cái không tồn tại thì coi như đã xoá", tránh báo lỗi giả cho
 * FE khi 2 tab cùng thao tác.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaVanBanLienQuanUseCase {

    private static final String RELATED_FIELD = "vanban_lienquan_obj";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final DocumentLinkService documentLinkService;

    public Result<UUID> execute(XoaVanBanLienQuanRequest request) {
        if (request == null || request.getVbdiId() == null) {
            return Result.failure("Thieu van ban di can xoa van ban lien quan");
        }
        if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
            return Result.failure("Thieu van ban can xoa");
        }

        var vbdiOptional = vbdiDocumentRepository.findById(request.getVbdiId());
        if (vbdiOptional.isEmpty() || !isUsableVbdiDocument(vbdiOptional.get())) {
            return Result.failure("Khong tim thay van ban di");
        }
        var vbdiDocument = vbdiOptional.get();

        // 06/10/2026 (bang document_links giai doan 2 - docs/architecture/document-links.md): go lien ket
        // "Van ban kem theo" (vbdi_kemtheo_*) = xoa mem 1 dong, khong ghi lai dynamic_fields. documentId khong phai UUID (phan tu
        // Domino cu) -> khong co lien ket nao, tra success nhu truoc (khong co gi de go).
        java.util.UUID targetId;
        try {
            targetId = java.util.UUID.fromString(request.getDocumentId().trim());
        } catch (IllegalArgumentException ex) {
            return Result.success(vbdiDocument.getId());
        }
        boolean removed = false;
        for (DocumentLinkType type : RelatedDocumentSnapshots.relatedTypes(RelatedDocumentSnapshots.SOURCE_VBDI)) {
            removed |= documentLinkService.unlink(type, vbdiDocument.getId(), targetId);
        }
        if (removed) {
            documentCacheService.evict(vbdiDocument.getId());
            documentCacheService.evictAllListCaches();
        }
        return Result.success(vbdiDocument.getId());
    }

    private boolean isUsableVbdiDocument(VbdiDocument document) {
        return !document.isDeleted()
            && SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType());
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
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
