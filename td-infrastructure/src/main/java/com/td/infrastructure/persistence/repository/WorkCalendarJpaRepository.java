package com.td.infrastructure.persistence.repository;

import com.td.domain.workcalendars.WorkCalendar;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port lichct (08/09/2026) — bảng vật lý ĐỘC LẬP {@code work_calendars}, KHÔNG phải
 * {@code DocumentJpaRepository} (bảng {@code documents} dùng chung).
 */
public interface WorkCalendarJpaRepository extends BaseRepository<WorkCalendar> {

    // Lưu ý (xem docs/HUONG_DAN_NHAN_BAN_VA_SUA_API.md, mục "Bước 5" và
    // DocumentRegisterJpaRepository): derived query so sánh "id" trên repository generic
    // (BaseRepository<T> kế thừa JpaRepository<T, UUID>) khiến Spring Data suy luận sai kiểu của
    // "id" (ra java.lang.Object thay vì UUID) và nổ lỗi "Cannot compare left expression of type
    // 'java.lang.Object' with right expression of type 'java.util.UUID'" lúc khởi động ứng dụng —
    // PHẢI viết JPQL tường minh bằng @Query.
    @Query("SELECT w FROM WorkCalendar w WHERE w.id = :id AND w.deletedOn IS NULL")
    Optional<WorkCalendar> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    Optional<WorkCalendar> findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
            String scope, LocalDate weekStart, String identifier);

    Optional<WorkCalendar> findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(
            String scope, LocalDate weekStart, String userCreator);
}
