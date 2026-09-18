package com.td.application.documents;

import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log) — Definition of Done (3): cập nhật document → có log {@code update} kèm diff
 * (oldValue khác newValue).
 */
@ExtendWith(MockitoExtension.class)
class UpdateDocumentUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentSearchService documentSearchService;
    @Mock
    private DocumentLogService documentLogService;

    @Test
    void execute_shouldLogUpdateWithDifferentOldAndNewTitle() {
        var useCase = new UpdateDocumentUseCase(documentRepository, documentCacheService, documentSearchService, documentLogService);

        BusinessDocument existing = new BusinessDocument("Tieu de cu", null, "DRAFT", null, null, null, null);
        UUID id = existing.getId();
        when(documentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateDocumentRequest();
        request.setId(id);
        request.setTitle("Tieu de moi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<Object> oldValueCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> newValueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(documentLogService).logUpdate(eq(id), oldValueCaptor.capture(), newValueCaptor.capture());

        DocumentDto oldValue = (DocumentDto) oldValueCaptor.getValue();
        DocumentDto newValue = (DocumentDto) newValueCaptor.getValue();
        assertThat(oldValue.getTitle()).isEqualTo("Tieu de cu");
        assertThat(newValue.getTitle()).isEqualTo("Tieu de moi");
    }

    @Test
    void execute_documentNotFound_shouldNotLogUpdate() {
        var useCase = new UpdateDocumentUseCase(documentRepository, documentCacheService, documentSearchService, documentLogService);
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new UpdateDocumentRequest();
        request.setId(id);
        request.setTitle("Khong ton tai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(documentLogService, org.mockito.Mockito.never()).logUpdate(any(), any(), any());
    }
}
