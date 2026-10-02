package com.td.application.duthao;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDisplayInfoService;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 25/09/2026 — xem javadoc {@link AddDuThaoSeenDocumentUseCase}. */
@ExtendWith(MockitoExtension.class)
class AddDuThaoSeenDocumentUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserDisplayInfoService userDisplayInfoService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private AddDuThaoSeenDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddDuThaoSeenDocumentUseCase(duThaoDocumentRepository, dynamicDocumentFieldsService,
            documentCacheService, currentUserProvider, userDisplayInfoService);
        lenient().when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo2"));
        lenient().when(userDisplayInfoService.resolve("demo2"))
            .thenReturn(new UserDisplayInfoService.UserDisplayInfo("Trưởng phòng", "Sở KHCN / Phòng CN"));
    }

    private DuThaoDocument draft(String type) {
        return new DuThaoDocument("Trich yeu", type, "0", null, null, null, null);
    }

    private AddDuThaoSeenDocumentRequest request(UUID id) {
        var request = new AddDuThaoSeenDocumentRequest();
        request.setDocumentId(id);
        return request;
    }

    @Test
    void execute_firstView_shouldRecordSeenListAndDetail() {
        var document = draft("duthao");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("log_seen_document"))).isEqualTo("demo2");
        @SuppressWarnings("unchecked")
        var detail = (List<Map<String, Object>>) fields.get("log_seen_document_detail");
        assertThat(detail).hasSize(1);
        assertThat(detail.get(0)).containsEntry("username", "demo2")
            .containsEntry("name", "Trưởng phòng").containsEntry("unit", "Sở KHCN / Phòng CN");
        assertThat(detail.get(0).get("created")).isEqualTo(detail.get(0).get("lastSeen"));
        verify(documentCacheService).evict(document.getId());
    }

    @Test
    void execute_repeatView_shouldNotDuplicate_andKeepFirstSeen() {
        var document = draft("duthao");
        document.replaceDynamicFields("{\"log_seen_document\":\"demo3;demo2\",\"log_seen_document_detail\":"
            + "[{\"username\":\"demo2\",\"name\":\"cu\",\"unit\":\"\",\"created\":\"01/01/2026 08:00:00\","
            + "\"lastSeen\":\"01/01/2026 08:00:00\"}]}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        useCase.execute(request(document.getId()));

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("log_seen_document"))).isEqualTo("demo3;demo2");
        @SuppressWarnings("unchecked")
        var detail = (List<Map<String, Object>>) fields.get("log_seen_document_detail");
        assertThat(detail).hasSize(1);
        assertThat(detail.get(0)).containsEntry("created", "01/01/2026 08:00:00")
            .containsEntry("name", "Trưởng phòng");
        assertThat(detail.get(0).get("lastSeen")).isNotEqualTo("01/01/2026 08:00:00");
    }

    @Test
    void execute_whenWrongTypeOrMissing_shouldFailWithoutSaving() {
        var process = draft("draft_process");
        when(duThaoDocumentRepository.findById(process.getId())).thenReturn(Optional.of(process));
        UUID missing = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(useCase.execute(request(process.getId())).isSuccess()).isFalse();
        assertThat(useCase.execute(request(missing)).isSuccess()).isFalse();
        assertThat(useCase.execute(null).isSuccess()).isFalse();
        verify(duThaoDocumentRepository, never()).save(any());
    }
}
