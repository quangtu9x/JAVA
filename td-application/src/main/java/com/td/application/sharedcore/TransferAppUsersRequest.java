package com.td.application.sharedcore;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

// BL-108: chuyển đơn vị hàng loạt.
@Data
public class TransferAppUsersRequest {

    @NotEmpty(message = "Danh sách người dùng không được để trống")
    private List<UUID> userIds;

    @NotNull(message = "Tổ chức đích không được để trống")
    private UUID organizationId;
}
