package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class HardDeleteDocumentUseCase {

    private final DocumentRepository documentRepository;

    public Result<UUID> execute(UUID documentId) {
        try {
            var documentOptional = documentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                return Result.failure("Không tìm thấy tài liệu với ID: " + documentId);
            }

            documentRepository.hardDelete(documentOptional.get());
            return Result.success(documentId);
        } catch (Exception ex) {
            return Result.failure("Xóa vĩnh viễn tài liệu thất bại: " + ex.getMessage());
        }
    }
}
