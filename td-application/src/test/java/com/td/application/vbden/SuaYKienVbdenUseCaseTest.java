package com.td.application.vbden;

import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.VbdenResDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 15/09/2026 ("Sửa ý kiến") — xem javadoc {@link SuaYKienVbdenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class SuaYKienVbdenUseCaseTest {

    @Mock
    private VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private SuaYKienVbdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SuaYKienVbdenUseCase(vbdenResDocumentRepository, dynamicDocumentFieldsService, documentLogService);
        lenient().when(vbdenResDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void execute_withUnknownRecord_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdenResDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new SuaYKienVbdenRequest();
        request.setId(id);
        request.setOpinion("Y kien moi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verify(vbdenResDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        VbdenResDocument record = newRecord("vbden", "{}");
        when(vbdenResDocumentRepository.findById(record.getId())).thenReturn(Optional.of(record));

        var request = new SuaYKienVbdenRequest();
        request.setId(record.getId());
        request.setOpinion("Y kien moi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedRecord_shouldReturnFailure() {
        VbdenResDocument record = newRecord("vbden_res", "{}");
        record.markAsDeleted(null);
        when(vbdenResDocumentRepository.findById(record.getId())).thenReturn(Optional.of(record));

        var request = new SuaYKienVbdenRequest();
        request.setId(record.getId());
        request.setOpinion("Y kien moi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldReplaceOpinionOnlyAndKeepOtherFields() {
        VbdenResDocument record = newRecord("vbden_res",
            "{\"parentid\":\"p1\",\"action\":\"chuyenxuly\",\"opinion\":\"Y kien cu\"}");
        when(vbdenResDocumentRepository.findById(record.getId())).thenReturn(Optional.of(record));

        var request = new SuaYKienVbdenRequest();
        request.setId(record.getId());
        request.setOpinion("Y kien moi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(record.getDynamicFieldsJson());
        assertThat(updated.get("opinion")).isEqualTo("Y kien moi");
        assertThat(updated.get("parentid")).isEqualTo("p1");
        assertThat(updated.get("action")).isEqualTo("chuyenxuly");
        verify(vbdenResDocumentRepository).save(any());
        verify(documentLogService).logUpdate(any(), any(), any());
    }

    @Test
    void execute_withBlankOpinion_shouldClearField() {
        VbdenResDocument record = newRecord("vbden_res", "{\"opinion\":\"Y kien cu\"}");
        when(vbdenResDocumentRepository.findById(record.getId())).thenReturn(Optional.of(record));

        var request = new SuaYKienVbdenRequest();
        request.setId(record.getId());
        request.setOpinion(null);

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(record.getDynamicFieldsJson());
        assertThat(updated.get("opinion")).isEqualTo("");
    }

    private VbdenResDocument newRecord(String documentType, String dynamicFieldsJson) {
        VbdenResDocument record = new VbdenResDocument("Quá trình xử lý", documentType, "DRAFT", null, null, null, null);
        record.replaceDynamicFields(dynamicFieldsJson);
        return record;
    }
}
