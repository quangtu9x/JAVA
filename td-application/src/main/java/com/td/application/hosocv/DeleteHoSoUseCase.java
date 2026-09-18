package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.sharedcore.AppUser;
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
 * Mở rộng BL-240 (HoSoCV Core) — G6 ({@code docs/analysis/08-...md} mục 5.3, xác nhận
 * {@code docs/analysis/13-...md} mục 2.3): xoá mềm hồ sơ công việc. Đúng hành vi thật
 * ({@code TDDocument.DeleteDocument()} dòng ~1380, comment "cho xoa nhieu, khong check phan tu con")
 * — **KHÁC hẳn vbden/vbdi** (không kiểm tra hồ sơ có văn bản/response con đã gắn hay không) —
 * nhận DANH SÁCH id, xoá được nhiều hồ sơ cùng lúc trong 1 lần gọi.
 *
 * <p>Tuân thủ {@code docs/architecture/04-quy-tac-crud-api-shared-core.md} mục 3/4:
 * {@code deletedBy} lấy từ {@link CurrentUserProvider} + {@link AppUserRepository} (KHÔNG
 * {@code UUID.randomUUID()}); thao tác hàng loạt không được "thành công giả" — chỉ
 * {@link Result#failure} khi KHÔNG có id nào xoá được, còn lại vẫn success kèm {@code log.warn} nếu
 * thiếu 1 phần.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteHoSoUseCase {

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> execute(List<UUID> hosocvIds) {
        if (hosocvIds == null || hosocvIds.isEmpty()) {
            return Result.failure("Danh sách hồ sơ không được để trống");
        }
        UUID deletedBy = resolveCurrentUserId();
        List<UUID> deleted = new ArrayList<>();

        for (UUID id : hosocvIds) {
            hoSoCVDocumentRepository.findById(id).ifPresent(document -> {
                if (document.isDeleted()) {
                    // Idempotent - da xoa truoc do, van tinh la "da xu ly".
                    deleted.add(document.getId());
                    return;
                }
                var before = buildSnapshot(document);
                document.markAsDeleted(deletedBy);
                var saved = hoSoCVDocumentRepository.save(document);
                documentCacheService.evict(saved.getId());
                documentLogService.logDelete(saved.getId(), before);
                deleted.add(saved.getId());
            });
        }

        if (deleted.isEmpty()) {
            return Result.failure("Không tìm thấy hồ sơ nào để xoá");
        }
        if (deleted.size() < hosocvIds.size()) {
            log.warn("Xoá hồ sơ công việc: chỉ {}/{} id tồn tại và được xoá", deleted.size(), hosocvIds.size());
        }
        documentCacheService.evictAllListCaches();
        return Result.success(deleted);
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
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
