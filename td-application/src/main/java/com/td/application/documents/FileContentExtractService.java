package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.FileContentExtractRepository.FileContentExtract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 17/10/2026 — "Nội dung trích xuất" của 1 tệp đính kèm (thay "Văn bản đã bóc tách"/OCR Domino, docs/architecture/14).
 * Quyền: CÙNG mức API tải tệp ({@code DocumentsController#downloadFile}) — tệp phải thuộc đúng văn bản
 * ({@link GetFileUseCase}); nội dung trích xuất KHÔNG lộ nhiều hơn chính tệp đó.
 */
@Service
@RequiredArgsConstructor
public class FileContentExtractService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_NONE = "NONE";

    private final GetFileUseCase getFileUseCase;
    private final FileContentExtractRepository repository;

    public Result<Map<String, Object>> get(UUID documentId, UUID fileId) {
        Result<FileDto> file = getFileUseCase.execute(DownloadFileRequest.builder().documentId(documentId).fileId(fileId).build());
        if (!file.isSuccess() || file.getData() == null) {
            return Result.failure(file.getError() != null ? file.getError() : "File không tồn tại");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", fileId);
        body.put("fileName", file.getData().getOriginalFileName());
        var extract = repository.findByFileId(fileId);
        boolean queued = repository.isQueued(fileId);
        if (extract.isEmpty()) {
            body.put("status", queued ? STATUS_PENDING : STATUS_NONE);
            return Result.success(body);
        }
        FileContentExtract e = extract.get();
        body.put("status", queued ? STATUS_PENDING : e.status());
        body.put("content", e.content());
        body.put("contentChars", e.contentChars());
        body.put("truncated", e.truncated());
        body.put("contentType", e.contentType());
        body.put("error", e.error());
        body.put("extractedAt", e.extractedAt());
        return Result.success(body);
    }

    public Result<Map<String, Object>> requestReextract(UUID documentId, UUID fileId) {
        Result<FileDto> file = getFileUseCase.execute(DownloadFileRequest.builder().documentId(documentId).fileId(fileId).build());
        if (!file.isSuccess() || file.getData() == null) {
            return Result.failure(file.getError() != null ? file.getError() : "File không tồn tại");
        }
        if (!repository.isQueued(fileId)) {
            repository.requestReextract(fileId);
        }
        return Result.success(Map.of("fileId", fileId, "status", STATUS_PENDING));
    }
}
