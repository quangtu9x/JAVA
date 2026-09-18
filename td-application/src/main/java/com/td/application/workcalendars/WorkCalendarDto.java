package com.td.application.workcalendars;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Port lichct (08/09/2026) — DTO nội bộ cho {@link com.td.domain.workcalendars.WorkCalendar}, dùng
 * bởi {@link GetWeekWorkCalendarUseCase} để flatten ra shape Domino ({@code id/thu2/type/identifier/
 * user_creator/ghichu} + toàn bộ key trong {@code weekFields} SPREAD cùng cấp — xem
 * {@code docs/analysis/09-...} mục 7.4 "Ví dụ contract").
 */
@Data
public class WorkCalendarDto {
    private UUID id;
    private String scope;
    private LocalDate weekStart;
    private String identifier;
    private String userCreator;
    private String note;

    // Đã parse từ cột week_fields (JSONB) — KHÔNG trả thẳng chuỗi JSON thô cho FE (đúng quy tắc 04
    // mục 6 cho mọi field động).
    private Map<String, Object> weekFields;

    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
