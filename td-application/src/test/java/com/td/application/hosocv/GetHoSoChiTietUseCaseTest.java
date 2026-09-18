package com.td.application.hosocv;

import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.hosocv.HoSoCVProcessDocument;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Mở rộng BL-240 (HoSoCV Core) — G1: {@link GetHoSoChiTietUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetHoSoChiTietUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private HoSoCVProcessDocumentRepository hoSoCVProcessDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GetHoSoChiTietUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetHoSoChiTietUseCase(
            hoSoCVDocumentRepository, hoSoCVProcessDocumentRepository, documentRepository,
            duThaoDocumentRepository, vbdiDocumentRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_withBlankId_shouldReturnFailure() {
        var result = useCase.execute(new GetHoSoChiTietRequest());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(id)).thenReturn(Optional.empty());
        var request = new GetHoSoChiTietRequest();
        request.setId(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldResolveLinkedVanBanThroughCorrectRepositoryAndIncludeProcessHistory() {
        UUID id = UUID.randomUUID();
        UUID duThaoId = UUID.randomUUID();
        UUID vbdiId = UUID.randomUUID();
        UUID vbdenId = UUID.randomUUID();
        HoSoCVDocument hosocv = newHoSoCVDocument(id, "hosocv",
            "{\"duthao_attached_id\":\"" + duThaoId + "\",\"document_out_attached_id\":\"" + vbdiId
                + "\",\"document_in_attached_id\":\"" + vbdenId + "\"}");
        when(hoSoCVDocumentRepository.findById(id)).thenReturn(Optional.of(hosocv));
        when(duThaoDocumentRepository.findById(duThaoId))
            .thenReturn(Optional.of(newDuThaoDocument(duThaoId, "duthao", "{}")));
        when(vbdiDocumentRepository.findById(vbdiId))
            .thenReturn(Optional.of(newVbdiDocument(vbdiId, "vbdi", "{}")));
        when(documentRepository.findById(vbdenId))
            .thenReturn(Optional.of(newBusinessDocument(vbdenId, "vbden", "{}")));
        var process = newProcessDocument(UUID.randomUUID(), "{\"hosocv_id\":\"" + id + "\",\"process_type\":\"trinhduyet\"}");
        when(hoSoCVProcessDocumentRepository.findByHoSoCVId(id)).thenReturn(List.of(process));

        var request = new GetHoSoChiTietRequest();
        request.setId(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vanBanLienQuan = (List<Map<String, Object>>) result.getData().get("van_ban_lien_quan");
        assertThat(vanBanLienQuan).hasSize(3);
        assertThat(vanBanLienQuan).anySatisfy(item -> assertThat(item.get("databasetype")).isEqualTo("duthao"));
        assertThat(vanBanLienQuan).anySatisfy(item -> assertThat(item.get("databasetype")).isEqualTo("vbdi"));
        assertThat(vanBanLienQuan).anySatisfy(item -> assertThat(item.get("databasetype")).isEqualTo("vbden"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lichSu = (List<Map<String, Object>>) result.getData().get("lich_su_xu_ly");
        assertThat(lichSu).hasSize(1);
        assertThat(lichSu.get(0).get("process_type")).isEqualTo("trinhduyet");
    }

    private HoSoCVDocument newHoSoCVDocument(UUID id, String documentType, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private DuThaoDocument newDuThaoDocument(UUID id, String documentType, String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private VbdiDocument newVbdiDocument(UUID id, String documentType, String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private BusinessDocument newBusinessDocument(UUID id, String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private HoSoCVProcessDocument newProcessDocument(UUID id, String dynamicFieldsJson) {
        HoSoCVProcessDocument document = new HoSoCVProcessDocument(
            "Vet xu ly", "hosocv_process", null, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
