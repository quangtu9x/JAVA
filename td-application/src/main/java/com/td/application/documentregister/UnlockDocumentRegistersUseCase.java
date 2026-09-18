package com.td.application.documentregister;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UnlockDocumentRegistersUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<List<UUID>> execute(List<UUID> ids) {
        try {
            var entities = documentRegisterRepository.findAllByIdInAndDeletedOnIsNull(ids);
            entities.forEach(e -> e.setLocked(false));
            var saved = entities.stream().map(documentRegisterRepository::save).map(e -> e.getId()).toList();
            return Result.success(saved);
        } catch (Exception ex) {
            log.error("Mở khoá sổ văn bản thất bại", ex);
            return Result.failure("Mở khoá sổ văn bản thất bại: " + ex.getMessage());
        }
    }
}
