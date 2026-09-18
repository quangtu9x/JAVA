package com.td.application.vbdi;

import com.td.domain.vbdi.VbdiUserActionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 30/08/2026 (port {@code qlvb/vbdi?...&action=getloguseraction}): test
 * {@link ListVbdiUserActionLogUseCase} — nhóm các dòng phẳng theo username, pivot actionType thành
 * field chứa danh sách timestamp nối ";" (khớp shape Domino cũ mà {@code LogUserAction.jsx} render).
 */
@ExtendWith(MockitoExtension.class)
class ListVbdiUserActionLogUseCaseTest {

    @Mock
    private VbdiUserActionLogRepository vbdiUserActionLogRepository;

    private ListVbdiUserActionLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVbdiUserActionLogUseCase(vbdiUserActionLogRepository);
    }

    // VbdiUserActionLog khong co setter cong khai cho createdOn (audit field do AuditableEntity
    // quan ly) - dung reflection de dung 1 gia tri co dinh trong test, tranh phu thuoc thoi gian
    // that thi (Date.now()-style) khong duoc phep trong moi truong nay.
    private VbdiUserActionLog entryWithCreatedOn(UUID parentId, String username, String fullname,
            String unit, String identifier, String actionType, LocalDateTime createdOn) throws Exception {
        var entry = new VbdiUserActionLog(parentId, username, fullname, unit, identifier, actionType);
        var field = entry.getClass().getSuperclass().getDeclaredField("createdOn");
        field.setAccessible(true);
        field.set(entry, createdOn);
        return entry;
    }

    @Test
    void execute_shouldGroupByUsernameAndJoinTimestampsPerActionType() throws Exception {
        UUID documentId = UUID.randomUUID();
        var first = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "guivanban", LocalDateTime.of(2026, 8, 30, 10, 0, 0));
        var second = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "guilaivanban", LocalDateTime.of(2026, 8, 30, 11, 0, 0));
        var thirdOtherUser = entryWithCreatedOn(documentId, "hoangnt.skhcn", "Nong Thanh Hoang",
            "Phong KHTC", "H56.07.02", "guivanban", LocalDateTime.of(2026, 8, 30, 12, 0, 0));
        when(vbdiUserActionLogRepository.findByParentId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(first, second, thirdOtherUser)));

        var request = new ListVbdiUserActionLogRequest();
        request.setDocumentId(documentId);

        List<Map<String, Object>> result = useCase.execute(request);

        assertThat(result).hasSize(2);
        var xuanRow = result.stream().filter(r -> "xuanlt.skhcn".equals(r.get("username"))).findFirst().orElseThrow();
        assertThat(xuanRow.get("fullname")).isEqualTo("Le Thi Xuan");
        assertThat(xuanRow.get("guivanban")).isEqualTo("30/08/2026 10:00:00 AM");
        assertThat(xuanRow.get("guilaivanban")).isEqualTo("30/08/2026 11:00:00 AM");
    }

    @Test
    void execute_withSameActionTypeTwice_shouldJoinTimestampsWithSemicolon() throws Exception {
        UUID documentId = UUID.randomUUID();
        var first = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "guivanban", LocalDateTime.of(2026, 8, 30, 10, 0, 0));
        var second = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "guivanban", LocalDateTime.of(2026, 8, 30, 14, 0, 0));
        when(vbdiUserActionLogRepository.findByParentId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(first, second)));

        var request = new ListVbdiUserActionLogRequest();
        request.setDocumentId(documentId);

        List<Map<String, Object>> result = useCase.execute(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("guivanban")).isEqualTo("30/08/2026 10:00:00 AM;30/08/2026 02:00:00 PM");
    }

    @Test
    void execute_withoutDocumentId_shouldReturnEmptyWithoutQuerying() {
        var result = useCase.execute(new ListVbdiUserActionLogRequest());

        assertThat(result).isEmpty();
        verifyNoInteractions(vbdiUserActionLogRepository);
    }
}
