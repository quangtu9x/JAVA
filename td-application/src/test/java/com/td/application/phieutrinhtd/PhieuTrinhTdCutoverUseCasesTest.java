package com.td.application.phieutrinhtd;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** 06/10/2026 - cutover FE phiếu trình: ChuyenTrangThai mở rộng (fields/người nhận/processId) + ChoYKien. */
@ExtendWith(MockitoExtension.class)
class PhieuTrinhTdCutoverUseCasesTest {

    @Mock
    private PhieuTrinhTdDocumentRepository documentRepository;
    @Mock
    private PhieuTrinhTdProcessDocumentRepository processRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService fields = new DynamicDocumentFieldsService();
    private PhieuTrinhTdDocument document;

    @BeforeEach
    void setUp() {
        document = new PhieuTrinhTdDocument("PT", "0", null)
            .replaceDynamicFields("{\"user_creator\":\"chuyenvien\",\"lanhdaovanphong\":\"Cu\"}");
        lenient().when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(processRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("chuyenvien"));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(any())).thenReturn(Optional.empty());
    }

    @Test
    void chuyenTrangThai_shouldMergeFieldsBeforeResolvingReceiver_protectStatus_andReturnProcessId() {
        var useCase = new ChuyenTrangThaiPhieuTrinhTdUseCase(
            documentRepository, processRepository, fields, currentUserProvider, appUserRepository, com.td.application.realtime.RealtimeNotifierTestSupport.noop());
        var request = new ChuyenTrangThaiPhieuTrinhTdRequest();
        request.setId(document.getId());
        request.setTargetStatus("2");
        request.setOpinion("Kinh trinh");
        request.setFields(Map.of(
            "lanhdaovanphong", "Chanh VP",
            "user_lanhdaovanphong", "chanhvp",
            "status", "7",
            "user_creator", "hacker"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().processId()).isNotNull();
        assertThat(document.getStatus()).isEqualTo("2");
        Map<String, Object> parent = fields.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(parent).containsEntry("user_lanhdaovanphong", "chanhvp")
            .containsEntry("user_creator", "chuyenvien")
            .containsEntry("ykientrinh2", "Kinh trinh");
        ArgumentCaptor<PhieuTrinhTdProcessDocument> captor = ArgumentCaptor.forClass(PhieuTrinhTdProcessDocument.class);
        verify(processRepository).save(captor.capture());
        Map<String, Object> log = fields.parseDynamicFields(captor.getValue().getDynamicFieldsJson());
        assertThat(log).containsEntry("user_receiver", "chanhvp").containsEntry("subject", "Kinh trinh")
            .containsEntry("action_type", "2");
    }

    @Test
    void choYKien_shouldWriteOpinionRowAndAddUserToCoordinatedOnce() {
        var useCase = new ChoYKienPhieuTrinhTdUseCase(
            documentRepository, processRepository, fields, currentUserProvider, appUserRepository);
        var request = new ChoYKienPhieuTrinhTdRequest();
        request.setId(document.getId());
        request.setSubject("Dong y");

        var first = useCase.execute(request);
        useCase.execute(request);

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getData().processId()).isNotNull();
        Map<String, Object> parent = fields.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(parent.get("user_coordinated")).isEqualTo(";chuyenvien;");
        ArgumentCaptor<PhieuTrinhTdProcessDocument> captor = ArgumentCaptor.forClass(PhieuTrinhTdProcessDocument.class);
        verify(processRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        Map<String, Object> opinion = fields.parseDynamicFields(captor.getValue().getDynamicFieldsJson());
        assertThat(opinion).containsEntry("form", "document_coordinated_opinion")
            .containsEntry("submission_dossier_id", document.getId().toString())
            .containsEntry("subject", "Dong y");
    }

    @Test
    void addMember_shouldKeepSemicolonFormat() {
        assertThat(ChoYKienPhieuTrinhTdUseCase.addMember("", "a")).isEqualTo(";a;");
        assertThat(ChoYKienPhieuTrinhTdUseCase.addMember(";a;", "b")).isEqualTo(";a;b;");
        assertThat(ChoYKienPhieuTrinhTdUseCase.addMember("a,b", "b")).isEqualTo("a,b");
    }
}
