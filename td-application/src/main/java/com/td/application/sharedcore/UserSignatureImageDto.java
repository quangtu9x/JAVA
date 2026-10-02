package com.td.application.sharedcore;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserSignatureImageDto {
    // documentId = khoá logic file_metadata (xem UploadUserSignatureImageUseCase#resolveDocumentId) —
    // FE dùng lại NGUYÊN endpoint tải/xoá tệp chung đã có (api/v1/documents/{documentId}/files/{fileId}/...),
    // không cần endpoint download/delete riêng cho ảnh chữ ký.
    private UUID documentId;
    private UUID fileId;
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadedAt;
}
