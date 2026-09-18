package com.td.application.workcalendars;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.workcalendars.WorkCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Port lichct (08/09/2026) — test {@link GetWeekWorkCalendarUseCase}. Case bắt buộc theo yêu cầu:
 * chưa có bản ghi → mảng rỗng (KHÔNG lỗi). Cộng case có bản ghi → shape SPREAD đúng contract Domino
 * (mục 7.4/8 tài liệu 09).
 */
@ExtendWith(MockitoExtension.class)
class GetWeekWorkCalendarUseCaseTest {

    @Mock
    private WorkCalendarRepository workCalendarRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetWeekWorkCalendarUseCase newUseCase() {
        return new GetWeekWorkCalendarUseCase(workCalendarRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_whenNoRecord_shouldReturnEmptyList() {
        when(workCalendarRepository.findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
                "coquan", LocalDate.of(2026, 8, 17), "CQ01"))
                .thenReturn(Optional.empty());

        var request = new GetWeekWorkCalendarRequest();
        request.setWeekStart("17/08/2026");
        request.setScope("coquan");
        request.setSubject("CQ01");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_whenInvalidWeekStart_shouldReturnEmptyList_notError() {
        var request = new GetWeekWorkCalendarRequest();
        request.setWeekStart("not-a-date");
        request.setScope("coquan");
        request.setSubject("CQ01");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_whenRecordExists_shouldSpreadWeekFieldsAtTopLevel() {
        var calendar = new WorkCalendar("coquan", LocalDate.of(2026, 8, 17), "CQ01", "alice", "Ghi chu");
        calendar.replaceWeekFields("{\"t2_s\":\"Hop giao ban\",\"t2_s_gio\":\"08:00\"}");

        when(workCalendarRepository.findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
                "coquan", LocalDate.of(2026, 8, 17), "CQ01"))
                .thenReturn(Optional.of(calendar));

        var request = new GetWeekWorkCalendarRequest();
        request.setWeekStart("17/08/2026");
        request.setScope("coquan");
        request.setSubject("CQ01");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        List<java.util.Map<String, Object>> data = result.getData();
        assertThat(data).hasSize(1);
        var item = data.get(0);
        assertThat(item.get("thu2")).isEqualTo("17/08/2026");
        assertThat(item.get("type")).isEqualTo("coquan");
        assertThat(item.get("identifier")).isEqualTo("CQ01");
        assertThat(item.get("ghichu")).isEqualTo("Ghi chu");
        assertThat(item.get("t2_s")).isEqualTo("Hop giao ban");
        assertThat(item.get("t2_s_gio")).isEqualTo("08:00");
    }
}
