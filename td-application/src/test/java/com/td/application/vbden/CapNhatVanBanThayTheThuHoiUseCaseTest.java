package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.vbdi.PublishVbdiReplyStatusRequest;
import com.td.application.vbdi.PublishVbdiReplyStatusUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 13/09/2026 — xem javadoc {@link CapNhatVanBanThayTheThuHoiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatVanBanThayTheThuHoiUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private CopyDocumentFilesUseCase copyDocumentFilesUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatVanBanThayTheThuHoiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatVanBanThayTheThuHoiUseCase(
            documentRepository, dynamicDocumentFieldsService, currentUserProvider,
            resolveIdentifierByLevelUseCase, copyDocumentFilesUseCase, documentLogService,
            documentCacheService, publishVbdiReplyStatusUseCase);
        org.mockito.Mockito.lenient().when(documentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BusinessDocument newDocument(String status, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", "vbden", status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    @Test
    void execute_docRevokeStatusMoi_shouldSoftDeleteDocRevoke() {
        BusinessDocument docRevoke = newDocument("0", "{}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"4\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}]}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(docRevoke.isDeleted()).isTrue();
        Map<String, Object> revokeFields = dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson());
        assertThat(revokeFields.get("isRevoke")).isEqualTo("1");
        assertThat(revokeFields.get("nghiepvu")).isEqualTo("thuhoi|thaythe");
        assertThat(revokeFields.get("reply")).isEqualTo("1");
        // C khong bi xoa, chi danh dau reply=1 (phan duoi chung).
        assertThat(documentC.isDeleted()).isFalse();
        Map<String, Object> cFields = dynamicDocumentFieldsService.parseDynamicFields(documentC.getDynamicFieldsJson());
        assertThat(cFields.get("reply")).isEqualTo("1");
        verifyNoInteractions(copyDocumentFilesUseCase);

        ArgumentCaptor<PublishVbdiReplyStatusRequest> captor = ArgumentCaptor.forClass(PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("03");
        assertThat(captor.getValue().getStatusLabel()).isEqualTo("Đã tiếp nhận");
        assertThat(captor.getValue().getActorDisplayName()).isEqualTo("vanthu01");
    }

    @Test
    void execute_docRevokeAlreadyProcessing_shouldMergeContentAndFilesInsteadOfDeleting() {
        BusinessDocument docRevoke = newDocument("2", "{\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"1\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}],"
                + "\"subject\":\"Van ban thay the\",\"signer\":\"Nguyen Van A\"}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.success(2));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(docRevoke.isDeleted()).isFalse();
        Map<String, Object> revokeFields = dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson());
        assertThat(revokeFields.get("subject")).isEqualTo("Van ban thay the");
        assertThat(revokeFields.get("signer")).isEqualTo("Nguyen Van A");
        // status entity cua docRevoke KHONG bi dung - van "2" (dang xu ly do, giu nguyen).
        assertThat(docRevoke.getStatus()).isEqualTo("2");
        assertThat(documentC.getStatus()).isEqualTo(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY);
        Map<String, Object> cFields = dynamicDocumentFieldsService.parseDynamicFields(documentC.getDynamicFieldsJson());
        assertThat((String) cFields.get("subject")).contains("đã chấp nhận thay thế thu hồi văn bản");
        assertThat(cFields.get("reply")).isEqualTo("1");
        verify(copyDocumentFilesUseCase).execute(any());
        verify(publishVbdiReplyStatusUseCase).execute(any());
    }

    @Test
    void execute_capNhat_docRevokeBussinessDocTypeMoi_shouldMergeAndDeleteC() {
        // Bugfix 13/09/2026 (bao loi nguoi dung "gui lai van ban... phai la van ban cap nhat"):
        // nghiepvu=="2" (gui lai), docRevoke.bussiness_doc_type=="0" (con "thuan") -> merge noi
        // dung+tep TU C VAO docRevoke (docRevoke SONG), roi HUY C (khac han nhanh "1"/"4").
        BusinessDocument docRevoke = newDocument("2", "{\"bussiness_doc_type\":\"0\",\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}],"
                + "\"subject\":\"Van ban moi\",\"tepbanhanh\":\"vanban.pdf\"}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.success(1));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(docRevoke.isDeleted()).isFalse();
        Map<String, Object> revokeFields = dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson());
        assertThat(revokeFields.get("subject")).isEqualTo("Van ban moi");
        assertThat(revokeFields.get("tepbanhanh")).isEqualTo("vanban.pdf");
        // docRevoke.status entity KHONG bi dung.
        assertThat(docRevoke.getStatus()).isEqualTo("2");
        // C bi huy (khac han nhanh "1"/"4" giu C song).
        assertThat(documentC.isDeleted()).isTrue();
        Map<String, Object> cFields = dynamicDocumentFieldsService.parseDynamicFields(documentC.getDynamicFieldsJson());
        assertThat(cFields.get("nghiepvu")).isEqualTo("capnhat");
        assertThat(cFields.get("reply")).isEqualTo("1");
        assertThat(cFields.get("vbden_dacapnhat_obj")).isInstanceOf(java.util.List.class);
        verify(copyDocumentFilesUseCase).execute(any());
        verify(publishVbdiReplyStatusUseCase).execute(any());
    }

    @Test
    void execute_capNhat_docRevokeAlreadyBussinessDocType_shouldSkipEntirely() {
        // Domino: khoi nghiepvu=="2" CHI xu ly khi docRevoke.bussiness_doc_type=="0" - khac gia tri
        // do (vd chinh no cung dang la muc tieu "1") thi KHONG LAM GI, khong huy C.
        BusinessDocument docRevoke = newDocument("2", "{\"bussiness_doc_type\":\"1\",\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}]}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(docRevoke.isDeleted()).isFalse();
        Map<String, Object> revokeFields = dynamicDocumentFieldsService.parseDynamicFields(docRevoke.getDynamicFieldsJson());
        assertThat(revokeFields.get("subject")).isEqualTo("Van ban cu"); // KHONG bi ghi de
        assertThat(documentC.isDeleted()).isFalse(); // KHONG bi huy
        verifyNoInteractions(copyDocumentFilesUseCase);
        verify(publishVbdiReplyStatusUseCase).execute(any());
    }

    @Test
    void execute_noRevokeTarget_shouldStillPublishStatus03AndMarkReply() {
        BusinessDocument documentC = newDocument("0", "{\"bussiness_doc_type\":\"4\"}");
        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> cFields = dynamicDocumentFieldsService.parseDynamicFields(documentC.getDynamicFieldsJson());
        assertThat(cFields.get("reply")).isEqualTo("1");
        verifyNoInteractions(copyDocumentFilesUseCase);
        verify(publishVbdiReplyStatusUseCase).execute(any());
    }

    @Test
    void execute_documentNotFound_shouldFail() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(publishVbdiReplyStatusUseCase);
    }

    @Test
    void execute_missingId_shouldFail() {
        var request = new CapNhatVanBanThayTheThuHoiRequest();

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(documentRepository);
    }
}
