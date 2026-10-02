package com.td.application.onlyoffice;

import com.td.application.common.models.Result;

import java.util.UUID;

/**
 * Yêu cầu người dùng 25/09/2026 (tiếp "OnlyOffice lưu thành tệp mới"): vết "Đã sửa" ở tab "Quá trình xử
 * lý" của dự thảo phải HIỂN THỊ tệp mới ngay bên dưới (đọc/tải được như 1 lần gắn kèm tệp bình thường).
 * Tab đó lọc tệp theo {@code file_metadata.process_id} (ViewQueryService#collectProcessFiles) — nên gắn
 * {@code process_id} = id dòng quá trình ({@code draft_process_documents}) vừa ghi cho tệp mới.
 *
 * <p>Chỉ áp dụng cho tệp do sửa trực tuyến tạo ra ({@code source_file_id} có giá trị) và dòng quá trình
 * phải thuộc CÙNG dự thảo với tệp — không phải API gắn tệp tuỳ ý. Idempotent: gắn lại đúng dòng cũ vẫn
 * thành công.</p>
 */
public interface LinkOnlyOfficeEditedFileToProcessUseCase {

    /**
     * @param description chuỗi chi tiết (sửa từ tệp nào, thay bằng tệp nào, ở đâu) — lưu vào
     *                    {@code file_metadata.description} của tệp mới, CÙNG cách luồng ký số
     *                    ({@code tdsignJavaFile.js}) đang làm; bỏ trống thì giữ mô tả sẵn có.
     */
    Result<Void> execute(UUID editedFileId, UUID processId, String description);
}
