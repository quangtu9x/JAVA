package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G4/G9: {@link GopYPhoiHopUseCase}. Đồng bộ ngược
 * {@code coordinated}/{@code user_coordinated} — tương đương {@code saveInitHoso}
 * ({@code TDDocument.java} dòng 916-967).
 */
@ExtendWith(MockitoExtension.class)
class GopYPhoiHopUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GopYPhoiHopUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GopYPhoiHopUseCase(
            hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService,
            documentLogService, currentUserProvider, addHoSoCVProcessDocumentUseCase);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));
        lenient().when(addHoSoCVProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_shouldAppendUsernameToCoordinatedFieldsAndCreateProcessRecord() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(),
            "{\"user_coordinator\":\"phoihop01;phoihop02\",\"user_coordinated\":\"phoihop02\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new GopYPhoiHopRequest();
        request.setHosocvId(document.getId());
        request.setOpinion("Dong y voi phuong an xu ly");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("user_coordinated"))).contains("phoihop01").contains("phoihop02");
        assertThat(String.valueOf(fields.get("coordinated"))).contains("phoihop01");

        ArgumentCaptor<com.td.application.documents.AddDynamicDocumentRequest> captor =
            ArgumentCaptor.forClass(com.td.application.documents.AddDynamicDocumentRequest.class);
        verify(addHoSoCVProcessDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("hosocv_id", document.getId().toString());
        assertThat(captor.getValue().getFields()).containsEntry("process_type", "phoihop");
        assertThat(captor.getValue().getFields()).containsEntry("opinion", "Dong y voi phuong an xu ly");
    }

    @Test
    void execute_alreadyCoordinated_shouldNotDuplicateUsername() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{\"user_coordinated\":\"phoihop01\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new GopYPhoiHopRequest();
        request.setHosocvId(document.getId());
        request.setOpinion("Y kien lan 2");

        useCase.execute(request);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(String.valueOf(fields.get("user_coordinated"))).isEqualTo("phoihop01");
    }

    private HoSoCVDocument newDocument(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
