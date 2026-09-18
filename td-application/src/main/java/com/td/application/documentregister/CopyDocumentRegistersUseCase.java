package com.td.application.documentregister;

import com.td.application.common.models.Result;
import com.td.domain.documentregister.DocumentRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-116: "Sao chép sổ" — xem CopyDocumentRegistersRequest. Xử lý từng sổ độc lập (lỗi 1 sổ không
// làm hỏng các sổ còn lại trong cùng lượt sao chép — đúng hành vi vòng lặp per-item của bản gốc
// handleCopySave, dùng toast riêng cho từng sổ thay vì rollback cả loạt).
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CopyDocumentRegistersUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<CopyDocumentRegistersResponse> execute(CopyDocumentRegistersRequest request) {
        var response = new CopyDocumentRegistersResponse();
        for (UUID sourceId : request.getIds()) {
            try {
                copyOne(sourceId, request.getTargetYear(), response);
            } catch (Exception ex) {
                log.error("Sao chép sổ văn bản {} thất bại", sourceId, ex);
                response.getErrors().add(sourceId + ": " + ex.getMessage());
            }
        }
        return Result.success(response);
    }

    private void copyOne(UUID sourceId, int targetYear, CopyDocumentRegistersResponse response) {
        var opt = documentRegisterRepository.findByIdAndDeletedOnIsNull(sourceId);
        if (opt.isEmpty()) {
            response.getErrors().add(sourceId + ": không tìm thấy sổ gốc");
            return;
        }
        var source = opt.get();
        String newName = source.getName() + " " + targetYear;

        if (documentRegisterRepository.existsByIdentifierAndDocumentRegisterTypeAndNameAndDeletedOnIsNull(
                source.getIdentifier(), source.getDocumentRegisterType(), newName)) {
            response.getErrors().add(source.getName() + ": sổ '" + newName + "' đã tồn tại");
            return;
        }

        boolean sourceWasDefault = source.isDefault();

        // Bỏ mặc định ở sổ gốc TRƯỚC khi tạo bản sao mặc định mới — tránh vi phạm ràng buộc "1 sổ
        // mặc định/đơn vị/loại sổ" (unique index V1.0.21) tại thời điểm insert bản sao.
        if (sourceWasDefault) {
            source.setDefault(false);
            documentRegisterRepository.save(source);
        }

        var copy = new DocumentRegister(
            newName, source.getDocumentRegisterType(), targetYear, source.getIdentifier(),
            source.getAgency(), source.getAgencyId(), source.getDocumentType(), source.getCodeNotation(),
            source.getUnitPublish(), source.getUnitPublishIdentifier(), source.getUnitPublishId(),
            source.getAssigner(), 1, source.isAutoIncrement(), sourceWasDefault, "",
            source.getUserAssignerId(), source.getUserAssignerUsername(), source.getAssignerPosition()
        );

        documentRegisterRepository.save(copy);
        response.getCreatedIds().add(copy.getId());
    }
}
