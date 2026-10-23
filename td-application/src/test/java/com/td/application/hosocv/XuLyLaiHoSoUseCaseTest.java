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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3/G9: {@link XuLyLaiHoSoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class XuLyLaiHoSoUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private XuLyLaiHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XuLyLaiHoSoUseCase(hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
    }

    @Test
    void execute_whenNotEnded_shouldReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{\"trinhlanhdaoduyet\":\"3\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyLaiHoSoRequest();
        request.setHosocvIds(List.of(document.getId()));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_whenRejected_shouldResetTrinhLanhDaoDuyet() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(),
            "{\"trinhlanhdaoduyet\":\"3\",\"end_date\":\"12/10/2026\",\"summary\":\"Da xong\"}");
        document.update(null, null, HoSoCVStatus.DA_KET_THUC, null, null, null, null);
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new XuLyLaiHoSoRequest();
        request.setHosocvIds(List.of(document.getId()));
        request.setLyDo("Bo sung chung tu");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(document.getStatus()).isEqualTo(HoSoCVStatus.DANG_XU_LY);
        assertThat(fields.get("trinhlanhdaoduyet")).isEqualTo(HoSoCVLifecycleStatus.TRINH_CHUA_TRINH);
        assertThat(fields).doesNotContainKeys("end_date", "summary");
        assertThat(fields.get("xulylai_lydo")).isEqualTo("Bo sung chung tu");
    }

    @Test
    void execute_whenEndedWithoutRejection_shouldReopenForProcessing() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(),
            "{\"trinhlanhdaoduyet\":\"1\",\"end_date\":\"12/10/2026\",\"summary\":\"Da xong\"}");
        document.update(null, null, HoSoCVStatus.DA_KET_THUC, null, null, null, null);
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new XuLyLaiHoSoRequest();
        request.setHosocvIds(List.of(document.getId()));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(HoSoCVStatus.DANG_XU_LY);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("trinhlanhdaoduyet", "1");
        assertThat(fields).doesNotContainKeys("end_date", "summary");
    }

    private HoSoCVDocument newDocument(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
