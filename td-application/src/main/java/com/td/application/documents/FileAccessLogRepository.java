package com.td.application.documents;

import com.td.domain.documents.FileAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * 09/09/2026 (yêu cầu người dùng "log xem tệp đính kèm... tối ưu lưu trữ"): truy cập bảng
 * {@code file_access_logs} — xem javadoc {@link FileAccessLog} về model UPSERT (KHÔNG append-only).
 */
public interface FileAccessLogRepository {

    /**
     * Ghi nhận 1 lượt xem/tải — UPSERT nguyên tử theo khoá {@code (module, documentId, fileId,
     * username, action)}: chưa có thì tạo mới ({@code firstAccessedOn}={@code lastAccessedOn}=hiện
     * tại, {@code accessCount}=1); đã có thì CHỈ cập nhật {@code lastAccessedOn} (+1
     * {@code accessCount}, làm mới {@code fileName}/{@code fullname}/{@code unit}), giữ nguyên
     * {@code firstAccessedOn} gốc. Trả về id dòng (mới hoặc đã có).
     */
    UUID recordAccess(String module, UUID documentId, UUID fileId, String fileName, String action,
            String username, String fullname, String unit);

    /** Toàn bộ lượt xem/tải theo văn bản, mới nhất trước ({@code last_accessed_on DESC}). */
    Page<FileAccessLog> findByModuleAndDocumentId(String module, UUID documentId, Pageable pageable);
}
