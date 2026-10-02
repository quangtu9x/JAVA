package com.td.application.onlyoffice;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Yêu cầu người dùng 25/09/2026 (OnlyOffice lưu bản sửa thành TỆP MỚI — xem
 * {@link SaveOnlyOfficeCallbackRequest}): tệp mới mà 1 phiên soạn thảo ({@code editSessionKey} =
 * {@code document.key} trong editor-config) đã lưu ra từ tệp gốc {@code sourceFileId}. FE gọi sau khi
 * đóng trình soạn thảo để (1) biết Document Server đã lưu xong chưa, (2) biết tên/id tệp mới để cập nhật
 * danh sách phân loại + ghi vết quá trình xử lý (giống luồng "Sửa tệp" BTE).
 *
 * @return rỗng nếu phiên chưa lưu ra tệp nào (chưa lưu xong, hoặc không có thay đổi); có giá trị:
 *     {@code file_id}, {@code name}, {@code document_id}, {@code size}.
 */
public interface GetOnlyOfficeEditedFileUseCase {

    Optional<Map<String, Object>> execute(UUID sourceFileId, String editSessionKey);
}
