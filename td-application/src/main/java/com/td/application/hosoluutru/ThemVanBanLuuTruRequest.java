package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Request cho {@link ThemVanBanLuuTruUseCase} — thêm/sửa thủ công 1 văn bản gắn vào hồ sơ lưu trữ (form {@code VanBanModal}). */
@Data
public class ThemVanBanLuuTruRequest {

    /** {@code null} = tạo mới, có giá trị = sửa văn bản đã tồn tại. */
    private UUID id;

    @NotNull(message = "hosoId khong duoc de trong")
    private UUID hosoId;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
