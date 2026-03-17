package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateDocumentUseCase {

    private final DocumentRepository documentRepository;

    public Result<UUID> execute(UpdateDocumentRequest request) {
        try {
            var documentOptional = documentRepository.findById(request.getId());
            if (documentOptional.isEmpty()) {
                return Result.failure("Không tìm thấy tài liệu với ID: " + request.getId());
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                return Result.failure("Tài liệu đã bị xóa và không thể cập nhật");
            }

            document.update(
                request.getTitle().trim(),
                normalize(request.getDocumentType()),
                normalize(request.getStatus()),
                request.getContent(),
                DocumentJsonMapper.toJsonArray(request.getTags()),
                DocumentJsonMapper.toJsonObject(request.getAttributes()),
                DocumentJsonMapper.toJsonObject(request.getMetadata())
            );

            var saved = documentRepository.save(document);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Cập nhật tài liệu thất bại: " + ex.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
