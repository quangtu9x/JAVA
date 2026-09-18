package com.td.application.onlyoffice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.UUID;

/**
 * Ghi đè nội dung tệp {@code documentId}/{@code fileId} bằng bản OnlyOffice vừa lưu (status 2/6 ở
 * callback — xem {@code SaveOnlyOfficeCallbackUseCase}). Ghi ĐÈ đúng vị trí lưu trữ MinIO hiện có
 * (không tạo dòng {@code file_metadata} mới) để giữ nguyên {@code fileId} — mọi màn hình/đường dẫn
 * tải xuống đang tham chiếu tệp này (danh sách tệp, ký số, lịch sử...) không cần đổi gì.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveOnlyOfficeCallbackRequest {

    private UUID documentId;
    private UUID fileId;
    private InputStream content;
    private long contentLength;
}
