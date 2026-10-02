package com.td.application.documents;

import java.util.Collection;
import java.util.UUID;

/**
 * Yêu cầu người dùng 24/09/2026 ("tạm tệp cũ chưa xoá đi, chỉ hiển thị tệp mới cập nhật ... không cho
 * phép tải về"): đánh dấu các tệp của {@code documentId} là ĐÃ ĐƯỢC THAY THẾ
 * ({@code file_metadata.superseded_at}) — KHÔNG xoá dòng, KHÔNG xoá object lưu trữ. Tệp đã thay thế bị
 * ẩn khỏi MỌI truy vấn tệp (liệt kê, tải, xem, đổi tên, xoá, ký...) — xem
 * {@code FileMetadataEntity} (td-infrastructure, {@code @SQLRestriction}).
 */
public interface SupersedeDocumentFilesUseCase {

    /**
     * @param documentId   tài liệu sở hữu tệp (chỉ tệp đúng {@code document_id} này mới bị đánh dấu)
     * @param fileIds      id các tệp cần đánh dấu; rỗng/null = không làm gì
     * @param supersededBy id bản ghi lịch sử gây ra việc thay thế (truy vết ngược), có thể null
     * @return số tệp thực sự được đánh dấu
     */
    int execute(UUID documentId, Collection<UUID> fileIds, UUID supersededBy);
}
