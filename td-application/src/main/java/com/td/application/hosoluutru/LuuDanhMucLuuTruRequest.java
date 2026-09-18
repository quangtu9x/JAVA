package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Request cho {@link LuuDanhMucLuuTruUseCase} — 3 danh mục dùng chung
 * ({@code kyhieuhoso}/{@code chatluong}/{@code loaihinhtailieu}), cùng 1 UI Domino (khác nhau bởi
 * {@code nameform} → {@code documentType}).
 */
@Data
public class LuuDanhMucLuuTruRequest {

    /** {@code null} = tạo mới, có giá trị = sửa bản ghi đã tồn tại. */
    private UUID id;

    @NotBlank(message = "documentType (nameform) khong duoc de trong")
    private String documentType;

    private Map<String, String> fields = new LinkedHashMap<>();
}
