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
 * Mở rộng BL-240 (HoSoCV Core) — G3: {@link TrinhDuyetHoSoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TrinhDuyetHoSoUseCaseTest {

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
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TrinhDuyetHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TrinhDuyetHoSoUseCase(
            hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService,
            documentLogService, currentUserProvider, addHoSoCVProcessDocumentUseCase, authorAclFieldsService);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("truongphong01"));
        lenient().when(addHoSoCVProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        assertThat(useCase.execute(new TrinhDuyetHoSoRequest()).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldSetTrinhChoDuyetAndOptionalApprover() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new TrinhDuyetHoSoRequest();
        request.setHosocvId(document.getId());
        request.setNguoiDuyet("giamdoc01");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("trinhlanhdaoduyet")).isEqualTo(HoSoCVLifecycleStatus.TRINH_CHO_DUYET);
        assertThat(fields.get("user_approved_by")).isEqualTo("giamdoc01");
    }

    @Test
    void execute_shouldAppendProcessRecordEvenIfItFailsInternally() {
        HoSoCVDocument document = newDocument(UUID.randomUUID(), "{}");
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(addHoSoCVProcessDocumentUseCase.execute(any())).thenThrow(new RuntimeException("boom"));

        var request = new TrinhDuyetHoSoRequest();
        request.setHosocvId(document.getId());

        // Loi ghi vet audit KHONG duoc lam hong thao tac trinh duyet chinh.
        var result = useCase.execute(request);
        assertThat(result.isSuccess()).isTrue();
    }

    private HoSoCVDocument newDocument(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
