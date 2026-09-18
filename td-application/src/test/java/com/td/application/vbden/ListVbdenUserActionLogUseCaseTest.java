package com.td.application.vbden;

import com.td.domain.vbden.VbdenUserActionLog;
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
 * 18/09/2026 (yêu cầu người dùng "thêm vết audit của văn bản đến"): test
 * {@link ListVbdenUserActionLogUseCase} — nhóm các dòng phẳng theo username, pivot actionType
 * thành field chứa danh sách timestamp nối ";" — mirror
 * {@code com.td.application.vbdi.ListVbdiUserActionLogUseCaseTest}.
 */
@ExtendWith(MockitoExtension.class)
class ListVbdenUserActionLogUseCaseTest {

    @Mock
    private VbdenUserActionLogRepository vbdenUserActionLogRepository;

    private ListVbdenUserActionLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVbdenUserActionLogUseCase(vbdenUserActionLogRepository);
    }

    // VbdenUserActionLog khong co setter cong khai cho createdOn (audit field do AuditableEntity
    // quan ly) - dung reflection de dung 1 gia tri co dinh trong test.
    private VbdenUserActionLog entryWithCreatedOn(UUID parentId, String username, String fullname,
            String unit, String identifier, String actionType, LocalDateTime createdOn) throws Exception {
        var entry = new VbdenUserActionLog(parentId, username, fullname, unit, identifier, actionType);
        var field = entry.getClass().getSuperclass().getDeclaredField("createdOn");
        field.setAccessible(true);
        field.set(entry, createdOn);
        return entry;
    }

    @Test
    void execute_shouldGroupByUsernameAndJoinTimestampsPerActionType() throws Exception {
        UUID documentId = UUID.randomUUID();
        var first = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "tiepnhan", LocalDateTime.of(2026, 9, 18, 10, 0, 0));
        var second = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "chuyenxuly", LocalDateTime.of(2026, 9, 18, 11, 0, 0));
        var thirdOtherUser = entryWithCreatedOn(documentId, "hoangnt.skhcn", "Nong Thanh Hoang",
            "Phong KHTC", "H56.07.02", "tiepnhan", LocalDateTime.of(2026, 9, 18, 12, 0, 0));
        when(vbdenUserActionLogRepository.findByParentId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(first, second, thirdOtherUser)));

        var request = new ListVbdenUserActionLogRequest();
        request.setDocumentId(documentId);

        List<Map<String, Object>> result = useCase.execute(request);

        assertThat(result).hasSize(2);
        var xuanRow = result.stream().filter(r -> "xuanlt.skhcn".equals(r.get("username"))).findFirst().orElseThrow();
        assertThat(xuanRow.get("fullname")).isEqualTo("Le Thi Xuan");
        assertThat(xuanRow.get("tiepnhan")).isEqualTo("18/09/2026 10:00:00 AM");
        assertThat(xuanRow.get("chuyenxuly")).isEqualTo("18/09/2026 11:00:00 AM");
    }

    @Test
    void execute_withSameActionTypeTwice_shouldJoinTimestampsWithSemicolon() throws Exception {
        UUID documentId = UUID.randomUUID();
        var first = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "thuhoivanban", LocalDateTime.of(2026, 9, 18, 10, 0, 0));
        var second = entryWithCreatedOn(documentId, "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "thuhoivanban", LocalDateTime.of(2026, 9, 18, 14, 0, 0));
        when(vbdenUserActionLogRepository.findByParentId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(first, second)));

        var request = new ListVbdenUserActionLogRequest();
        request.setDocumentId(documentId);

        List<Map<String, Object>> result = useCase.execute(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("thuhoivanban")).isEqualTo("18/09/2026 10:00:00 AM;18/09/2026 02:00:00 PM");
    }

    @Test
    void execute_withoutDocumentId_shouldReturnEmptyWithoutQuerying() {
        var result = useCase.execute(new ListVbdenUserActionLogRequest());

        assertThat(result).isEmpty();
        verifyNoInteractions(vbdenUserActionLogRepository);
    }
}
