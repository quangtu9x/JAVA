package com.td.application.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.documents.DocumentLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log) — Definition of Done (2)/(3)/(4): logCreate/logUpdate/logDelete/logHardDelete
 * ghi đúng {@code {documentId, logType, logData(JSONB), createdBy, createdAt}}, append-only.
 */
@ExtendWith(MockitoExtension.class)
class DocumentLogServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private DocumentLogRepository documentLogRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private DocumentLogService service;

    @BeforeEach
    void setUp() {
        service = new DocumentLogService(documentLogRepository, currentUserProvider);
    }

    @Test
    void logCreate_shouldSaveLogWithNewValueAndCurrentUsername() throws Exception {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));
        UUID documentId = UUID.randomUUID();

        service.logCreate(documentId, Map.of("title", "Van ban A"));

        ArgumentCaptor<DocumentLog> captor = ArgumentCaptor.forClass(DocumentLog.class);
        verify(documentLogRepository).save(captor.capture());
        DocumentLog saved = captor.getValue();

        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getLogType()).isEqualTo(DocumentLogService.LOG_TYPE_CREATE);
        assertThat(saved.getCreatedBy()).isEqualTo("alice");

        Map<String, Object> logData = OBJECT_MAPPER.readValue(saved.getLogDataJson(), Map.class);
        assertThat(logData).containsOnlyKeys("newValue");
    }

    @Test
    void logUpdate_shouldSaveLogWithOldAndNewValue() throws Exception {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("bob"));
        UUID documentId = UUID.randomUUID();

        service.logUpdate(documentId, Map.of("title", "Cu"), Map.of("title", "Moi"));

        ArgumentCaptor<DocumentLog> captor = ArgumentCaptor.forClass(DocumentLog.class);
        verify(documentLogRepository).save(captor.capture());
        DocumentLog saved = captor.getValue();

        assertThat(saved.getLogType()).isEqualTo(DocumentLogService.LOG_TYPE_UPDATE);
        Map<String, Object> logData = OBJECT_MAPPER.readValue(saved.getLogDataJson(), Map.class);
        assertThat(logData).containsOnlyKeys("oldValue", "newValue");
    }

    @Test
    void logDelete_shouldSaveLogWithOldValueOnly() throws Exception {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("carol"));
        UUID documentId = UUID.randomUUID();

        service.logDelete(documentId, Map.of("title", "Bi xoa"));

        ArgumentCaptor<DocumentLog> captor = ArgumentCaptor.forClass(DocumentLog.class);
        verify(documentLogRepository).save(captor.capture());
        DocumentLog saved = captor.getValue();

        assertThat(saved.getLogType()).isEqualTo(DocumentLogService.LOG_TYPE_DELETE);
        Map<String, Object> logData = OBJECT_MAPPER.readValue(saved.getLogDataJson(), Map.class);
        assertThat(logData).containsOnlyKeys("oldValue");
    }

    @Test
    void logHardDelete_shouldUseDistinctLogType() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("dave"));
        UUID documentId = UUID.randomUUID();

        service.logHardDelete(documentId, Map.of("title", "Xoa vinh vien"));

        ArgumentCaptor<DocumentLog> captor = ArgumentCaptor.forClass(DocumentLog.class);
        verify(documentLogRepository).save(captor.capture());
        assertThat(captor.getValue().getLogType()).isEqualTo(DocumentLogService.LOG_TYPE_HARD_DELETE);
    }

    @Test
    void logCreate_withoutAuthenticatedUser_shouldDefaultCreatedByToSystem() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        UUID documentId = UUID.randomUUID();

        service.logCreate(documentId, Map.of("title", "Van ban"));

        ArgumentCaptor<DocumentLog> captor = ArgumentCaptor.forClass(DocumentLog.class);
        verify(documentLogRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("system");
    }

    @Test
    void logCreate_whenRepositorySaveThrows_shouldNotPropagateException() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));
        when(documentLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        service.logCreate(UUID.randomUUID(), Map.of("title", "Van ban"));
        // Khong throw - audit log loi khong duoc lam hong thao tac nghiep vu chinh
    }

    @Test
    void logDelete_shouldNeverCallUpdateOrDeleteOnRepository() {
        // DocumentLogRepository co chu dich khong co phuong thuc update/delete - test nay xac nhan
        // interface chi co save() va query, dam bao "log khong the sua/xoa" o tang bien dich.
        assertThat(DocumentLogRepository.class.getMethods()).extracting(java.lang.reflect.Method::getName)
            .containsExactlyInAnyOrder("save", "findByDocumentId");
    }
}
