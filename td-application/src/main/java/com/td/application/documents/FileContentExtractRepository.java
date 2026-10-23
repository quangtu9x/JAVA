package com.td.application.documents;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 17/10/2026 — đọc kết quả trích nội dung tệp ({@code file_content_extracts}, td-woker ghi) và yêu cầu trích lại
 * (docs/architecture/14-trich-noi-dung-tep.md).
 */
public interface FileContentExtractRepository {

    /** Bản ghi nội dung đã trích của 1 tệp. */
    record FileContentExtract(UUID fileId, UUID documentId, String status, String content, int contentChars,
                              boolean truncated, String contentType, String error,
                              OffsetDateTime extractedAt) {
    }

    Optional<FileContentExtract> findByFileId(UUID fileId);

    /** Tệp đang nằm trong hàng đợi trích (chờ worker). */
    boolean isQueued(UUID fileId);

    /** Đưa tệp vào hàng đợi trích lại (worker xử lý ngay nhờ NOTIFY). */
    void requestReextract(UUID fileId);
}
