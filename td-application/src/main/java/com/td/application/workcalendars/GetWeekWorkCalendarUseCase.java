package com.td.application.workcalendars;

import com.td.application.common.models.Result;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port lichct (08/09/2026) — tương đương {@code GetLichCongTac.java} (Domino) phần WEB (bỏ nhánh
 * {@code mobile=1}/{@code buildScheduleArrayNode} — HOÃN LẠI, xem mục 11 tài liệu 09).
 *
 * <p>Mỗi tuần/1 chủ thể chỉ có ĐÚNG 1 bản ghi (unique index {@code work_calendars}) — trả mảng 1
 * phần tử khi có, mảng RỖNG khi chưa có (KHÔNG lỗi — đúng hành vi Domino {@code total=0}).
 *
 * <p><b>Shape response</b> (mục 7.4/8 tài liệu 09): key thường ({@code id/thu2/type/identifier/
 * user_creator/ghichu}) SPREAD cùng cấp với toàn bộ key trong {@code week_fields} (t2_s, t2_s_gio,
 * ...) — KHÔNG lồng field động vào 1 object con.
 *
 * <p><b>Quyết định bảo mật chủ động (khác Domino gốc)</b>: {@code lanhdaotinh} KHÔNG đọc chéo
 * server/dbpath ({@code ipserver}/{@code dbpath}) — chỉ đọc/ghi như {@code coquan} trên CHÍNH tenant
 * hiện tại của request (đúng tinh thần mục 7.6.7 tài liệu 09, tránh SSRF/mở kết nối tuỳ ý theo input
 * client) — {@code identifier} (mã lãnh đạo) vẫn do FE gửi như bình thường.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetWeekWorkCalendarUseCase {

    private static final DateTimeFormatter WEEK_START_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WorkCalendarRepository workCalendarRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<List<Map<String, Object>>> execute(GetWeekWorkCalendarRequest request) {
        try {
            LocalDate weekStart = parseWeekStart(request.getWeekStart());
            String scope = normalizeScope(request.getScope());
            String subject = request.getSubject() == null ? null : request.getSubject().trim();

            // Input khong hop le -> tra rong (KHONG loi) - dung tinh than "chua co du lieu" cua
            // Domino (total=0), tranh phinh 500 cho 1 thao tac doc.
            if (weekStart == null || scope.isBlank() || subject == null || subject.isBlank()) {
                return Result.success(List.of());
            }

            Optional<WorkCalendar> found = "canhan".equals(scope)
                    ? workCalendarRepository.findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(
                            scope, weekStart, subject)
                    : workCalendarRepository.findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
                            scope, weekStart, subject);

            if (found.isEmpty()) {
                return Result.success(List.of());
            }
            return Result.success(List.of(toLegacyShape(found.get())));
        } catch (Exception ex) {
            log.error("Lay lich cong tac that bai", ex);
            return Result.failure("Lay lich cong tac that bai: " + ex.getMessage());
        }
    }

    private Map<String, Object> toLegacyShape(WorkCalendar calendar) {
        Map<String, Object> weekFields = dynamicDocumentFieldsService.parseDynamicFields(calendar.getWeekFieldsJson());
        WorkCalendarDto dto = WorkCalendarDtoMapper.map(calendar, weekFields);

        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("id", dto.getId());
        shape.put("thu2", dto.getWeekStart().format(WEEK_START_FORMAT));
        shape.put("type", dto.getScope());
        shape.put("identifier", dto.getIdentifier());
        shape.put("user_creator", dto.getUserCreator());
        shape.put("ghichu", dto.getNote());
        shape.putAll(dto.getWeekFields());
        return shape;
    }

    // "lanhdaotinh" (FE) duoc luu/doc nhu "coquan" tren CHINH tenant hien tai - xem javadoc lop nay.
    private String normalizeScope(String rawScope) {
        String scope = rawScope == null ? "" : rawScope.trim().toLowerCase();
        return "lanhdaotinh".equals(scope) ? "coquan" : scope;
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
