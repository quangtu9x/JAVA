package com.td.application.phieutrinhtd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Request cho {@code POST /api/v1/phieutrinhtd/LuuPhieuTrinh}, tương đương {@code qlvb/add?id=...&databasetype=phieutrinhtd}. */
@Data
public class LuuPhieuTrinhTdRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private String title;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
