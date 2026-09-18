package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Request cho {@code POST /api/v1/hosoluutru/LuuHoSo}, tương đương {@code qlvb/add?id=...&databasetype=hosoluutru}. */
@Data
public class LuuHoSoLuuTruRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private String title;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
