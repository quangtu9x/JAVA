package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-230 (DuThao Core) — Slice 1 ("create/save core + get detail", tài liệu phân tích 04 mục 13) —
 * request cho {@code POST /api/v1/duthao/Luu}, tương thích 1 PHẦN {@code qlvb/add?
 * databasetype=duthao&id=...} (nhánh hiệu chỉnh dự thảo ĐÃ TỒN TẠI, dùng bởi
 * {@code ChiTietPage.jsx#onFinish} khi {@code id} có giá trị).
 *
 * <p>Đơn giản hơn {@code LuuVanBanDenRequest} — {@code duthao} không có kiến trúc "2-document" (sổ
 * văn bản) như {@code vbden}, nên không có {@code idso}/{@code documentRegisterId}.</p>
 */
@Data
public class LuuDuThaoRequest {

    /** Id dự thảo cần cập nhật (bắt buộc). */
    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
