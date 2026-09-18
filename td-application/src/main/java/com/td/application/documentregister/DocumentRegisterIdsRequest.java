package com.td.application.documentregister;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

// Body dùng chung cho khoá/mở khoá hàng loạt sổ văn bản.
@Data
public class DocumentRegisterIdsRequest {

    @NotEmpty(message = "Danh sách ID không được để trống")
    private List<UUID> ids;
}
