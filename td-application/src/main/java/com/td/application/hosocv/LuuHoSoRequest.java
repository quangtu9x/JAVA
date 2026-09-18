package com.td.application.hosocv;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G1 ({@code docs/analysis/08-...md} mục 8.1
 * {@code LuuHoSoUseCase}): request cho {@code POST /api/v1/hosocv/LuuHoSo}, tương đương
 * {@code qlvb/add?id=...&databasetype=hosocv} (sửa hồ sơ ĐÃ TỒN TẠI) — đúng khuôn
 * {@code LuuDuThaoRequest}.
 */
@Data
public class LuuHoSoRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private String title;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
