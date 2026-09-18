package com.td.application.sharedcore;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

// BL-108: dùng chung cho các thao tác hàng loạt trên Users (khoá/mở khoá).
@Data
public class BulkUserIdsRequest {

    @NotEmpty(message = "Danh sách người dùng không được để trống")
    private List<UUID> userIds;
}
