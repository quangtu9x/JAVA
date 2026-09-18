package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DT-007 (tách draft_process sang bảng riêng, yêu cầu người dùng 25/08/2026): khóa hành vi của
 * {@link AddDraftProcessDocumentUseCase} — writer DUY NHẤT của bảng vật lý độc lập
 * {@code draft_process_documents}.
 */
@ExtendWith(MockitoExtension.class)
class AddDraftProcessDocumentUseCaseTest {

    @Mock
    private DraftProcessDocumentRepository draftProcessDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AddDraftProcessDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        // Khoi tao thu cong (khong @InjectMocks): DynamicDocumentFieldsService la DOI TUONG THAT,
        // khong phai mock nen constructor-injection cua Mockito khong tien duoc no.
        useCase = new AddDraftProcessDocumentUseCase(
            draftProcessDocumentRepository, dynamicDocumentFieldsService, documentLogService);
    }

    @Test
    void execute_shouldForceDraftProcessDocumentTypeAndPersistFields() throws Exception {
        when(draftProcessDocumentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        // Caller cố tình đặt type khác - tầng use case PHẢI ghi đè (bảng chỉ chứa draft_process).
        request.setDocumentType("duthao");
        request.setStatus("5");
        request.setTitle("Xử lý dự thảo: chuyenduyet");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("parentid", "8f2c6a1e-0000-0000-0000-000000000001");
        fields.put("action", "chuyenduyet");
        fields.put("creator", "nguyenvana");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.domain.duthao.DraftProcessDocument> captor =
            ArgumentCaptor.forClass(com.td.domain.duthao.DraftProcessDocument.class);
        verify(draftProcessDocumentRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getDocumentType()).isEqualTo("draft_process");
        assertThat(saved.getStatus()).isEqualTo("5");
        assertThat(saved.getTitle()).isEqualTo("Xử lý dự thảo: chuyenduyet");
        Map<String, Object> dynamic = objectMapper.readValue(saved.getDynamicFieldsJson(), Map.class);
        assertThat(dynamic)
            .containsEntry("parentid", "8f2c6a1e-0000-0000-0000-000000000001")
            .containsEntry("action", "chuyenduyet")
            .containsEntry("creator", "nguyenvana");
        verify(documentLogService).logCreate(any(), any());
    }

    @Test
    void execute_withBlankTitleAndStatus_shouldApplyDefaults() {
        when(draftProcessDocumentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.domain.duthao.DraftProcessDocument> captor =
            ArgumentCaptor.forClass(com.td.domain.duthao.DraftProcessDocument.class);
        verify(draftProcessDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Quá trình xử lý dự thảo");
        assertThat(captor.getValue().getStatus()).isEqualTo(DuThaoStatus.DRAFTING);
    }

    @Test
    void execute_whenRepositoryThrows_shouldReturnFailureWithoutRethrowing() {
        when(draftProcessDocumentRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        var request = new AddDynamicDocumentRequest();

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(draftProcessDocumentRepository).save(any());
    }
}