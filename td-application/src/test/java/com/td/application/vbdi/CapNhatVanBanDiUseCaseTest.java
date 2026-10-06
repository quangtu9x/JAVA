package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.vbden.AddVbdenResDocumentUseCase;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 11/09/2026 — test {@link CapNhatVanBanDiUseCase}: xem javadoc lớp đó (port action Domino
 * {@code capnhatvanbandi}).
 */
@ExtendWith(MockitoExtension.class)
class CapNhatVanBanDiUseCaseTest {

    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatVanBanDiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatVanBanDiUseCase(
            vbdiDocumentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, resolveIdentifierByLevelUseCase,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
    }

    private VbdiDocument newVbdiDocument(String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        // 06/10/2026: "tra loi van ban den" doc tu bang document_links - seed lien ket tu documentin_obj
        // cua du lieu test (tuong duong migration V1.0.70 backfill; id khong phai UUID bi bo qua).
        com.td.application.documentlinks.RelatedDocumentSnapshots.toItems(
                new com.td.application.documents.DynamicDocumentFieldsService()
                    .parseDynamicFields(dynamicFieldsJson).get("documentin_obj"))
            .forEach(item -> com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository)
                .linkItem(com.td.domain.documentlinks.DocumentLinkType.VBDI_TRALOI_VBDEN, document.getId(), item));
        return document;
    }

    @Test
    void execute_withoutVbdiId_shouldReturnFailure() {
        var request = new CapNhatVanBanDiRequest();

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownVbdi_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdiId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedVbdi_shouldReturnFailure() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        vbdi.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withEmptyDocumentInObj_shouldNoOpAndStillSucceed() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verify(addVbdenResDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_withDominoLegacyUnidItem_shouldSkipWithoutCreatingRecord() {
        VbdiDocument vbdi = newVbdiDocument(
            "{\"documentin_obj\":[{\"id\":\"AB12CD34EF56AB12CD34EF56AB12CD34\",\"code\":\"OLD\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verify(addVbdenResDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_shouldCreateProcessingRecordForEachJavaNativeItem_withoutValidatingExistence() {
        UUID vbdenId1 = UUID.randomUUID();
        UUID vbdenId2 = UUID.randomUUID();
        VbdiDocument vbdi = newVbdiDocument("{\"code_full\":\"10/CV-VBDI\",\"promulgation_date\":\"01/09/2026\","
            + "\"documentin_obj\":[{\"id\":\"" + vbdenId1 + "\"},{\"id\":\"" + vbdenId2 + "\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());
        request.setCreatorName("Nguyen Van Thu");
        request.setPosition("Van thu");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase, times(2)).execute(captor.capture());
        var childRequest = captor.getAllValues().get(0);
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", vbdenId1.toString());
        assertThat(childRequest.getFields()).containsEntry("action", "capnhatvanbandi");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("creator", "Nguyen Van Thu");
        assertThat(childRequest.getFields()).containsEntry("position", "Van thu");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Cập nhật văn bản đi");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
        assertThat(childRequest.getFields().get("res_obj"))
            .contains(vbdi.getId().toString())
            .contains("10/CV-VBDI")
            .contains("01/09/2026");
    }

    @Test
    void execute_withCustomOpinion_shouldOverrideDefault() {
        UUID vbdenId = UUID.randomUUID();
        VbdiDocument vbdi = newVbdiDocument("{\"documentin_obj\":[{\"id\":\"" + vbdenId + "\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());
        request.setOpinion("Ý kiến tuỳ chỉnh");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("opinion", "Ý kiến tuỳ chỉnh");
    }

    @Test
    void execute_withMixedLegacyAndJavaNativeItems_shouldOnlyProcessJavaNative() {
        UUID vbdenId = UUID.randomUUID();
        VbdiDocument vbdi = newVbdiDocument("{\"documentin_obj\":["
            + "{\"id\":\"AB12CD34EF56AB12CD34EF56AB12CD34\"},"
            + "{\"id\":\"" + vbdenId + "\"}"
            + "]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase, times(1)).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("parentid", vbdenId.toString());
    }

    @Test
    void execute_withChildCreationFailure_shouldStillReturnSuccessAndProcessOthers() {
        UUID vbdenId1 = UUID.randomUUID();
        UUID vbdenId2 = UUID.randomUUID();
        VbdiDocument vbdi = newVbdiDocument(
            "{\"documentin_obj\":[{\"id\":\"" + vbdenId1 + "\"},{\"id\":\"" + vbdenId2 + "\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(addVbdenResDocumentUseCase.execute(any()))
            .thenReturn(Result.failure("loi gia lap"))
            .thenReturn(Result.success(UUID.randomUUID()));

        var request = new CapNhatVanBanDiRequest();
        request.setVbdiId(vbdi.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(addVbdenResDocumentUseCase, times(2)).execute(any());
    }
}
