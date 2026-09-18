package com.td.application.hosocv;

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
 * Mở rộng BL-240 (HoSoCV Core) — G6 ({@code docs/analysis/08-...md} mục 5.3): khôi phục hồ sơ công
 * việc từ thùng rác ({@code TDDocument.RestoreDocument()} dòng ~1872) — đối xứng với
 * {@link DeleteHoSoUseCase}, nhận danh sách id, không kiểm tra phần tử con.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestoreHoSoUseCase {

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<List<UUID>> execute(List<UUID> hosocvIds) {
        if (hosocvIds == null || hosocvIds.isEmpty()) {
            return Result.failure("Danh sách hồ sơ không được để trống");
        }
        List<UUID> restored = new ArrayList<>();

        for (UUID id : hosocvIds) {
            hoSoCVDocumentRepository.findById(id).ifPresent(document -> {
                if (!document.isDeleted()) {
                    // Idempotent - chua tung bi xoa, van tinh la "da xu ly".
                    restored.add(document.getId());
                    return;
                }
                var before = buildSnapshot(document);
                document.setDeletedOn(null);
                document.setDeletedBy(null);
                var saved = hoSoCVDocumentRepository.save(document);
                documentCacheService.evict(saved.getId());
                documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
                restored.add(saved.getId());
            });
        }

        if (restored.isEmpty()) {
            return Result.failure("Không tìm thấy hồ sơ nào để khôi phục");
        }
        if (restored.size() < hosocvIds.size()) {
            log.warn("Khôi phục hồ sơ công việc: chỉ {}/{} id tồn tại và được khôi phục", restored.size(), hosocvIds.size());
        }
        documentCacheService.evictAllListCaches();
        return Result.success(restored);
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
