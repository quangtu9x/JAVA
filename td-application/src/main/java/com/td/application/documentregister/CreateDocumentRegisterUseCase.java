package com.td.application.documentregister;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.documentregister.DocumentRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-116: tạo sổ văn bản mới. Thay "kiemtrungdocument" (Domino, gọi trước khi lưu, có thể race
// condition giữa check và save) — validate NGAY TRONG transaction này, chặt hơn bản gốc: 1) không
// trùng (identifier, loại sổ, tên sổ); 2) không có sổ mặc định khác cho cùng (identifier, loại sổ)
// nếu isDefault = true. Cả 2 ràng buộc còn có unique index ở DB (V1.0.21) làm lưới an toàn cuối.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreateDocumentRegisterUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<UUID> execute(CreateDocumentRegisterRequest request) {
        try {
            String name                 = TextNormalizer.normalizeAndSanitize(request.getName());
            String documentRegisterType = TextNormalizer.normalize(request.getDocumentRegisterType());
            String identifier           = request.getIdentifier();
            boolean isDefault           = Boolean.TRUE.equals(request.getIsDefault());

            if (documentRegisterType == null || documentRegisterType.isBlank()) {
                return Result.failure("Loại sổ không được để trống");
            }

            if (documentRegisterRepository.existsByIdentifierAndDocumentRegisterTypeAndNameAndDeletedOnIsNull(
                    identifier, documentRegisterType, name)) {
                return Result.failure("Đã tồn tại sổ văn bản '" + name + "' cho loại sổ này ở đơn vị của bạn");
            }

            if (isDefault && documentRegisterRepository
                    .existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
                        identifier, documentRegisterType)) {
                return Result.failure("Đơn vị của bạn đã có sổ mặc định cho loại sổ này");
            }

            var entity = new DocumentRegister(
                name, documentRegisterType, request.getYear(), identifier,
                request.getAgency(), request.getAgencyId(),
                TextNormalizer.normalizeAndSanitize(request.getDocumentType()),
                request.getCodeNotation(),
                request.getUnitPublish(), request.getUnitPublishIdentifier(), request.getUnitPublishId(),
                TextNormalizer.normalizeAndSanitize(request.getAssigner()),
                request.getCodeStartNumber(), request.getAutoIncrement(), isDefault,
                request.getCodeStartNumbers(), request.getUserAssignerId(),
                request.getUserAssignerUsername(),
                TextNormalizer.normalizeAndSanitize(request.getAssignerPosition())
            );

            var saved = documentRegisterRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo sổ văn bản thất bại", ex);
            return Result.failure("Tạo sổ văn bản thất bại: " + ex.getMessage());
        }
    }
}
