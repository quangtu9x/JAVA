package com.td.application.workcalendars;

import lombok.Data;

/**
 * Port lichct (08/09/2026) — tương thích body {@code GetLichCongTac} Domino: {@code keys[0]} =
 * {@code weekStart} (thứ 2 tuần, {@code dd/MM/yyyy}), {@code keys[1]} = {@code subject} (identifier
 * hoặc username tuỳ scope), {@code viewname} = {@code scope}. Controller build request này từ
 * {@code ViewQueryRequest} tái dùng (xem {@code LegacyDanhMucController#getLichCongTac}).
 */
@Data
public class GetWeekWorkCalendarRequest {
    private String weekStart;
    private String scope;
    private String subject;
}
