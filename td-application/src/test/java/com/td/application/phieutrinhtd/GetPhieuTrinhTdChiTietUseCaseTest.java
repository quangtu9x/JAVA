package com.td.application.phieutrinhtd;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPhieuTrinhTdChiTietUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetPhieuTrinhTdChiTietUseCase useCase() {
        return new GetPhieuTrinhTdChiTietUseCase(
            phieuTrinhTdDocumentRepository, phieuTrinhTdProcessDocumentRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_withBlankId_shouldReturnFailure() {
        var request = new GetPhieuTrinhTdChiTietRequest();
        request.setId("");

        assertThat(useCase().execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withInvalidUuid_shouldReturnFailure() {
        var request = new GetPhieuTrinhTdChiTietRequest();
        request.setId("not-a-uuid");

        assertThat(useCase().execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.empty());
        var request = new GetPhieuTrinhTdChiTietRequest();
        request.setId(id.toString());

        assertThat(useCase().execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withExistingDocument_shouldReturnFieldsAndQuaTrinhHistory() {
        UUID id = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument("Phieu trinh A", PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1, null);
        document.replaceDynamicFields("{\"lanhdao1\":\"nguyenvana\"}");
        ReflectionTestUtils.setField(document, "id", id);
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        PhieuTrinhTdProcessDocument process = new PhieuTrinhTdProcessDocument("Quatrinh");
        process.replaceDynamicFields("{\"action_type\":\"5\"}");
        when(phieuTrinhTdProcessDocumentRepository.findByParentId(id)).thenReturn(List.of(process));

        var request = new GetPhieuTrinhTdChiTietRequest();
        request.setId(id.toString());

        var result = useCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("status")).isEqualTo(PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1);
        assertThat(data.get("lanhdao1")).isEqualTo("nguyenvana");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> quaTrinh = (List<Map<String, Object>>) data.get("quatrinh");
        assertThat(quaTrinh).hasSize(1);
        assertThat(quaTrinh.get(0).get("action_type")).isEqualTo("5");
    }
}
