package com.td.application.workcalendars;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.workcalendars.WorkCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Port lichct (08/09/2026) — test {@link SaveWorkCalendarUseCase}, cùng mẫu
 * {@code DuThaoDaBanHanhUseCaseTest}. 2 case bắt buộc theo yêu cầu: tạo mới, cập nhật ghi đè field
 * cũ (KHÔNG merge).
 */
@ExtendWith(MockitoExtension.class)
class SaveWorkCalendarUseCaseTest {

    @Mock
    private WorkCalendarRepository workCalendarRepository;
    @Mock
    private WorkCalendarCacheService workCalendarCacheService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private WorkCalendarPermissionService permissionService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private SaveWorkCalendarUseCase newUseCase() {
        return new SaveWorkCalendarUseCase(
                workCalendarRepository, workCalendarCacheService, dynamicDocumentFieldsService,
                currentUserProvider, permissionService);
    }

    @Test
    void execute_create_shouldFilterInvalidKeysAndBlankValues() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));
        when(permissionService.canCreate("canhan", null)).thenReturn(true);
        when(workCalendarRepository.findByScopeAndWeekStartAndUserCreatorAndDeletedOnIsNull(
                "canhan", LocalDate.of(2026, 8, 17), "alice"))
                .thenReturn(Optional.empty());
        when(workCalendarRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new SaveWorkCalendarRequest();
        request.setScope("canhan");
        request.setWeekStart("17/08/2026");
        request.setNote("Ghi chu tuan");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("t2_s", "Hop giao ban");
        fields.put("t2_s_gio", "08:00");
        fields.put("t2_dd_s", "Phong hop");
        fields.put("t2_s_dd", ""); // o rong -> bi loai
        fields.put("khong_hop_le", "xxx"); // key la -> bi loai
        request.setWeekFields(fields);

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();

        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(
                captureSavedWeekFieldsJson());
        assertThat(saved).containsEntry("t2_s", "Hop giao ban");
        assertThat(saved).containsEntry("t2_s_gio", "08:00");
        assertThat(saved).containsEntry("t2_dd_s", "Phong hop");
        assertThat(saved).doesNotContainKey("t2_s_dd");
        assertThat(saved).doesNotContainKey("khong_hop_le");
    }

    @Test
    void execute_update_shouldReplaceWeekFieldsEntirely_notMerge() {
        var existing = new WorkCalendar("coquan", LocalDate.of(2026, 8, 17), "CQ01", "alice", "Cu");
        existing.replaceWeekFields("{\"t2_s\":\"Noi dung cu\",\"t3_c\":\"Con lai\"}");

        when(workCalendarRepository.findByIdAndDeletedOnIsNull(existing.getId()))
                .thenReturn(Optional.of(existing));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));
        when(permissionService.canEdit("coquan", "CQ01", "alice")).thenReturn(true);
        when(workCalendarRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new SaveWorkCalendarRequest();
        request.setId(existing.getId());
        request.setScope("coquan");
        request.setWeekStart("17/08/2026");
        request.setIdentifier("CQ01");
        request.setNote("Ghi chu moi");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("t2_s", "Noi dung moi");
        request.setWeekFields(fields);

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(existing.getNote()).isEqualTo("Ghi chu moi");
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(existing.getWeekFieldsJson());
        assertThat(saved).containsEntry("t2_s", "Noi dung moi");
        // Ghi de TOAN BO - "t3_c" cua ban ghi cu KHONG con (khac hanh vi merge cua Category).
        assertThat(saved).doesNotContainKey("t3_c");
    }

    @Test
    void execute_create_whenNaturalKeyExists_shouldUpdateIdempotently() {
        var existing = new WorkCalendar("coquan", LocalDate.of(2026, 8, 17), "CQ01", "alice", "Cu");
        existing.replaceWeekFields("{\"t2_s\":\"Noi dung cu\"}");

        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));
        when(permissionService.canCreate("coquan", "CQ01")).thenReturn(true);
        when(workCalendarRepository.findByScopeAndWeekStartAndIdentifierAndDeletedOnIsNull(
                "coquan", LocalDate.of(2026, 8, 17), "CQ01"))
                .thenReturn(Optional.of(existing));
        when(workCalendarRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new SaveWorkCalendarRequest();
        request.setScope("coquan");
        request.setWeekStart("17/08/2026");
        request.setIdentifier("CQ01");
        request.setNote("Moi");
        request.setWeekFields(Map.of("t2_s", "Noi dung moi"));

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(existing.getId());
        assertThat(existing.getNote()).isEqualTo("Moi");
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(existing.getWeekFieldsJson()))
                .containsEntry("t2_s", "Noi dung moi");
    }

    @Test
    void execute_update_whenNotOwner_shouldFail() {
        var existing = new WorkCalendar("canhan", LocalDate.of(2026, 8, 17), null, "bob", null);
        existing.replaceWeekFields("{}");

        when(workCalendarRepository.findByIdAndDeletedOnIsNull(existing.getId()))
                .thenReturn(Optional.of(existing));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));

        var request = new SaveWorkCalendarRequest();
        request.setId(existing.getId());
        request.setScope("canhan");
        request.setWeekStart("17/08/2026");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    private String captureSavedWeekFieldsJson() {
        var captor = org.mockito.ArgumentCaptor.forClass(WorkCalendar.class);
        org.mockito.Mockito.verify(workCalendarRepository).save(captor.capture());
        return captor.getValue().getWeekFieldsJson();
    }
}
