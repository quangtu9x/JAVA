package com.td.infrastructure.onlyoffice;

import com.td.application.onlyoffice.GetOnlyOfficeEditedFileUseCase;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Xem javadoc {@link GetOnlyOfficeEditedFileUseCase}. Tra theo (tệp gốc, key phiên) — document_id lấy từ
 * chính tệp gốc (cùng lý do BuildOnlyOfficeEditorConfigUseCaseImpl không tin documentId FE gửi lên: ở tab
 * "Quá trình xử lý" resdata là 1 dòng lịch sử, không phải chính dự thảo).
 */
@Service
@RequiredArgsConstructor
public class GetOnlyOfficeEditedFileUseCaseImpl implements GetOnlyOfficeEditedFileUseCase {

    private final FileMetadataJpaRepository fileMetadataRepository;

    @Override
    public Optional<Map<String, Object>> execute(UUID sourceFileId, String editSessionKey) {
        if (sourceFileId == null || editSessionKey == null || editSessionKey.isBlank()) {
            return Optional.empty();
        }
        Optional<FileMetadataEntity> source = fileMetadataRepository.findById(sourceFileId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return fileMetadataRepository
                .findFirstByDocumentIdAndSourceFileIdAndEditSessionKey(
                        source.get().getDocumentId(), sourceFileId, editSessionKey.trim())
                .filter(f -> f.getFileSize() != null && f.getFileSize() > 0)
                .map(f -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("file_id", f.getId());
                    dto.put("name", f.getOriginalFilename());
                    dto.put("document_id", f.getDocumentId());
                    dto.put("size", f.getFileSize());
                    return dto;
                });
    }
}
