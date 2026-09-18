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
 * Mở rộng BL-240 (HoSoCV Core) — G3: {@link DuyetHoSoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class DuyetHoSoUseCaseTest {

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
    private DuyetHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DuyetHoSoUseCase(
            hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService,
            documentLogService, currentUserProvider, addHoSoCVProcessDocumentUseCase);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("giamdoc01"));
        lenient().when(addHoSoCVProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_whenNotPending_shouldReturnFailure() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new DuyetHoSoRequest();
        request.setHosocvId(document.getId());
        request.setDuyet(true);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_duyetTrue_shouldSetTrinhDaDuyet() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{\"trinhlanhdaoduyet\":\"2\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new DuyetHoSoRequest();
        request.setHosocvId(document.getId());
        request.setDuyet(true);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("trinhlanhdaoduyet")).isEqualTo(HoSoCVLifecycleStatus.TRINH_DA_DUYET);
    }

    @Test
    void execute_duyetFalse_shouldSetTrinhKhongDuocDuyet() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{\"trinhlanhdaoduyet\":\"2\"}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new DuyetHoSoRequest();
        request.setHosocvId(document.getId());
        request.setDuyet(false);
        request.setYKien("Thieu ho so chung minh");

        useCase.execute(request);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("trinhlanhdaoduyet")).isEqualTo(HoSoCVLifecycleStatus.TRINH_KHONG_DUOC_DUYET);
    }

    private HoSoCVDocument newDocument(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
