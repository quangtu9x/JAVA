package com.td.infrastructure.onlyoffice;

import com.td.application.common.models.Result;
import com.td.application.duthao.DraftProcessDocumentRepository;
import com.td.application.onlyoffice.LinkOnlyOfficeEditedFileToProcessUseCase;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Xem javadoc {@link LinkOnlyOfficeEditedFileToProcessUseCase}. */
@Service
@RequiredArgsConstructor
public class LinkOnlyOfficeEditedFileToProcessUseCaseImpl implements LinkOnlyOfficeEditedFileToProcessUseCase {

    /** Số dòng quá trình của 1 dự thảo luôn nhỏ — đủ để xác nhận dòng thuộc đúng dự thảo. */
    private static final int MAX_PROCESS_ROWS = 1000;

    private final FileMetadataJpaRepository fileMetadataRepository;
    private final DraftProcessDocumentRepository draftProcessDocumentRepository;

    @Override
    public Result<Void> execute(UUID editedFileId, UUID processId, String description) {
        if (editedFileId == null || processId == null) {
            return Result.failure("Thiếu tệp hoặc dòng quá trình xử lý");
        }
        Optional<FileMetadataEntity> fileOpt = fileMetadataRepository.findById(editedFileId);
        if (fileOpt.isEmpty() || fileOpt.get().getSourceFileId() == null) {
            return Result.failure("Không tìm thấy tệp đã sửa trực tuyến");
        }
        FileMetadataEntity file = fileOpt.get();
        boolean hasDescription = description != null && !description.isBlank();
        if (processId.equals(file.getProcessId())) {
            if (hasDescription && !description.trim().equals(file.getDescription())) {
                file.setDescription(description.trim());
                fileMetadataRepository.save(file);
            }
            return Result.success(null);
        }
        if (file.getProcessId() != null) {
            return Result.failure("Tệp đã được gắn với một lần xử lý khác");
        }
        boolean belongsToSameDraft = file.getDocumentId() != null && draftProcessDocumentRepository
                .findByParentId(file.getDocumentId().toString(), PageRequest.of(0, MAX_PROCESS_ROWS))
                .getContent().stream()
                .anyMatch(process -> processId.equals(process.getId()));
        if (!belongsToSameDraft) {
            return Result.failure("Dòng quá trình xử lý không thuộc dự thảo của tệp");
        }
        file.setProcessId(processId);
        if (hasDescription) {
            file.setDescription(description.trim());
        }
        fileMetadataRepository.save(file);
        return Result.success(null);
    }
}
