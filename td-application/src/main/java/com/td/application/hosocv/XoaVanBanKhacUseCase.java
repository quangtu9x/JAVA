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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 02/10/2026 (cutover tab "VĂN BẢN LIÊN QUAN" Hồ sơ công việc): xoá "văn bản khác" khỏi hồ sơ, thay
 * {@code qlvb/delete} (Domino, {@code deleteoption=trash}). Xoá MỀM (quy ước chung của dự án), chỉ
 * nhận id đúng loại {@link LuuVanBanKhacUseCase#DOCUMENT_TYPE} — không xoá nhầm được hồ sơ công việc
 * qua endpoint này.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XoaVanBanKhacUseCase {

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> execute(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.failure("Danh sách văn bản không được để trống");
        }
        UUID deletedBy = currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
        List<UUID> deleted = new ArrayList<>();

        for (UUID id : ids) {
            hoSoCVDocumentRepository.findById(id)
                .filter(document -> LuuVanBanKhacUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()))
                .ifPresent(document -> {
                    if (document.isDeleted()) {
                        deleted.add(document.getId());
                        return;
                    }
                    var before = snapshot(document);
                    document.markAsDeleted(deletedBy);
                    var saved = hoSoCVDocumentRepository.save(document);
                    documentCacheService.evict(saved.getId());
                    documentLogService.logDelete(saved.getId(), before);
                    deleted.add(saved.getId());
                });
        }

        if (deleted.isEmpty()) {
            return Result.failure("Không tìm thấy văn bản khác để xoá");
        }
        documentCacheService.evictAllListCaches();
        return Result.success(deleted);
    }

    private Map<String, Object> snapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
