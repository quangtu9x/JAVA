package com.td.application.workcalendars;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Port lichct (08/09/2026) — region {@code work-calendars:by-week}, key theo
 * {@code scope+weekStart+subject} (mục 7.1/7.6.8 tài liệu 09).
 *
 * <p>Chỉ implement {@link #evict(String, String, String)} — {@link GetWeekWorkCalendarUseCase}
 * KHÔNG đọc cache (FE {@code LichCongTacView.jsx} luôn gửi {@code cache:false} ở
 * {@code GetLichCongTac}, nên ưu tiên đọc DB trực tiếp — xem mục 7.6.8 tài liệu 09). Chỉ
 * {@link SaveWorkCalendarUseCase}/{@link DeleteWorkCalendarUseCase} gọi evict, để KHÔNG bao giờ có
 * cache cũ lẫn với endpoint tương lai (mobile/portal — hiện chưa port) đọc region này.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkCalendarCacheService {

    public static final String WORK_CALENDAR_BY_WEEK_CACHE = "work-calendars:by-week";

    private final CacheManager cacheManager;

    public void evict(String scope, String weekStart, String subject) {
        Cache cache = cacheManager.getCache(WORK_CALENDAR_BY_WEEK_CACHE);
        if (cache == null) {
            return;
        }
        try {
            cache.evict(buildKey(scope, weekStart, subject));
        } catch (Exception ex) {
            log.debug("WorkCalendar cache evict failed: {}", ex.getMessage());
        }
    }

    private String buildKey(String scope, String weekStart, String subject) {
        return (scope == null ? "" : scope) + ":" + (weekStart == null ? "" : weekStart)
                + ":" + (subject == null ? "" : subject);
    }
}
