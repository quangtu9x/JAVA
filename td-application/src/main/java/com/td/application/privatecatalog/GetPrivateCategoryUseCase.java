package com.td.application.privatecatalog;

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
public class GetPrivateCategoryUseCase {

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<PrivateCategoryDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID danh mục không được để trống");
            }

            var opt = privateCategoryRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục với ID: " + id);
            }

            var entity = opt.get();
            var dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson());
            return Result.success(PrivateCategoryDtoMapper.map(entity, dynamicFields));
        } catch (Exception ex) {
            log.error("Lấy danh mục riêng thất bại", ex);
            return Result.failure("Lấy danh mục riêng thất bại: " + ex.getMessage());
        }
    }
}
