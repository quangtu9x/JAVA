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
 * Mở rộng BL-240 (HoSoCV Core) — G3: {@link KetThucHoSoUseCase}. Nhận danh sách id (đơn/nhiều).
 */
@ExtendWith(MockitoExtension.class)
class KetThucHoSoUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private KetThucHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KetThucHoSoUseCase(hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
    }

    @Test
    void execute_withEmptyList_shouldReturnFailure() {
        assertThat(useCase.execute(new KetThucHoSoRequest()).isSuccess()).isFalse();
    }

    @Test
    void execute_alreadyEnded_shouldBeSkippedAndReturnFailureIfNoneApply() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), HoSoCVStatus.DA_KET_THUC);
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new KetThucHoSoRequest();
        request.setHosocvIds(List.of(document.getId()));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldSetStatusEndedWithEndDateAndSummary() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), HoSoCVStatus.DANG_XU_LY);
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new KetThucHoSoRequest();
        request.setHosocvIds(List.of(document.getId()));
        request.setSummary("Da xu ly xong don thu");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(HoSoCVStatus.DA_KET_THUC);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("end_date")).isNotNull();
        assertThat(fields.get("summary")).isEqualTo("Da xu ly xong don thu");
    }

    private HoSoCVDocument newDocument(UUID id, String status) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", status, null, null, null, null);
        document.replaceDynamicFields("{}");
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
