package com.td.application.documents;

import com.td.domain.documents.FileAccessLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 09/09/2026 (2) — test {@link ListFileAccessLogUseCase}: đọc PHẲNG file_access_logs (mỗi dòng đã
 * là 1 tổ hợp tệp/người dùng DUY NHẤT, không cần pivot — khác {@code ListVbdiUserActionLogUseCase}),
 * khoá {@code "actions"} trong {@code dynamic_fields} (JSONB) được parse qua
 * {@link DynamicDocumentFieldsService} thành {@code List<String>} ở field {@code "actions"}.
 */
@ExtendWith(MockitoExtension.class)
class ListFileAccessLogUseCaseTest {

    @Mock
    private FileAccessLogRepository fileAccessLogRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private ListFileAccessLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListFileAccessLogUseCase(fileAccessLogRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_shouldReturnFlatRowsWithFirstAndLastAccessedOnAndAccessCount() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        LocalDateTime first = LocalDateTime.of(2026, 9, 9, 8, 0, 0);
        LocalDateTime last = LocalDateTime.of(2026, 9, 10, 15, 30, 0);
        var entry = new FileAccessLog("vbdi", documentId, fileId, "Cong van 12.pdf",
            "{\"actions\":[\"view\",\"download\"]}", "nhanvien01", "Nguyen Van A", "So KH&CN / Phong TH",
            first, last, 3);
        when(fileAccessLogRepository.findByModuleAndDocumentId(eq("vbdi"), eq(documentId), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 200), 1));

        var request = new ListFileAccessLogRequest();
        request.setModule("vbdi");
        request.setDocumentId(documentId);

        List<Map<String, Object>> result = useCase.execute(request);

        assertThat(result).hasSize(1);
        var row = result.get(0);
        assertThat(row.get("fileId")).isEqualTo(fileId);
        assertThat(row.get("fileName")).isEqualTo("Cong van 12.pdf");
        assertThat(row.get("actions")).isEqualTo(List.of("view", "download"));
        assertThat(row.get("username")).isEqualTo("nhanvien01");
        assertThat(row.get("accessCount")).isEqualTo(3);
        assertThat(row.get("firstAccessedOn")).isNotNull();
        assertThat(row.get("lastAccessedOn")).isNotNull();
        assertThat(row.get("firstAccessedOn")).isNotEqualTo(row.get("lastAccessedOn"));
    }

    @Test
    void execute_withoutModule_shouldReturnEmptyWithoutQuerying() {
        var request = new ListFileAccessLogRequest();
        request.setDocumentId(UUID.randomUUID());

        assertThat(useCase.execute(request)).isEmpty();
    }

    @Test
    void execute_withoutDocumentId_shouldReturnEmptyWithoutQuerying() {
        var request = new ListFileAccessLogRequest();
        request.setModule("vbdi");

        assertThat(useCase.execute(request)).isEmpty();
    }
}
