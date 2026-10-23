package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.FileContentExtractRepository.FileContentExtract;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 17/10/2026 — "Nội dung trích xuất" (docs/architecture/14): quyền cùng mức tải tệp. */
class FileContentExtractServiceTest {

    private final UUID documentId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final List<UUID> reextracted = new ArrayList<>();
    private Optional<FileContentExtract> stored = Optional.empty();
    private boolean queued;

    private FileContentExtractService service(boolean fileBelongsToDocument) {
        GetFileUseCase getFile = request -> {
            if (!fileBelongsToDocument || !request.getFileId().equals(fileId)) {
                return Result.failure("File không tồn tại");
            }
            FileDto dto = new FileDto();
            dto.setOriginalFileName("hopdong.pdf");
            return Result.success(dto);
        };
        FileContentExtractRepository repository = new FileContentExtractRepository() {
            @Override
            public Optional<FileContentExtract> findByFileId(UUID id) {
                return stored;
            }

            @Override
            public boolean isQueued(UUID id) {
                return queued;
            }

            @Override
            public void requestReextract(UUID id) {
                reextracted.add(id);
            }
        };
        return new FileContentExtractService(getFile, repository);
    }

    @Test
    void get_shouldReturnExtractedContent() {
        stored = Optional.of(new FileContentExtract(fileId, documentId, "DONE", "Hợp đồng mua sắm", 16, false,
            "application/pdf", null, OffsetDateTime.now()));

        var result = service(true).get(documentId, fileId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("status", "DONE").containsEntry("content", "Hợp đồng mua sắm")
            .containsEntry("fileName", "hopdong.pdf");
    }

    @Test
    void get_whenQueuedOrNeverExtracted_shouldReportPendingOrNone() {
        queued = true;
        assertThat(service(true).get(documentId, fileId).getData()).containsEntry("status", "PENDING");
        queued = false;
        assertThat(service(true).get(documentId, fileId).getData()).containsEntry("status", "NONE");
    }

    // Tệp không thuộc văn bản → từ chối (cùng mức API tải tệp), không lộ nội dung.
    @Test
    void get_fileNotOfDocument_shouldFail() {
        stored = Optional.of(new FileContentExtract(fileId, documentId, "DONE", "bí mật", 6, false, null, null, null));

        var result = service(false).get(documentId, fileId);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void requestReextract_shouldQueueOnceAndCheckOwnership() {
        assertThat(service(true).requestReextract(documentId, fileId).isSuccess()).isTrue();
        queued = true;
        service(true).requestReextract(documentId, fileId);
        assertThat(reextracted).containsExactly(fileId);

        assertThat(service(false).requestReextract(documentId, fileId).isSuccess()).isFalse();
    }
}
