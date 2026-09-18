package com.td.application.recordsstorage;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetRecordsStorageCatalogUseCase {

    private final RecordsStorageCatalogRepository recordsStorageCatalogRepository;

    public Result<RecordsStorageCatalogDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID danh mục hồ sơ không được để trống");
            }
            var opt = recordsStorageCatalogRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục hồ sơ với ID: " + id);
            }
            return Result.success(RecordsStorageCatalogDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            log.error("Lấy danh mục hồ sơ thất bại", ex);
            return Result.failure("Lấy danh mục hồ sơ thất bại: " + ex.getMessage());
        }
    }
}
