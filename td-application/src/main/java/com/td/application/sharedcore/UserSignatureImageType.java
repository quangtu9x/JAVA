package com.td.application.sharedcore;

// Port 2 field ảnh chữ ký cá nhân của Domino (TDDatabaseConnection.java: obj_user.put("anhkyso",...)
// / obj_user.put("anhkynhay",...), xem docs/field-mapping/03-user-field-mapping.md dòng 61-62):
// OFFICIAL = "anhkyso" (ảnh chữ ký số/chữ ký chính, dùng khi phát hành văn bản chính thức), DRAFT =
// "anhkynhay" (ảnh chữ ký nháy/tắt, dùng khi ký nháy/xem trước). Domino gộp chung field text trên
// user doc; Java tách 2 documentId RIÊNG (xem UploadUserSignatureImageUseCase#resolveDocumentId) để
// tái dùng nguyên vẹn cơ chế is_primary có sẵn của file_metadata (UploadFileUseCaseImpl) mà không
// đụng tới bảng nào — 1 documentId luôn ứng với ĐÚNG 1 loại ảnh của ĐÚNG 1 người dùng.
public enum UserSignatureImageType {
    OFFICIAL("anhkyso"),
    DRAFT("anhkynhay");

    private final String legacyFieldName;

    UserSignatureImageType(String legacyFieldName) {
        this.legacyFieldName = legacyFieldName;
    }

    public String getLegacyFieldName() {
        return legacyFieldName;
    }
}
