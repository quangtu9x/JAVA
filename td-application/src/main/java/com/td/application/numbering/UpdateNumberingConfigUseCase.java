package com.td.application.numbering;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BL-104 — Definition of Done (4): API cấu hình format (cập nhật format/trạng thái active theo
 * documentType).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UpdateNumberingConfigUseCase {

    private final NumberingConfigRepository numberingConfigRepository;

    public Result<NumberingConfigDto> execute(String documentType, UpdateNumberingConfigRequest request) {
        try {
            if (documentType == null || documentType.isBlank()) {
                return Result.failure("documentType không được để trống");
            }

            String normalizedType = documentType.trim().toLowerCase();
            var configOptional = numberingConfigRepository.findByDocumentType(normalizedType);
            if (configOptional.isEmpty()) {
                return Result.failure("Không tìm thấy cấu hình numbering cho documentType: " + documentType);
            }

            var config = configOptional.get().update(request.getFormat(), request.getActive());
            var saved = numberingConfigRepository.save(config);
            return Result.success(NumberingConfigDtoMapper.map(saved));
        } catch (Exception ex) {
            log.error("Cập nhật cấu hình numbering thất bại", ex);
            return Result.failure("Cập nhật cấu hình numbering thất bại: " + ex.getMessage());
        }
    }
}
