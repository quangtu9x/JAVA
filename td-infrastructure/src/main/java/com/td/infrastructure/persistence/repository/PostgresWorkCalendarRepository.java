package com.td.infrastructure.persistence.repository;

import com.td.application.workcalendars.WorkCalendarRepository;
import com.td.domain.workcalendars.WorkCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * Port lichct (08/09/2026) — adapter cho bảng vật lý ĐỘC LẬP {@code work_calendars} (xem javadoc
 * {@link WorkCalendar}). Toàn bộ truy vấn đều là lookup CHÍNH XÁC theo (scope, weekStart,
 * identifier|userCreator) — dùng thẳng Spring Data derived query (không cần native SQL/containment
 * JSONB như {@code PostgresDuThaoDocumentRepository}, vì {@code work_calendars} không có nhu cầu
 * lọc theo field trong {@code week_fields}).
 */
@Repository
@RequiredArgsConstructor
public class PostgresWorkCalendarRepository implements WorkCalendarRepository {

    private final WorkCalendarJpaRepository jpa;

    @Override
    public Optional<WorkCalendar> findById(UUID id) {
        // Ep kieu ro JpaRepository<WorkCalendar, UUID> - BaseRepository ke thua CA CrudRepository
        // (Spring Data) LAN IRepository (port rieng, cung khai bao findById(UUID)) nen goi thang qua
        // bien jpa (kieu WorkCalendarJpaRepository) bi ambiguous - dung tien le
        // PostgresDuThaoDocumentRepository#findById.
        return ((org.springframework.data.jpa.repository.JpaRepository<WorkCalendar, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<WorkCalendar> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends WorkCalendar> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(WorkCalendar entity) {
        jpa.delete(entity);
    }

    @Override
    public Optional<WorkCalendar> findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
            String scope, LocalDate weekStart, String identifier) {
        return jpa.findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(scope, weekStart, identifier);
    }

    @Override
    public Optional<WorkCalendar> findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(
            String scope, LocalDate weekStart, String userCreator) {
        return jpa.findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(scope, weekStart, userCreator);
    }

    @Override
    public List<WorkCalendar> findByScopeAndWeekStartBetweenAndIdentifierAndDeletedOnIsNull(
            String scope, LocalDate from, LocalDate to, String identifier) {
        return jpa.findByScopeAndWeekStartBetweenAndIdentifierAndDeletedOnIsNull(scope, from, to, identifier);
    }

    @Override
    public List<WorkCalendar> findByScopeAndWeekStartBetweenAndUserCreatorAndDeletedOnIsNull(
            String scope, LocalDate from, LocalDate to, String userCreator) {
        return jpa.findByScopeAndWeekStartBetweenAndUserCreatorAndDeletedOnIsNull(scope, from, to, userCreator);
    }
}
