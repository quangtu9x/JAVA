package com.td.application.hosocv;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserFullNameResolver;
import com.td.domain.hosocv.HoSoCVDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 02/10/2026 — {@link LuuVanBanKhacUseCase} ("văn bản khác" của hồ sơ, thay {@code qlvb/add} form
 * {@code other_related_document}).
 */
@ExtendWith(MockitoExtension.class)
class LuuVanBanKhacUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserFullNameResolver userFullNameResolver;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private LuuVanBanKhacUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LuuVanBanKhacUseCase(hoSoCVDocumentRepository, dynamicDocumentFieldsService,
            documentCacheService, documentLogService, currentUserProvider, userFullNameResolver);
    }

    @Test
    void create_withUnknownHoSo_shouldFailWithoutSaving() {
        UUID hosocvId = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(null, hosocvId, Map.of("subject", "Tờ trình")));

        assertThat(result.isSuccess()).isFalse();
        verify(hoSoCVDocumentRepository, never()).save(any());
    }

    @Test
    void create_shouldSaveOwnTypeLinkedToHoSoWithServerSideCreatorAndOnlyFormFields() {
        HoSoCVDocument hosocv = new HoSoCVDocument("Hồ sơ", "hosocv", "0", null, null, null, null);
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));
        when(userFullNameResolver.resolveForWrite("lpha", null)).thenReturn("Lê Phạm Hà");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", "12/TTr");
        fields.put("promulgation_date", "01/10/2026");
        fields.put("subject", "Tờ trình xin ý kiến");
        fields.put("creator", "Giả mạo");
        fields.put("document_profiles_id", UUID.randomUUID().toString());

        var result = useCase.execute(request(null, hosocv.getId(), fields));

        ArgumentCaptor<HoSoCVDocument> captor = ArgumentCaptor.forClass(HoSoCVDocument.class);
        verify(hoSoCVDocumentRepository).save(captor.capture());
        HoSoCVDocument saved = captor.getValue();
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(saved.getDynamicFieldsJson());
        assertThat(result.isSuccess()).isTrue();
        assertThat(saved.getDocumentType()).isEqualTo(LuuVanBanKhacUseCase.DOCUMENT_TYPE);
        assertThat(saved.getTitle()).isEqualTo("Tờ trình xin ý kiến");
        assertThat(savedFields)
            .containsEntry("form", "other_related_document")
            .containsEntry("document_profiles_id", hosocv.getId().toString())
            .containsEntry("code", "12/TTr")
            .containsEntry("promulgation_date", "01/10/2026")
            .containsEntry("user_creator", "lpha")
            .containsEntry("creator", "Lê Phạm Hà");
    }

    @Test
    void update_shouldMergeFormFieldsAndKeepLink() {
        UUID hosocvId = UUID.randomUUID();
        HoSoCVDocument existing = new HoSoCVDocument("Cũ", LuuVanBanKhacUseCase.DOCUMENT_TYPE, "1", null, null, null, null)
            .replaceDynamicFields("{\"form\":\"other_related_document\",\"document_profiles_id\":\""
                + hosocvId + "\",\"code\":\"1\",\"creator\":\"A\"}");
        when(hoSoCVDocumentRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(request(existing.getId(), null, Map.of("code", "2", "subject", "Mới")));

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(existing.getDynamicFieldsJson());
        assertThat(result.isSuccess()).isTrue();
        assertThat(existing.getTitle()).isEqualTo("Mới");
        assertThat(fields)
            .containsEntry("code", "2")
            .containsEntry("document_profiles_id", hosocvId.toString())
            .containsEntry("creator", "A");
    }

    @Test
    void update_withHoSoCongViecId_shouldFail() {
        HoSoCVDocument hosocv = new HoSoCVDocument("Hồ sơ", "hosocv", "0", null, null, null, null);
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));

        var result = useCase.execute(request(hosocv.getId(), null, Map.of("subject", "x")));

        assertThat(result.isSuccess()).isFalse();
        verify(hoSoCVDocumentRepository, never()).save(any());
    }

    private LuuVanBanKhacRequest request(UUID id, UUID hosocvId, Map<String, String> fields) {
        LuuVanBanKhacRequest request = new LuuVanBanKhacRequest();
        request.setId(id);
        request.setHosocvId(hosocvId);
        request.setFields(new LinkedHashMap<>(fields));
        return request;
    }
}
