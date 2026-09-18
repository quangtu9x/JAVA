package com.td.application.phieutrinhtd;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapNhatPhieuTrinhTdUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatPhieuTrinhTdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatPhieuTrinhTdUseCase(
            phieuTrinhTdDocumentRepository, dynamicDocumentFieldsService, authorAclFieldsService);
        lenient().when(phieuTrinhTdDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void execute_withoutId_shouldReturnFailure() {
        assertThat(useCase.execute(new LuuPhieuTrinhTdRequest()).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.empty());
        var request = new LuuPhieuTrinhTdRequest();
        request.setId(id);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withExistingDocument_shouldMergeDynamicFieldsWithoutLosingExistingOnes() {
        UUID id = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument("Phieu trinh A", PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null);
        document.replaceDynamicFields("{\"description\":\"cu\",\"lanhdao1\":\"nguyenvana\"}");
        ReflectionTestUtils.setField(document, "id", id);
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        var request = new LuuPhieuTrinhTdRequest();
        request.setId(id);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("description", "moi");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> merged = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(merged.get("description")).isEqualTo("moi");
        assertThat(merged.get("lanhdao1")).isEqualTo("nguyenvana");
    }
}
