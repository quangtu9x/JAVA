package com.td.application.vbcddh;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 30/10/2026 (cutover FE vbcddh): request {@code POST /api/v1/vbcddh/TimKiem} — giữ NGUYÊN payload FE
 * gửi Domino {@code qlvb/search?databasetype=vbcddh} ({@code search} = bộ lọc CollapseVBCDDH: form,
 * keyword, field ngày dạng [từ, đến] dd/MM/yyyy, các field chuỗi...).
 */
@Data
public class TimKiemVbcddhRequest {

    private int pageNumber = 1;

    private int pageSize = 20;

    private String items = "getall";

    private String keyword;

    private List<String> columns = new ArrayList<>();

    private Map<String, Object> search = new LinkedHashMap<>();
}
