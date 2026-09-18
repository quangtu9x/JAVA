package com.td.application.documents;

import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log): xoá vĩnh viễn document → có log {@code hard_delete}, ghi log SAU khi
 * hard-delete để chứng minh log không phụ thuộc FK vào document đã bị xoá (xem V1.0.13).
 */
@ExtendWith(MockitoExtension.class)
class HardDeleteDocumentUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentSearchService documentSearchService;
    @Mock
    private DocumentLogService documentLogService;

    @Test
    void execute_shouldHardDeleteThenLogHardDelete() {
        var useCase = new HardDeleteDocumentUseCase(documentRepository, documentCacheService, documentSearchService, documentLogService);

        BusinessDocument existing = new BusinessDocument("Van ban", null, "DRAFT", null, null, null, null);
        UUID id = existing.getId();
        when(documentRepository.findById(id)).thenReturn(Optional.of(existing));

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isTrue();

        InOrder inOrder = inOrder(documentRepository, documentLogService);
        inOrder.verify(documentRepository).hardDelete(existing);
        inOrder.verify(documentLogService).logHardDelete(eq(id), any());
    }

    @Test
    void execute_documentNotFound_shouldNotLog() {
        var useCase = new HardDeleteDocumentUseCase(documentRepository, documentCacheService, documentSearchService, documentLogService);
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isFalse();
        org.mockito.Mockito.verify(documentLogService, org.mockito.Mockito.never()).logHardDelete(any(), any());
    }
}
