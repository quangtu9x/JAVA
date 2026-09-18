package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
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
 * Mở rộng BL-240 (HoSoCV Core) — G3/G9 ({@code docs/analysis/13-...md} Phần 2.3 "Xử lý lại hồ sơ",
 * bổ sung so với tài liệu 08): đưa hồ sơ bị lãnh đạo TỪ CHỐI DUYỆT ({@code trinhlanhdaoduyet="3"})
 * về lại trạng thái xử lý bình thường để trình lại — {@code trinhlanhdaoduyet: "3" → ""}. Nhận danh
 * sách id, khớp FE {@code xulylaihoso}/{@code xulylainhieuhoso}.
 *
 * <p>Chỉ áp dụng cho hồ sơ đang {@code trinhlanhdaoduyet="3"} — hồ sơ khác trạng thái bị bỏ qua
 * (đúng quy tắc thao tác hàng loạt {@code docs/architecture/04-...md} mục 4).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XuLyLaiHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<List<UUID>> execute(XuLyLaiHoSoRequest request) {
        if (request == null || request.getHosocvIds() == null || request.getHosocvIds().isEmpty()) {
            return Result.failure("Danh sách hồ sơ không được để trống");
        }
        List<UUID> updated = new ArrayList<>();

        for (UUID id : request.getHosocvIds()) {
            hoSoCVDocumentRepository.findById(id).ifPresent(document -> {
                if (document.isDeleted() || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
                    return;
                }
                Map<String, Object> fields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
                Object currentTrinh = fields.get(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET);
                if (!HoSoCVLifecycleStatus.TRINH_KHONG_DUOC_DUYET.equals(String.valueOf(currentTrinh))) {
                    return;
                }

                var before = buildSnapshot(document);
                fields.put(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET, HoSoCVLifecycleStatus.TRINH_CHUA_TRINH);
                if (request.getLyDo() != null && !request.getLyDo().isBlank()) {
                    fields.put("xulylai_lydo", request.getLyDo());
                }
                document.replaceDynamicFields(toJson(fields));

                var saved = hoSoCVDocumentRepository.save(document);
                documentCacheService.evict(saved.getId());
                documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
                updated.add(saved.getId());
            });
        }

        if (updated.isEmpty()) {
            return Result.failure("Không có hồ sơ nào đủ điều kiện xử lý lại (phải đang ở trạng thái không được duyệt)");
        }
        if (updated.size() < request.getHosocvIds().size()) {
            log.warn("Xử lý lại hồ sơ công việc: chỉ {}/{} id đủ điều kiện", updated.size(), request.getHosocvIds().size());
        }
        documentCacheService.evictAllListCaches();
        return Result.success(updated);
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
