package com.td.application.recordsstorage;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.recordsstorage.RecordsStorageCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreateRecordsStorageCatalogUseCase {

    private final RecordsStorageCatalogRepository recordsStorageCatalogRepository;

    public Result<UUID> execute(CreateRecordsStorageCatalogRequest request) {
        try {
            var entity = new RecordsStorageCatalog(
                TextNormalizer.normalizeAndSanitize(request.getCategory()),
                request.getCode(),
                request.getPhong(), request.getPhongId(),
                request.getFirstTopic(), request.getFirstTopicId(),
                request.getSecondTopic(), request.getSecondTopicId(),
                request.getStoragePeriod(), request.getYearInception(),
                request.getSortOrder(),
                TextNormalizer.normalizeAndSanitize(request.getNote()),
                request.getNumberOfDocumentNumber(),
                request.getKyhieu(),
                request.getInternalUnitObj(),
                request.getIndividualObj(),
                request.getIdentifier()
            );
            var saved = recordsStorageCatalogRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo danh mục hồ sơ thất bại", ex);
            return Result.failure("Tạo danh mục hồ sơ thất bại: " + ex.getMessage());
        }
    }
}
