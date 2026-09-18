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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G1: {@link CapNhatHoSoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatHoSoUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatHoSoUseCase(
            hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            authorAclFieldsService);
    }

    @Test
    void execute_withoutId_shouldReturnFailure() {
        var result = useCase.execute(new LuuHoSoRequest());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(id)).thenReturn(Optional.empty());
        var request = new LuuHoSoRequest();
        request.setId(id);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldMergeFieldsKeepingExistingValuesNotInRequest() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "hosocv",
            "{\"category\":\"Hanh chinh\",\"first_topic\":\"De muc A\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new LuuHoSoRequest();
        request.setId(document.getId());
        request.getFields().put("category", "Tu phap");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("category")).isEqualTo("Tu phap");
        assertThat(fields.get("first_topic")).isEqualTo("De muc A");
    }

    @Test
    void execute_clientSentUserCreator_shouldBeIgnored() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "hosocv", "{\"user_creator\":\"thatuser\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new LuuHoSoRequest();
        request.setId(document.getId());
        request.getFields().put("user_creator", "keattacker");

        useCase.execute(request);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("user_creator")).isEqualTo("thatuser");
    }

    @Test
    void execute_withTitle_shouldUpdateTitleColumn() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "hosocv", "{}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new LuuHoSoRequest();
        request.setId(document.getId());
        request.setTitle("Ho so moi ten");

        useCase.execute(request);

        assertThat(document.getTitle()).isEqualTo("Ho so moi ten");
    }

    private HoSoCVDocument newDocument(UUID id, String documentType, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
