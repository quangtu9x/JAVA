package com.td.application.categories;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<CategoryDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID danh mục không được để trống");
            }

            // Đọc từ cache trước
            var cached = categoryCacheService.get(id);
            if (cached != null) {
                return Result.success(cached);
            }

            var opt = categoryRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục với ID: " + id);
            }

            var entity = opt.get();
            var dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson());
            var dto = CategoryDtoMapper.map(entity, dynamicFields);
            categoryCacheService.put(id, dto);
            return Result.success(dto);
        } catch (Exception ex) {
            log.error("Lấy danh mục thất bại", ex);
            return Result.failure("Lấy danh mục thất bại: " + ex.getMessage());
        }
    }
}
