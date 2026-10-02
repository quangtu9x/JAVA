package com.td.application.sharedcore;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// documentId "ảo" dùng chung với hạ tầng file_metadata sẵn có (UploadFileUseCase/ListDocumentFilesUseCase
// — xem javadoc UserSignatureImageDto#documentId): SUY RA tất định từ (userId, type), KHÔNG lưu ở đâu
// cả — gọi lại đúng userId+type luôn ra đúng 1 documentId. Nhờ vậy is_primary (vốn chỉ demote theo
// documentId, xem UploadFileUseCaseImpl#demoteExistingPrimary) tự động tách đúng theo TỪNG loại ảnh
// của TỪNG người dùng mà không cần sửa gì ở UploadFileUseCaseImpl/file_metadata.
public final class UserSignatureImageDocumentIdResolver {

    private UserSignatureImageDocumentIdResolver() {
    }

    public static UUID resolve(UUID userId, UserSignatureImageType type) {
        String seed = "user-signature-image:" + userId + ":" + type.name();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
