package com.td.application.documents;

import com.td.application.common.cqrs.UseCase;
import com.td.application.common.models.PaginationResponse;

/**
 * DT-007 (27/08/2026): danh sách tệp đính kèm của MỘT lần xử lý cụ thể (vd 1 vết
 * {@code draft_process}) trong cùng 1 tài liệu cha — khác {@link ListDocumentFilesUseCase} (TOÀN
 * BỘ tệp của 1 tài liệu, không lọc theo lần xử lý). Tệp vẫn lưu theo {@code document_id} = id tài
 * liệu CHA (khớp mọi module khác dùng chung {@code /api/v1/documents/{id}/files}); cột
 * {@code process_id} (xem {@code FileMetadataEntity}) chỉ dùng để LỌC LẠI khi cần hiển thị theo
 * từng lần xử lý (vd tab "Quá trình xử lý" của module {@code duthao}), không phải khoá storage.
 */
public interface ListDocumentFilesByProcessUseCase extends UseCase<ListFilesByProcessRequest, PaginationResponse<FileDto>> {
}
