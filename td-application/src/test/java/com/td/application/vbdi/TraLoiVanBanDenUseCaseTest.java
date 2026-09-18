package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.vbden.AddVbdenResDocumentUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 09/09/2026 — test {@link TraLoiVanBanDenUseCase}: xem javadoc lớp đó (port
 * {@code GanVanBanModal.jsx#btnActionTraLoiVanBanDen} + Domino {@code CapNhatVanBanDen.java#
 * taoduthao_hoso_phieutrinh("ganduthaotraloi")}).
 */
@ExtendWith(MockitoExtension.class)
class TraLoiVanBanDenUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TraLoiVanBanDenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TraLoiVanBanDenUseCase(
            vbdiDocumentRepository, documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, resolveIdentifierByLevelUseCase,
            authorAclFieldsService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
    }

    private VbdiDocument newVbdiDocument(String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private BusinessDocument newVbdenDocument(String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban den", "vbden", "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private Map<String, Object> documentInItem(UUID id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id.toString());
        item.put("code", "10/CV-VBDEN");
        item.put("subject", "Trich yeu van ban den");
        item.put("unit_publish", "So Noi vu");
        item.put("promulgation_date", "01/09/2026");
        return item;
    }

    @Test
    void execute_withoutVbdiId_shouldReturnFailure() {
        var request = new TraLoiVanBanDenRequest();
        request.setDocumentIn(List.of(documentInItem(UUID.randomUUID())));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutDocumentIn_shouldReturnFailure() {
        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(UUID.randomUUID());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownVbdi_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdiId);
        request.setDocumentIn(List.of(documentInItem(UUID.randomUUID())));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(UUID.randomUUID())));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldAppendDocumentInObjOnVbdi() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        UUID vbdenId = UUID.randomUUID();
        BusinessDocument vbden = newVbdenDocument("{}");
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.empty());

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        var item = documentInItem(vbdenId);
        request.setDocumentIn(List.of(item));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> documentIn = (List<Object>) updated.get("documentin_obj");
        assertThat(documentIn).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> savedItem = (Map<String, Object>) documentIn.get(0);
        assertThat(savedItem).containsEntry("id", vbdenId.toString()).containsEntry("code", "10/CV-VBDEN");
    }

    @Test
    void execute_withAlreadyLinkedDocument_shouldNotDuplicateInDocumentInObj() {
        UUID vbdenId = UUID.randomUUID();
        VbdiDocument vbdi = newVbdiDocument("{\"documentin_obj\":[{\"id\":\"" + vbdenId + "\",\"code\":\"OLD\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(vbdenId)));

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> documentIn = (List<Object>) updated.get("documentin_obj");
        assertThat(documentIn).hasSize(1);
    }

    @Test
    void execute_withInvalidDocumentInId_shouldReturnFailureWithoutMutating() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        Map<String, Object> badItem = new LinkedHashMap<>();
        badItem.put("id", "not-a-uuid");

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(badItem));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verify(vbdiDocumentRepository, never()).save(any());
    }

    // Domino taoduthao_hoso_phieutrinh(): user_process khac rong -> chuyen actingUsername tu
    // user_process/user_returned sang user_processing.
    @Test
    void execute_withActingUsernameInUserProcess_shouldMoveToUserProcessing() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        UUID vbdenId = UUID.randomUUID();
        BusinessDocument vbden = newVbdenDocument("{"
            + "\"user_process\":[\"vanthu01\",\"nguyenvanb\"],"
            + "\"user_returned\":[\"vanthu01\"]"
            + "}");
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(vbden));

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(vbdenId)));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbden.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updated.get("user_returned")).isEqualTo(List.of());
        assertThat(updated.get("user_processing")).isEqualTo(List.of("vanthu01"));
    }

    // Domino: user_process RONG -> KHONG mutate van ban den (khong luu), nhung VAN tao ban ghi
    // vbden_res (CreateOrUpdateDocument chay TRUOC dieu kien nay, khong bi anh huong).
    @Test
    void execute_withEmptyUserProcess_shouldNotMutateVbdenButStillCreateProcessingRecord() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        UUID vbdenId = UUID.randomUUID();
        BusinessDocument vbden = newVbdenDocument("{}");
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(vbden));

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(vbdenId)));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase, times(1)).execute(any());
    }

    @Test
    void execute_withUnknownVbdenTarget_shouldSkipItButStillSucceedAndLogProcessingRecord() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        UUID vbdenId = UUID.randomUUID();
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.empty());

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(vbdenId)));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase, times(1)).execute(any());
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionAndVbdiResObj() {
        VbdiDocument vbdi = newVbdiDocument("{\"code_full\":\"10/CV-VBDI\"}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        UUID vbdenId = UUID.randomUUID();
        BusinessDocument vbden = newVbdenDocument("{}");
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(vbden));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(vbdenId)));
        request.setCreatorName("Nguyen Van Thu");
        request.setPosition("Van thu");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", vbdenId.toString());
        assertThat(childRequest.getFields()).containsEntry("action", "ganduthaotraloi");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("creator", "Nguyen Van Thu");
        assertThat(childRequest.getFields()).containsEntry("position", "Van thu");
        assertThat(childRequest.getFields()).containsEntry("opinion", "10/CV-VBDI");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
        assertThat(childRequest.getFields().get("res_obj"))
            .contains(vbdi.getId().toString())
            .contains("10/CV-VBDI");
    }

    @Test
    void execute_withMultipleDocumentIn_shouldProcessEachIndependently() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        UUID vbdenId1 = UUID.randomUUID();
        UUID vbdenId2 = UUID.randomUUID();
        BusinessDocument vbden1 = newVbdenDocument("{\"user_process\":[\"vanthu01\"]}");
        BusinessDocument vbden2 = newVbdenDocument("{}");
        when(documentRepository.findById(vbdenId1)).thenReturn(Optional.of(vbden1));
        when(documentRepository.findById(vbdenId2)).thenReturn(Optional.of(vbden2));

        var request = new TraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentIn(List.of(documentInItem(vbdenId1), documentInItem(vbdenId2)));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> documentIn = (List<Object>) updated.get("documentin_obj");
        assertThat(documentIn).hasSize(2);
        verify(documentRepository, times(1)).save(vbden1);
        verify(documentRepository, never()).save(vbden2);
        verify(addVbdenResDocumentUseCase, times(2)).execute(any());
    }
}
