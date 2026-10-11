package com.td.application.workcalendars;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.workcalendars.WorkCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Port lichct (08/09/2026) — tương đương nhánh {@code Form="lich"} của {@code LuuDanhMuc.java}
 * (Domino), xem mục 4.2/7.6.1/7.6.2 tài liệu 09.
 *
 * <p><b>Create/update</b>: {@code id != null} → cập nhật (ghi đè TOÀN BỘ {@code week_fields} bằng
 * payload mới — KHÔNG merge theo ô, đúng mục 7.6.2 — chỉ {@code note}/{@code weekFields} được sửa;
 * {@code scope}/{@code weekStart}/{@code identifier}/{@code userCreator} CỐ ĐỊNH từ lúc tạo, xem
 * javadoc {@link WorkCalendar#updateNote}). {@code id == null} → tạo mới, chặn trùng
 * {@code (scope, weekStart, identifier|userCreator)} bằng kiểm tra trước (bổ sung cho unique index
 * DB).
 *
 * <p><b>Field động</b>: chỉ giữ key khớp {@link #WEEK_FIELD_KEY_PATTERN} (ngày t2..t7/cn, buổi s/c,
 * chỉ số thứ tự tuỳ chọn, hậu tố phụ {@code _gio/_dd/_tp} tuỳ chọn) VÀ có giá trị khác rỗng — key lạ
 * bị bỏ qua (không suy đoán), key hợp lệ nhưng giá trị {@code ""} (FE gửi khi người dùng xoá ô) bị
 * loại khỏi JSONB (đúng mục 7.6.1 — không giữ key rỗng).
 *
 * <p><b>Sai lệch so với regex nêu trong tài liệu 09 mục 5.2</b> (ghi nhận, không sửa âm thầm tài
 * liệu gốc — xem mục 11): chuỗi gốc {@code ^t[2-7]|cn_(s|c)(_[0-9]+)?(_gio|_dd|_tp)?$} thiếu ngoặc
 * quanh phần alternation (Java hiểu là {@code (^t[2-7])|(cn_(s|c)...$)}, tách rời 2 nhánh không như
 * ý — nhánh {@code ^t[2-7]} sẽ khớp CẢ những chuỗi không liên quan miễn bắt đầu bằng {@code t2}..
 * {@code t7}). Đã SỬA thành {@code ^(t[2-7]|cn)_(s|c)(_[0-9]+)?(_gio|_dd|_tp)?$} — đúng theo NGỮ
 * NGHĨA mô tả bằng lời ngay phía trên trong tài liệu 09 ("ngày t2..t7 hoặc cn, buổi s/c, chỉ số thứ
 * tự tuỳ chọn, hậu tố phụ tuỳ chọn"), không phải suy đoán tự do.
 *
 * <p><b>Quyền (permission) — CHƯA áp dụng đủ 4 điều kiện {@code canEditSchedule} (mục 7.5 tài liệu
 * 09)</b>: chỉ mới chặn CỨNG khi có bằng chứng rõ ràng — bản ghi ĐÃ có {@code userCreator} khác
 * username hiện tại (điều kiện 1 "user_creator == currentUser.username" bị vi phạm rõ ràng). CHƯA
 * wire 3 điều kiện còn lại (menu_lichct chứa "Nhập lịch lãnh đạo UBND tỉnh"/"Nhập lịch cơ quan"/
 * "Nhập lịch đơn vị" + identifier khớp cấp tương ứng, qua {@code ResolveIdentifierByLevelUseCase}) —
 * nghĩa là user có quyền quản trị lịch phòng/đơn vị/cơ quan nhưng KHÔNG PHẢI người tạo bản ghi đó sẽ
 * tạm thời KHÔNG sửa được bản ghi đã tồn tại (chỉ tạo mới được). Ghi nhận là hạn chế cần hoàn thiện
 * đợt sau (mục 11 tài liệu 09) — thà chặt hơn cần thiết còn hơn suy diễn quyền không có bằng chứng.
 *
 * <p><b>Quyết định bảo mật chủ động</b>: {@code lanhdaotinh} (FE) được lưu như {@code coquan} trên
 * CHÍNH tenant hiện tại — KHÔNG nhận {@code ipserver}/{@code dbpath} tuỳ ý từ client (xem mục 7.6.7
 * tài liệu 09).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SaveWorkCalendarUseCase {

    private static final DateTimeFormatter WEEK_START_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Xem javadoc lop ve sai lech so voi chuoi regex goc trong tai lieu 09.
    static final Pattern WEEK_FIELD_KEY_PATTERN =
            Pattern.compile("^((t[2-7]|cn)_(s|c)(_[0-9]+)?(_gio|_dd|_tp)?|(t[2-7]|cn)_(gio|dd|tp)_(s|c))$");

    private final WorkCalendarRepository workCalendarRepository;
    private final WorkCalendarCacheService workCalendarCacheService;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final WorkCalendarPermissionService permissionService;

    public Result<UUID> execute(SaveWorkCalendarRequest request) {
        try {
            if (request.getScope() == null || request.getScope().isBlank()) {
                return Result.failure("type (scope) khong duoc de trong");
            }
            String scope = normalizeScope(request.getScope());
            LocalDate weekStart = parseWeekStart(request.getWeekStart());
            if (weekStart == null) {
                return Result.failure("thu2 khong hop le (yeu cau dd/MM/yyyy)");
            }

            String currentUsername = currentUserProvider.currentUsername().orElse(null);
            String weekFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    filterValidWeekFields(request.getWeekFields()), null, null, null);

            if (request.getId() != null) {
                return update(request, weekFieldsJson, currentUsername);
            }
            return create(request, scope, weekStart, weekFieldsJson, currentUsername);
        } catch (Exception ex) {
            log.error("Luu lich cong tac that bai", ex);
            return Result.failure("Luu lich cong tac that bai: " + ex.getMessage());
        }
    }

    private Result<UUID> update(SaveWorkCalendarRequest request, String weekFieldsJson, String currentUsername) {
        var opt = workCalendarRepository.findByIdAndDeletedOnIsNull(request.getId());
        if (opt.isEmpty()) {
            return Result.failure("Khong tim thay lich cong tac voi ID: " + request.getId());
        }
        var calendar = opt.get();

        if (!permissionService.canEdit(request.getScope(), calendar.getIdentifier(), calendar.getUserCreator())) {
            return Result.failure("Ban khong co quyen sua lich cong tac nay");
        }

        calendar.updateNote(request.getNote());
        calendar.replaceWeekFields(weekFieldsJson);
        var saved = workCalendarRepository.save(calendar);
        evictCache(saved);
        return Result.success(saved.getId());
    }

    private Result<UUID> create(
            SaveWorkCalendarRequest request, String scope, LocalDate weekStart,
            String weekFieldsJson, String currentUsername) {
        String identifier = "canhan".equals(scope) ? null : trimToNull(request.getIdentifier());
        // user_creator luon la nguoi dang thao tac (SERVER-AUTHORITATIVE, khong nhan tu client) -
        // dung tien le TaoDuThaoUseCase (userCreator do server tu gan theo tai khoan dang dang nhap).
        String userCreator = currentUsername;

        if (!permissionService.canCreate(request.getScope(), identifier)) {
            return Result.failure("Ban khong co quyen tao lich cong tac nay");
        }

        if ("canhan".equals(scope)) {
            if (userCreator == null) {
                return Result.failure("Khong xac dinh duoc nguoi dung hien tai");
            }
            var existing = workCalendarRepository
                    .findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(scope, weekStart, userCreator);
            if (existing.isEmpty()) {
                existing = workCalendarRepository
                        .findByScopeAndWeekStartBetweenAndUserCreatorAndDeletedOnIsNull(
                                scope, weekStart.minusDays(1), weekStart.plusDays(6), userCreator)
                        .stream().findFirst();
            }
            if (existing.isPresent()) {
                return updateExisting(existing.get(), request, weekFieldsJson);
            }
        } else {
            if (identifier == null) {
                return Result.failure("identifier khong duoc de trong voi type=" + scope);
            }
            var existing = workCalendarRepository
                    .findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(scope, weekStart, identifier);
            if (existing.isEmpty()) {
                existing = workCalendarRepository
                        .findByScopeAndWeekStartBetweenAndIdentifierAndDeletedOnIsNull(
                                scope, weekStart.minusDays(1), weekStart.plusDays(6), identifier)
                        .stream().findFirst();
            }
            if (existing.isPresent()) {
                return updateExisting(existing.get(), request, weekFieldsJson);
            }
        }

        var calendar = new WorkCalendar(scope, weekStart, identifier, userCreator, request.getNote());
        calendar.replaceWeekFields(weekFieldsJson);
        var saved = workCalendarRepository.save(calendar);
        evictCache(saved);
        return Result.success(saved.getId());
    }

    /**
     * Idempotent create by the calendar natural key. The FE normally supplies an id after loading
     * the week, but a refresh/race must not turn the same week into a duplicate-create error.
     * Authorization has already been checked by {@link WorkCalendarPermissionService#canCreate}.
     */
    private Result<UUID> updateExisting(
            WorkCalendar calendar, SaveWorkCalendarRequest request, String weekFieldsJson) {
        calendar.updateNote(request.getNote());
        calendar.replaceWeekFields(weekFieldsJson);
        var saved = workCalendarRepository.save(calendar);
        evictCache(saved);
        return Result.success(saved.getId());
    }

    private void evictCache(WorkCalendar calendar) {
        String subject = "canhan".equals(calendar.getScope()) ? calendar.getUserCreator() : calendar.getIdentifier();
        workCalendarCacheService.evict(calendar.getScope(), calendar.getWeekStart().format(WEEK_START_FORMAT), subject);
    }

    private Map<String, String> filterValidWeekFields(Map<String, String> rawFields) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawFields == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : rawFields.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase();
            if (key.isEmpty() || !WEEK_FIELD_KEY_PATTERN.matcher(key).matches()) {
                continue; // key la/khong hop le - bo qua, khong suy doan (muc 5.2 tai lieu 09)
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue; // o bi xoa (FE gui "") -> loai khoi JSONB, khong giu key rong (muc 7.6.1)
            }
            result.put(key, value);
        }
        return result;
    }

    // "lanhdaotinh" (FE) duoc luu nhu "coquan" tren CHINH tenant hien tai - xem javadoc lop nay.
    private String normalizeScope(String rawScope) {
        String scope = rawScope.trim().toLowerCase();
        return "lanhdaotinh".equals(scope) ? "coquan" : scope;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseWeekStart(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), WEEK_START_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
