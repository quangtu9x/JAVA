package com.td.application.documentregister;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpdateDocumentRegisterUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<UUID> execute(UUID id, UpdateDocumentRegisterRequest request) {
        try {
            var opt = documentRegisterRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy sổ văn bản với ID: " + id);
            }
            var entity = opt.get();

            String name = (request.getName() != null)
                ? TextNormalizer.normalizeAndSanitize(request.getName()) : entity.getName();
            String documentRegisterType = (request.getDocumentRegisterType() != null)
                ? TextNormalizer.normalize(request.getDocumentRegisterType()) : entity.getDocumentRegisterType();
            String identifier = (request.getIdentifier() != null) ? request.getIdentifier() : entity.getIdentifier();
            boolean isDefault = (request.getIsDefault() != null) ? request.getIsDefault() : entity.isDefault();

            if (documentRegisterRepository
                    .existsByIdentifierAndDocumentRegisterTypeAndNameAndIdNotAndDeletedOnIsNull(
                        identifier, documentRegisterType, name, id)) {
                return Result.failure("Đã tồn tại sổ văn bản '" + name + "' cho loại sổ này ở đơn vị của bạn");
            }

            if (isDefault && documentRegisterRepository
                    .existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndIdNotAndDeletedOnIsNull(
                        identifier, documentRegisterType, id)) {
                return Result.failure("Đơn vị của bạn đã có sổ mặc định cho loại sổ này");
            }

            entity.update(
                name, documentRegisterType, request.getYear(), identifier,
                request.getAgency(), request.getAgencyId(),
                TextNormalizer.normalizeAndSanitize(request.getDocumentType()),
                request.getCodeNotation(),
                request.getUnitPublish(), request.getUnitPublishIdentifier(), request.getUnitPublishId(),
                TextNormalizer.normalizeAndSanitize(request.getAssigner()),
                request.getCodeStartNumber(), request.getAutoIncrement(), request.getIsDefault(),
                request.getCodeStartNumbers(), request.getUserAssignerId(),
                request.getUserAssignerUsername(),
                TextNormalizer.normalizeAndSanitize(request.getAssignerPosition())
            );

            var saved = documentRegisterRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật sổ văn bản thất bại", ex);
            return Result.failure("Cập nhật sổ văn bản thất bại: " + ex.getMessage());
        }
    }
}
