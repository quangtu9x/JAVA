package com.td.application.documentregister;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// BL-116: thay cơ chế Domino gốc (đổi field "form" giữa "document_register"/
// "document_register_is_locked" bằng requestPOST_FORM cho từng id, không transaction) — giờ là 1
// cột boolean thật (is_locked) + 1 transaction cho cả batch.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LockDocumentRegistersUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<List<UUID>> execute(List<UUID> ids) {
        try {
            var entities = documentRegisterRepository.findAllByIdInAndDeletedOnIsNull(ids);
            entities.forEach(e -> e.setLocked(true));
            var saved = entities.stream().map(documentRegisterRepository::save).map(e -> e.getId()).toList();
            return Result.success(saved);
        } catch (Exception ex) {
            log.error("Khoá sổ văn bản thất bại", ex);
            return Result.failure("Khoá sổ văn bản thất bại: " + ex.getMessage());
        }
    }
}
