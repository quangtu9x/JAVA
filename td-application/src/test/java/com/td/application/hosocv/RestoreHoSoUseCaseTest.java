package com.td.application.hosocv;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G6: {@link RestoreHoSoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class RestoreHoSoUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private RestoreHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RestoreHoSoUseCase(hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
    }

    @Test
    void execute_withEmptyList_shouldReturnFailure() {
        assertThat(useCase.execute(List.of()).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldClearDeletedOnAndDeletedBy() {
        HoSoCVDocument document = newDocument(UUID.randomUUID());
        document.markAsDeleted(UUID.randomUUID());
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(List.of(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.isDeleted()).isFalse();
        assertThat(document.getDeletedBy()).isNull();
    }

    @Test
    void execute_notDeleted_shouldBeIdempotent() {
        HoSoCVDocument document = newDocument(UUID.randomUUID());
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(List.of(document.getId()));

        assertThat(result.isSuccess()).isTrue();
    }

    private HoSoCVDocument newDocument(UUID id) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields("{}");
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
