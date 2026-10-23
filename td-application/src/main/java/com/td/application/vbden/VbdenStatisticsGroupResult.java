package com.td.application.vbden;

import java.util.List;
import java.util.Map;

/** Kết quả tương thích TDMessage mà GroupTableVbden đang sử dụng. */
public record VbdenStatisticsGroupResult(
    List<Map<String, Object>> data,
    int total,
    int totalGroup,
    String type
) {
}
