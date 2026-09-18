package com.td.application.vbdi;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 11/09/2026 (yêu cầu người dùng "port vbdi/thungrac/thungrac sang JAVA") — test
 * {@link RestoreVbdiDocumentUseCase}: xem javadoc lớp đó (port {@code handleRestore#case "recover"}).
 */
@ExtendWith(MockitoExtension.class)
class RestoreVbdiDocumentUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private RestoreVbdiDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RestoreVbdiDocumentUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
    }

    private VbdiDocument newVbdiDocument() {
        return new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(useCase.execute(id).isSuccess()).isFalse();
    }

    @Test
    void execute_withNotDeletedDocument_shouldNoOpAndStillSucceed() {
        VbdiDocument vbdi = newVbdiDocument();
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var result = useCase.execute(vbdi.getId());

        assertThat(result.isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentLogService, never()).logUpdate(any(), any(), any());
    }

    @Test
    void execute_withDeletedDocument_shouldClearDeletedFlagsAndSave() {
        VbdiDocument vbdi = newVbdiDocument();
        vbdi.markAsDeleted(UUID.randomUUID());
        assertThat(vbdi.isDeleted()).isTrue();
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(vbdi.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(vbdi.isDeleted()).isFalse();
        assertThat(vbdi.getDeletedOn()).isNull();
        assertThat(vbdi.getDeletedBy()).isNull();
        verify(vbdiDocumentRepository).save(vbdi);
        verify(documentCacheService).evict(vbdi.getId());
        verify(documentCacheService).evictAllListCaches();
        verify(documentLogService).logUpdate(eq(vbdi.getId()), any(), any());
    }
}
