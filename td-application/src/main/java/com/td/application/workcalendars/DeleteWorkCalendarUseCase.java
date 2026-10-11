package com.td.application.workcalendars;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Port lichct (08/09/2026) — soft delete theo {@code id}, cùng khuôn {@code DeleteCategoryUseCase}
 * (đúng quy tắc 04 mục 3: ghi đúng người xoá thật, KHÔNG dùng {@code UUID.randomUUID()}).
 *
 * <p>Quyền: cùng mức độ CHƯA đủ 4 điều kiện {@code canEditSchedule} như
 * {@link SaveWorkCalendarUseCase} (xem javadoc lớp đó) — chỉ chặn cứng khi bản ghi có
 * {@code userCreator} khác username hiện tại.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteWorkCalendarUseCase {

    private static final DateTimeFormatter WEEK_START_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WorkCalendarRepository workCalendarRepository;
    private final WorkCalendarCacheService workCalendarCacheService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final WorkCalendarPermissionService permissionService;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = workCalendarRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Khong tim thay lich cong tac voi ID: " + id);
            }
            var calendar = opt.get();

            if (!permissionService.canEdit(calendar.getScope(), calendar.getIdentifier(), calendar.getUserCreator())) {
                return Result.failure("Ban khong co quyen xoa lich cong tac nay");
            }

            calendar.markAsDeleted(resolveCurrentUserId());
            var saved = workCalendarRepository.save(calendar);

            String subject = "canhan".equals(saved.getScope()) ? saved.getUserCreator() : saved.getIdentifier();
            workCalendarCacheService.evict(saved.getScope(), saved.getWeekStart().format(WEEK_START_FORMAT), subject);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xoa lich cong tac that bai", ex);
            return Result.failure("Xoa lich cong tac that bai: " + ex.getMessage());
        }
    }

    // Ghi dung nguoi xoa that (quy tac 04 muc 3) - null (khong phai UUID ngau nhien) khi khong xac
    // dinh duoc user hien tai.
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
                .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
                .map(AppUser::getId)
                .orElse(null);
    }
}
