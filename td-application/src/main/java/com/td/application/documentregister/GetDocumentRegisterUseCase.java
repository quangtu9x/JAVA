package com.td.application.documentregister;

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
public class GetDocumentRegisterUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;

    public Result<DocumentRegisterDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID sổ văn bản không được để trống");
            }
            var opt = documentRegisterRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy sổ văn bản với ID: " + id);
            }
            return Result.success(DocumentRegisterDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            log.error("Lấy sổ văn bản thất bại", ex);
            return Result.failure("Lấy sổ văn bản thất bại: " + ex.getMessage());
        }
    }
}
