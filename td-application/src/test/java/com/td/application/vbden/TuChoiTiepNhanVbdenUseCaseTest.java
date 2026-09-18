package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 07/09/2026 (2) — xem javadoc {@link TuChoiTiepNhanVbdenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TuChoiTiepNhanVbdenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TuChoiTiepNhanVbdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TuChoiTiepNhanVbdenUseCase(
            documentRepository, dynamicDocumentFieldsService, currentUserProvider,
            resolveIdentifierByLevelUseCase, documentLogService, documentCacheService,
            publishVbdiReplyStatusUseCase, authorAclFieldsService);
        org.mockito.Mockito.lenient().when(documentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BusinessDocument newDocument(String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", "vbden", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    @Test
    void execute_default_shouldMarkRefuseRemoveFromUnitProcessAndPublishStatus02() {
        BusinessDocument document = newDocument(
            "{\"send_log_id\":\"sl-1\",\"unit_process\":[\"H05.02\",\"H05.03\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.02"));

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());
        request.setOpinion("Khong dung tham quyen");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("is_document_refuse")).isEqualTo("1");
        assertThat(saved.get("nghiepvu")).isEqualTo("tuchoi");
        // Bugfix 07/09/2026 (5): "reply" field RIENG (khac is_document_refuse) - dieu kien loai tru
        // cua 4 view "Cho xu ly" (quamangnoibo/lienthong/dendientu/denquamang).
        assertThat(saved.get("reply")).isEqualTo("1");
        assertThat(saved.get("unit_process")).isEqualTo(List.of("H05.03"));
        assertThat(saved.get("unit_discard")).isEqualTo(List.of("H05.02"));
        assertThat(saved.get("user_discard")).isEqualTo(List.of("vanthu02"));
        // 09/09/2026 (2): khong co field "internal" (mac dinh khac "1", vd van ban denquamang) ->
        // Domino LUON set status="5" (khong dieu kien "don vi cuoi cung", chi ap dung khi internal=="1").
        assertThat(document.getStatus()).isEqualTo(VbdenStatusService.STATUS_TU_CHOI_TIEP_NHAN);

        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("02");
        assertThat(captor.getValue().getStatusLabel()).isEqualTo("Từ chối tiếp nhận");
        assertThat(captor.getValue().getStatusDetails()).isEqualTo("Khong dung tham quyen");
        assertThat(captor.getValue().getActorDisplayName()).isEqualTo("vanthu02");
    }

    /**
     * Bugfix 07/09/2026 (3, người dùng sửa lại: "Từ chối/Trả lại" PHẢI phản hồi trạng thái giống
     * 01/03) — type="tralai" đánh dấu is_document_reply, VÀ publish broker với statusCode phi chuẩn
     * "tralai" (KHÔNG gửi statusDetails — nội dung cố định dựng ở consumer, xem
     * VbdiReplyTaskHandler#buildContent).
     */
    @Test
    void execute_traLai_shouldMarkReplyAndPublishTraLaiStatus() {
        BusinessDocument document = newDocument("{\"unit_process\":[\"H05.02\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.02"));

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());
        request.setType("tralai");
        request.setOpinion("Sai doi tuong nhan");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("is_document_reply")).isEqualTo("1");
        assertThat(saved.get("nghiepvu")).isEqualTo("tralai");
        assertThat(saved.get("reply")).isEqualTo("1");
        // 09/09/2026 (2, xem javadoc lop - doi chieu lai ReplyDocumentOut.java that): khong co field
        // "internal" (mac dinh khac "1") -> Domino LUON set is_document_refuse+status="5" o day BAT
        // KE type - "tralai" van co the dong thoi mang ca is_document_reply LAN is_document_refuse.
        assertThat(saved.get("is_document_refuse")).isEqualTo("1");
        assertThat(document.getStatus()).isEqualTo(VbdenStatusService.STATUS_TU_CHOI_TIEP_NHAN);

        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("tralai");
        assertThat(captor.getValue().getStatusLabel()).isEqualTo("Trả lại");
        assertThat(captor.getValue().getStatusDetails()).isNull();
        assertThat(captor.getValue().getActorDisplayName()).isEqualTo("vanthu02");
    }

    /**
     * 09/09/2026 (2, van ban internal=="1" vd quamangnoibo - nhieu phong/ban CUNG 1 dong document):
     * con don vi khac dang xu ly (unit_process con lai sau khi loai identifier khong rong) -> CHUA
     * phai don vi cuoi cung -> KHONG force is_document_refuse/status="5", du type la gi.
     */
    @Test
    void execute_traLai_internalNotLastUnit_shouldNotForceRefuseOrStatus5() {
        BusinessDocument document = newDocument(
            "{\"internal\":\"1\",\"unit_process\":[\"H05.02\",\"H05.03\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.02"));

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());
        request.setType("tralai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("is_document_reply")).isEqualTo("1");
        assertThat(saved.get("is_document_refuse")).isNull();
        assertThat(saved.get("unit_process")).isEqualTo(List.of("H05.03"));
        assertThat(document.getStatus()).isEqualTo("2");
    }

    /**
     * 09/09/2026 (2): van ban internal=="1" NHUNG day la don vi CUOI CUNG con lai (unit_process rong
     * sau khi loai identifier, unit_process_registered cung rong) -> Domino VAN set
     * is_document_refuse+status="5" du type=="tralai" (dung is_document_reply DONG THOI voi
     * is_document_refuse).
     */
    @Test
    void execute_traLai_internalLastUnit_shouldForceRefuseAndStatus5() {
        BusinessDocument document = newDocument("{\"internal\":\"1\",\"unit_process\":[\"H05.02\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.02"));

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());
        request.setType("tralai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("is_document_reply")).isEqualTo("1");
        assertThat(saved.get("is_document_refuse")).isEqualTo("1");
        assertThat(saved.get("unit_process")).isEqualTo(List.of());
        assertThat(document.getStatus()).isEqualTo(VbdenStatusService.STATUS_TU_CHOI_TIEP_NHAN);
    }

    /**
     * 09/09/2026 (2): "tuchoi" (khong phai tralai) LUON set is_document_refuse (nhanh theo type,
     * khong dieu kien) - nhung khi internal=="1" VA con don vi khac dang xu ly, status VAN GIU
     * NGUYEN (chua phai don vi cuoi cung) - KHAC ban "don gian hoa" truoc do (09/09/2026, 1) tung set
     * status="5" vo dieu kien cho moi truong hop "tuchoi".
     */
    @Test
    void execute_tuchoi_internalNotLastUnit_shouldMarkRefuseFieldButKeepStatus() {
        BusinessDocument document = newDocument(
            "{\"internal\":\"1\",\"unit_process\":[\"H05.02\",\"H05.03\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.02"));

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("is_document_refuse")).isEqualTo("1");
        assertThat(saved.get("unit_process")).isEqualTo(List.of("H05.03"));
        assertThat(document.getStatus()).isEqualTo("2");
    }

    @Test
    void execute_unknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(id);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verifyNoInteractions(publishVbdiReplyStatusUseCase);
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument document = newDocument("{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    /** Khong co identifier (khong resolve duoc) - van phai thanh cong, chi bo qua buoc loai khoi unit_process. */
    @Test
    void execute_withoutIdentifier_shouldStillSucceedWithoutTouchingUnitProcess() {
        BusinessDocument document = newDocument("{\"unit_process\":[\"H05.02\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var request = new TuChoiTiepNhanVbdenRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("unit_process")).isEqualTo(List.of("H05.02"));
        assertThat(saved.get("unit_discard")).isNull();
        assertThat(saved.get("user_discard")).isEqualTo(List.of("vanthu02"));
    }
}
