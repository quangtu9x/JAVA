package com.td.application.vbden;

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
 * BL-210 (VBDen Core) — DoD (4), yêu cầu người dùng 05/09/2026 ("tách vbden_res sang bảng riêng"):
 * khóa hành vi của {@link AddVbdenResDocumentUseCase} — writer DUY NHẤT của bảng vật lý độc lập
 * {@code vbden_res_documents}.
 */
@ExtendWith(MockitoExtension.class)
class AddVbdenResDocumentUseCaseTest {

    @Mock
    private VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AddVbdenResDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        // Khoi tao thu cong (khong @InjectMocks): DynamicDocumentFieldsService la DOI TUONG THAT,
        // khong phai mock nen constructor-injection cua Mockito khong tien duoc no.
        useCase = new AddVbdenResDocumentUseCase(
            vbdenResDocumentRepository, dynamicDocumentFieldsService, documentLogService);
    }

    @Test
    void execute_shouldForceVbdenResDocumentTypeAndPersistFields() throws Exception {
        when(vbdenResDocumentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        // Caller co tinh dat type khac - tang use case PHAI ghi de (bang chi chua vbden_res).
        request.setDocumentType("vbden");
        request.setStatus("5");
        request.setTitle("chuyenxuly");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("parentid", "8f2c6a1e-0000-0000-0000-000000000001");
        fields.put("action", "chuyenxuly");
        fields.put("nguoi_thuc_hien", "vanthu01");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.domain.vbden.VbdenResDocument> captor =
            ArgumentCaptor.forClass(com.td.domain.vbden.VbdenResDocument.class);
        verify(vbdenResDocumentRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getDocumentType()).isEqualTo("vbden_res");
        assertThat(saved.getStatus()).isEqualTo("5");
        assertThat(saved.getTitle()).isEqualTo("chuyenxuly");
        Map<String, Object> dynamic = objectMapper.readValue(saved.getDynamicFieldsJson(), Map.class);
        assertThat(dynamic)
            .containsEntry("parentid", "8f2c6a1e-0000-0000-0000-000000000001")
            .containsEntry("action", "chuyenxuly")
            .containsEntry("nguoi_thuc_hien", "vanthu01");
        verify(documentLogService).logCreate(any(), any());
    }

    @Test
    void execute_withBlankTitleAndStatus_shouldApplyDefaults() {
        when(vbdenResDocumentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.domain.vbden.VbdenResDocument> captor =
            ArgumentCaptor.forClass(com.td.domain.vbden.VbdenResDocument.class);
        verify(vbdenResDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Quá trình xử lý văn bản đến");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void execute_whenRepositoryThrows_shouldReturnFailureWithoutRethrowing() {
        when(vbdenResDocumentRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        var request = new AddDynamicDocumentRequest();

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(vbdenResDocumentRepository).save(any());
    }
}
