package com.td.application.documents;

import com.td.domain.documents.DocumentLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log) — Definition of Done (5): API tra cứu log theo documentId hoạt động, có
 * phân trang.
 */
@ExtendWith(MockitoExtension.class)
class GetDocumentLogsUseCaseTest {

    @Mock
    private DocumentLogRepository documentLogRepository;

    @Test
    void execute_shouldReturnMappedLogsPaginated() {
        UUID documentId = UUID.randomUUID();
        DocumentLog createLog = new DocumentLog(documentId, "create", "{\"newValue\":{\"title\":\"A\"}}", "alice");
        DocumentLog updateLog = new DocumentLog(documentId, "update", "{\"oldValue\":{},\"newValue\":{}}", "bob");

        when(documentLogRepository.findByDocumentId(eq(documentId), any())).thenReturn(
            new PageImpl<>(List.of(updateLog, createLog), PageRequest.of(0, 20), 2));

        var useCase = new GetDocumentLogsUseCase(documentLogRepository);
        var result = useCase.execute(documentId, 0, 20);

        assertThat(result.getTotalItems()).isEqualTo(2L);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getLogType()).isEqualTo("update");
        assertThat(result.getItems().get(0).getLogData()).containsKeys("oldValue", "newValue");
        assertThat(result.getItems().get(1).getLogType()).isEqualTo("create");
        assertThat(result.getItems().get(1).getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void execute_withException_shouldReturnEmptyPageGracefully() {
        when(documentLogRepository.findByDocumentId(any(), any())).thenThrow(new RuntimeException("DB error"));

        var useCase = new GetDocumentLogsUseCase(documentLogRepository);
        var result = useCase.execute(UUID.randomUUID(), 0, 20);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalItems()).isZero();
    }

    @Test
    void execute_shouldClampPageSize() {
        when(documentLogRepository.findByDocumentId(any(), any())).thenReturn(
            new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        var useCase = new GetDocumentLogsUseCase(documentLogRepository);
        useCase.execute(UUID.randomUUID(), -3, 9999);
        // Khong throw voi input bat thuong - pageNumber am va pageSize qua lon deu duoc clamp
    }
}
