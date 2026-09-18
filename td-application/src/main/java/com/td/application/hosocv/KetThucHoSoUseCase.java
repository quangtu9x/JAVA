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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3 ({@code docs/analysis/08-...md} mục 2.2): kết thúc hồ sơ công
 * việc — {@code status: "0" → "1"} + {@code end_date}/{@code summary}. Nhận danh sách id, khớp FE
 * {@code ketthuchoso}/{@code ketthucnhieuhoso} (cùng 1 hành vi, khác số lượng gọi 1 lần).
 *
 * <p>Chỉ áp dụng cho hồ sơ đang {@code status="0"} (đang xử lý) — hồ sơ đã kết thúc (idempotent,
 * tính là "đã xử lý") hoặc không tồn tại đều bị bỏ qua, đúng quy tắc thao tác hàng loạt
 * ({@code docs/architecture/04-...md} mục 4): chỉ {@link Result#failure} khi KHÔNG có id nào áp
 * dụng được.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KetThucHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<List<UUID>> execute(KetThucHoSoRequest request) {
        if (request == null || request.getHosocvIds() == null || request.getHosocvIds().isEmpty()) {
            return Result.failure("Danh sách hồ sơ không được để trống");
        }
        List<UUID> updated = new ArrayList<>();

        for (UUID id : request.getHosocvIds()) {
            hoSoCVDocumentRepository.findById(id).ifPresent(document -> {
                if (document.isDeleted() || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())
                        || !HoSoCVStatus.DANG_XU_LY.equals(document.getStatus())) {
                    return;
                }
                Map<String, Object> fields = new LinkedHashMap<>(
                    dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
                var before = buildSnapshot(document);

                fields.put(HoSoCVLifecycleStatus.FIELD_END_DATE, DATE_FORMATTER.format(LocalDate.now()));
                if (request.getSummary() != null && !request.getSummary().isBlank()) {
                    fields.put(HoSoCVLifecycleStatus.FIELD_SUMMARY, request.getSummary());
                }
                document.replaceDynamicFields(toJson(fields));
                document.update(null, null, HoSoCVStatus.DA_KET_THUC, null, null, null, null);

                var saved = hoSoCVDocumentRepository.save(document);
                documentCacheService.evict(saved.getId());
                documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
                updated.add(saved.getId());
            });
        }

        if (updated.isEmpty()) {
            return Result.failure("Không có hồ sơ nào đủ điều kiện kết thúc (phải đang ở trạng thái đang xử lý)");
        }
        if (updated.size() < request.getHosocvIds().size()) {
            log.warn("Kết thúc hồ sơ công việc: chỉ {}/{} id đủ điều kiện", updated.size(), request.getHosocvIds().size());
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
