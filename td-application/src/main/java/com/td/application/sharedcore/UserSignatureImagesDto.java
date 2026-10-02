package com.td.application.sharedcore;

import lombok.Builder;
import lombok.Data;

// null trong 2 field dưới nghĩa là người dùng CHƯA gắn ảnh loại đó (không phải lỗi) — khớp hành vi
// Domino cũ (field text rỗng khi user_administrator/user mới tạo, xem TDDatabaseConnection.java).
@Data
@Builder
public class UserSignatureImagesDto {
    private UserSignatureImageDto official;
    private UserSignatureImageDto draft;
}
