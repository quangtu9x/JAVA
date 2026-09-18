package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
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
 * 11/09/2026 (yêu cầu người dùng "modal Trả lời văn bản đến bị lỗi xóa tương tự" — cùng gap vừa sửa ở
 * {@link XoaVanBanLienQuanUseCase} cho danh sách "Văn bản kèm theo", nay áp dụng cho danh sách "Trả
 * lời cho văn bản đến"): port {@code TraLoiChoVanBanDen.jsx#handleDelete} — loại 1 phần tử khỏi field
 * {@code documentin_obj} trên văn bản đi ({@link TraLoiVanBanDenUseCase} chỉ có chiều thêm/nối, chưa có
 * chiều xoá).
 *
 * <p>Domino gốc: CÙNG cơ chế {@code vanban_lienquan_obj} — FE tự lọc mảng rồi ghi đè toàn bộ field qua
 * {@code qlvb/add?id={id}}. Ở đây SERVER tự loại đúng 1 phần tử theo {@code documentId} (không tin
 * nguyên mảng FE gửi lên — cùng nguyên tắc {@link XoaVanBanLienQuanUseCase}).</p>
 *
 * <p><b>CHỦ Ý không hoàn tác side-effect của {@link TraLoiVanBanDenUseCase}</b>: khi thêm 1 văn bản đến
 * vào {@code documentin_obj}, use case đó có chuyển {@code user_process}/{@code user_returned} sang
 * {@code user_processing} trên CHÍNH văn bản đến VÀ tạo 1 bản ghi {@code vbden_res}
 * ({@code action=ganduthaotraloi}) — xoá khỏi danh sách hiển thị ở đây KHÔNG hoàn tác 2 side-effect đó
 * (đúng Domino gốc: {@code handleDelete} chỉ ghi đè lại field hiển thị trên văn bản đi, hoàn toàn không
 * đụng tới văn bản đến/bản ghi quá trình xử lý đã tạo trước đó — xác nhận qua đọc trực tiếp
 * {@code TraLoiChoVanBanDen.jsx}, không có lời gọi nào khác ngoài {@code qlvb/add}).</p>
 *
 * <p>Không tìm thấy phần tử cần xoá → vẫn trả THÀNH CÔNG (no-op, cùng quy ước
 * {@link XoaVanBanLienQuanUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaTraLoiVanBanDenUseCase {

    private static final String DOCUMENT_IN_FIELD = "documentin_obj";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(XoaTraLoiVanBanDenRequest request) {
        if (request == null || request.getVbdiId() == null) {
            return Result.failure("Thieu van ban di can xoa van ban den da tra loi");
        }
        if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
            return Result.failure("Thieu van ban can xoa");
        }

        var vbdiOptional = vbdiDocumentRepository.findById(request.getVbdiId());
        if (vbdiOptional.isEmpty() || !isUsableVbdiDocument(vbdiOptional.get())) {
            return Result.failure("Khong tim thay van ban di");
        }
        var vbdiDocument = vbdiOptional.get();

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(vbdiDocument.getDynamicFieldsJson()));
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
            return Result.success(vbdiDocument.getId());
        }

        var before = buildSnapshot(vbdiDocument);
        fields.put(DOCUMENT_IN_FIELD, remaining);
        vbdiDocument.replaceDynamicFields(toJson(fields));
        var saved = vbdiDocumentRepository.save(vbdiDocument);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Xoa van ban den {} khoi documentin_obj cua van ban di {}", targetId, saved.getId());
        return Result.success(saved.getId());
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
