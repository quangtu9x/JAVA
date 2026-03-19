package com.td.application.categories;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;

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

            var dto = CategoryDtoMapper.map(opt.get());
            categoryCacheService.put(id, dto);
            return Result.success(dto);
        } catch (Exception ex) {
            return Result.failure("Lấy danh mục thất bại: " + ex.getMessage());
        }
    }
}
