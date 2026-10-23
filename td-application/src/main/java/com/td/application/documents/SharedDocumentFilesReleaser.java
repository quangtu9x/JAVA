package com.td.application.documents;

import java.util.UUID;

/**
 * Yêu cầu người dùng 22/10/2026 (đính kèm dùng chung giữa các đơn vị nhận): khi 1 văn bản bị xóa VĨNH VIỄN,
 * gỡ các dòng {@code file_metadata} dùng chung ({@code is_shared=TRUE}) của văn bản đó và nhả tham chiếu
 * trong sổ đăng ký tệp dùng chung — object MinIO KHÔNG bị xóa ở đây, job dọn dẹp td-woker chỉ xóa khi
 * không còn đơn vị nào tham chiếu. Xóa mềm (thùng rác) KHÔNG gọi, vì văn bản còn khôi phục được.
 */
public interface SharedDocumentFilesReleaser {

    void releaseSharedFiles(UUID documentId);
}
