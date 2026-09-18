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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng "đã gắn được, nhưng nút xóa ở danh sách chưa port, đang gọi api cũ
 * qlvb/add?id=...&databasetype=duthao"): port {@code TraLoiChoVanBanDen.jsx#handleDelete} khi mở
 * TỪ chi tiết dự thảo — SAO CHÉP Y HỆT thuật toán đã port cho văn bản đi ở
 * {@link com.td.application.vbdi.XoaTraLoiVanBanDenUseCase} (đọc javadoc lớp đó để biết đầy đủ đối
 * chiếu Domino gốc), chỉ khác văn bản CHA giữ {@code documentin_obj} là dự thảo
 * ({@code duthao_documents}) thay vì văn bản đi ({@code vbdi_documents}) — loại đúng 1 phần tử khỏi
 * {@code documentin_obj} theo {@code documentId} (server tự tìm, không tin nguyên mảng FE gửi lên).
 *
 * <p><b>CHỦ Ý không hoàn tác side-effect của {@link TraLoiVanBanDenDuThaoUseCase}</b>: khi thêm 1 văn
 * bản đến vào {@code documentin_obj}, use case đó có chuyển {@code user_process}/{@code user_returned}
 * sang {@code user_processing} trên CHÍNH văn bản đến VÀ tạo 1 bản ghi {@code vbden_res}
 * ({@code action=ganduthaotraloi}) — xoá khỏi danh sách hiển thị ở đây KHÔNG hoàn tác 2 side-effect
 * đó (đúng Domino gốc, cùng quyết định đã áp dụng cho văn bản đi).</p>
 *
 * <p>Không tìm thấy phần tử cần xoá → vẫn trả THÀNH CÔNG (no-op, cùng quy ước
 * {@link com.td.application.vbdi.XoaTraLoiVanBanDenUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaTraLoiVanBanDenDuThaoUseCase {

    private static final String DOCUMENT_TYPE = ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE;
    private static final String DOCUMENT_IN_FIELD = "documentin_obj";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(XoaTraLoiVanBanDenDuThaoRequest request) {
        if (request == null || request.getDraftId() == null) {
            return Result.failure("Thieu du thao can xoa van ban den da tra loi");
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

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));
        List<Object> existingList = toObjectList(fields.get(DOCUMENT_IN_FIELD));
        String targetId = request.getDocumentId().trim();

        List<Object> remaining = new ArrayList<>();
        boolean removed = false;
        for (Object item : existingList) {
            if (!removed && item instanceof Map<?, ?> map && targetId.equals(String.valueOf(map.get("id")))) {
                removed = true;
                continue;
            }
            remaining.add(item);
        }

        if (!removed) {
            return Result.success(draft.getId());
        }

        var before = buildSnapshot(draft);
        fields.put(DOCUMENT_IN_FIELD, remaining);
        draft.replaceDynamicFields(toJson(fields));
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Xoa van ban den {} khoi documentin_obj cua du thao {}", targetId, saved.getId());
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
