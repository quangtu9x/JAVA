package com.td.application.hosoluutru;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 30/10/2026: request {@code POST /api/v1/hosoluutru/TimKiem} — giữ NGUYÊN payload FE cũ gửi
 * {@code qlvb/search?databasetype=hosoluutru}: {@code search} = các ô của form
 * {@code CollapseHoSoLuuTru.jsx} (giá trị chuỗi, mảng [từ ngày, đến ngày] hoặc {value,label}),
 * {@code keys} = khoá phạm vi giống {@code GetView} (identifier đơn vị/tài khoản).
 */
@Data
public class TimKiemHoSoLuuTruRequest {

    private Integer pageNumber;

    private Integer pageSize;

    private List<String> keys;

    private String keyword;

    private Map<String, Object> search = new LinkedHashMap<>();
}
