package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.hosocv.AddHoSoCVProcessDocumentUseCase;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Port module "Hồ sơ lưu trữ": {@link TiepNhanHoSoLuuTruUseCase} — bước 2/3 (nhánh tiếp nhận) của cơ
 * chế đồng bộ {@code hosoluutru} → {@code hosocv} (xem docs/analysis/16-...md mục 5.2/5.6).
 */
@ExtendWith(MockitoExtension.class)
class TiepNhanHoSoLuuTruUseCaseTest {

    @Mock
    private HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TiepNhanHoSoLuuTruUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TiepNhanHoSoLuuTruUseCase(
            hoSoLuuTruDocumentRepository, hoSoCVDocumentRepository, dynamicDocumentFieldsService,
            currentUserProvider, addHoSoCVProcessDocumentUseCase);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("kholuutru01"));
        lenient().when(hoSoLuuTruDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(hoSoCVDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(addHoSoCVProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_whenNotChoTiepNhan_shouldReturnFailure() {
        HoSoLuuTruDocument document = newDocument(UUID.randomUUID(), "hoso", "{}");
        when(hoSoLuuTruDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThat(useCase.execute(request(document.getId())).isSuccess()).isFalse();
    }

    @Test
    void execute_whenChoTiepNhan_shouldSetFormHosoAndSyncStoragedOnHoSoCV() {
        UUID hosocvId = UUID.randomUUID();
        HoSoLuuTruDocument document = newDocument(
            UUID.randomUUID(), "hoso_chuaduyet", "{\"document_profiles_id\":\"" + hosocvId + "\"}");
        when(hoSoLuuTruDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        HoSoCVDocument hosocv = newHosoCV(hosocvId, "{}");
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.of(hosocv));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo("hoso");
        Map<String, Object> hosocvFields = dynamicDocumentFieldsService.parseDynamicFields(hosocv.getDynamicFieldsJson());
        assertThat(hosocvFields.get("storaged")).isEqualTo("1");
    }

    private TiepNhanHoSoLuuTruRequest request(UUID id) {
        var request = new TiepNhanHoSoLuuTruRequest();
        request.setHosoLuuTruId(id);
        return request;
    }

    private HoSoLuuTruDocument newDocument(UUID id, String status, String dynamicFieldsJson) {
        HoSoLuuTruDocument document = new HoSoLuuTruDocument("Ho so luu tru test", "hosoluutru", status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private HoSoCVDocument newHosoCV(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Ho so cong viec test", "hosocv", "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
