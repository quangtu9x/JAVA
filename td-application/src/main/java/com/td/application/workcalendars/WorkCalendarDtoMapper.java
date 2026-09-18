package com.td.application.workcalendars;

import com.td.domain.workcalendars.WorkCalendar;

import java.util.Map;

/**
 * Port lichct (08/09/2026) — cùng convention {@code CategoryDtoMapper}: mapper thuần static/không
 * cần Spring DI, {@code weekFields} do caller (use case, có {@code DynamicDocumentFieldsService})
 * tự parse rồi truyền vào.
 */
final class WorkCalendarDtoMapper {

    private WorkCalendarDtoMapper() {
    }

    static WorkCalendarDto map(WorkCalendar entity, Map<String, Object> weekFields) {
        var dto = new WorkCalendarDto();
        dto.setId(entity.getId());
        dto.setScope(entity.getScope());
        dto.setWeekStart(entity.getWeekStart());
        dto.setIdentifier(entity.getIdentifier());
        dto.setUserCreator(entity.getUserCreator());
        dto.setNote(entity.getNote());
        dto.setWeekFields(weekFields == null ? Map.of() : weekFields);
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        return dto;
    }
}
