package com.td.application.documentregister;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

// BL-116: "Sao chép sổ" (nút Sao chép sổ ở ItemsList.jsx gốc) — nhân bản 1/nhiều sổ sang năm mới,
// reset codeStartNumber về 1 và xoá codeStartNumbers (danh sách số đã nhập của năm cũ không còn ý
// nghĩa ở năm mới). Nếu sổ gốc đang là mặc định (isDefault), sổ gốc sẽ bị bỏ mặc định sau khi tạo
// bản sao (bản sao kế thừa isDefault=true) — đúng hành vi bản Domino gốc (handleCopySave).
@Data
public class CopyDocumentRegistersRequest {

    @NotEmpty(message = "Danh sách ID sổ cần sao chép không được để trống")
    private List<UUID> ids;

    @NotNull(message = "Năm của sổ mới không được để trống")
    private Integer targetYear;
}
