package com.td.application.documents;

import com.td.application.common.models.Result;

/**
 * 27/08/2026 (port "Chuyển văn thư" — xem javadoc {@link CopyDocumentFilesRequest}): port DUY NHẤT
 * của {@link CopyDocumentFilesRequest}. Trả về SỐ TỆP đã copy (0 nếu tài liệu nguồn không có tệp
 * nào — không phải lỗi).
 */
public interface CopyDocumentFilesUseCase {
    Result<Integer> execute(CopyDocumentFilesRequest request);
}
