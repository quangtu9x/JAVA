package com.td.application.workcalendars;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.workcalendars.WorkCalendar;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * Port lichct (08/09/2026) — bảng vật lý ĐỘC LẬP {@code work_calendars} (xem javadoc
 * {@link WorkCalendar}). Chỉ khai báo ĐÚNG method thực sự dùng bởi {@link GetWeekWorkCalendarUseCase}/
 * {@link SaveWorkCalendarUseCase}/{@link DeleteWorkCalendarUseCase} — cùng tinh thần
 * {@code DuThaoDocumentRepository} (không suy diễn thêm method chưa có bằng chứng cần dùng).
 */
public interface WorkCalendarRepository extends IRepository<WorkCalendar> {

    Optional<WorkCalendar> findByIdAndDeletedOnIsNull(UUID id);

    /** Chủ thể = identifier (scope phong/donvi/coquan). */
    Optional<WorkCalendar> findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
            String scope, LocalDate weekStart, String identifier);

    /** Chủ thể = user_creator (scope canhan). */
    Optional<WorkCalendar> findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(
            String scope, LocalDate weekStart, String userCreator);

    List<WorkCalendar> findByScopeAndWeekStartBetweenAndIdentifierAndDeletedOnIsNull(
            String scope, LocalDate from, LocalDate to, String identifier);

    List<WorkCalendar> findByScopeAndWeekStartBetweenAndUserCreatorAndDeletedOnIsNull(
            String scope, LocalDate from, LocalDate to, String userCreator);
}
