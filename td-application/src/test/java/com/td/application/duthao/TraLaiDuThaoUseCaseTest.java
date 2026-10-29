package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.duthao.DraftProcessDocument;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): test {@link TraLaiDuThaoSupport} qua 2 use case công khai — đối chiếu
 * {@code XuLyDuThao#TraLaiDuThaoChoLanhDaoKyDuyet()}/{@code TraLaiDuThaoChoNguoiSoanThao()}.
 */
@ExtendWith(MockitoExtension.class)
class TraLaiDuThaoUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DraftProcessDocumentRepository draftProcessDocumentRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TraLaiDuThaoSupport support;

    @BeforeEach
    void setUp() {
        support = new TraLaiDuThaoSupport(
            vbdiDocumentRepository, duThaoDocumentRepository, dynamicDocumentFieldsService,
            addDraftProcessDocumentUseCase, currentUserProvider, documentLogService, documentCacheService,
            new DraftProcessActorFields(org.mockito.Mockito.mock(com.td.application.sharedcore.AppUserRepository.class)),
            draftProcessDocumentRepository, com.td.application.realtime.RealtimeNotifierTestSupport.noop());
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu1"));
        lenient().when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(duThaoDocumentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private VbdiDocument newVbdi(UUID draftId) {
        VbdiDocument vbdi = new VbdiDocument("Cong van", "vbdi", VbdiStatus.PENDING_SIGNATURE, null, null, null, null);
        vbdi.replaceDynamicFields("{\"draft_id\":\"" + draftId + "\"}");
        return vbdi;
    }

    private DuThaoDocument newDraft(String currentStatus, String signer, String userSigner,
                                     String creator, String userCreator, String documentOutId) {
        DuThaoDocument draft = new DuThaoDocument("Du thao", "duthao", currentStatus, null, null, null, null);
        draft.replaceDynamicFields(
            "{\"signer\":\"" + signer + "\",\"user_signer\":\"" + userSigner + "\","
                + "\"creator\":\"" + creator + "\",\"user_creator\":\"" + userCreator + "\","
                + "\"document_out_id\":\"" + documentOutId + "\"}");
        return draft;
    }

    @Test
    void chuaLanhDao_shouldSetPendingSignatureOnDraftAndReturnedByClerkOnVbdi() {
        DuThaoDocument draft = newDraft(DuThaoStatus.SIGNED_WAITING_SUBMIT, "Nguyen Van Lanh Dao", "lanhdao1",
            "Tran Van Chuyen Vien", "chuyenvien1", "vbdi-old-id");
        VbdiDocument vbdi = newVbdi(draft.getId());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdi.getId());
        request.setDraftId(draft.getId());
        request.setOpinion("Sai the thuc");

        Result<UUID> result = support.execute(request, TraLaiDuThaoTarget.LANH_DAO);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.PENDING_SIGNATURE);
        assertThat(vbdi.getStatus()).isEqualTo(VbdiStatus.RETURNED_BY_CLERK);

        Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(draftFields).containsEntry("return_document_out_for", "1");
        assertThat(draftFields).containsEntry("return_document_out_for_user", "lanhdao1");
        assertThat(draftFields).containsEntry("status_pre", DuThaoStatus.SIGNED_WAITING_SUBMIT);
        assertThat(draftFields).containsEntry("draft_end", "0");
        assertThat(draftFields).containsEntry("return_document_out_id", "vbdi-old-id");
        assertThat(draftFields).doesNotContainKey("document_out_id");

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        assertThat(vbdiFields).containsEntry("creator_opinion", "Sai the thuc");
        assertThat(String.valueOf(vbdiFields.get("log_giveback"))).contains("Sai the thuc");

        verify(addDraftProcessDocumentUseCase).execute(any());
    }

    @Test
    void traLaiNguoiSoanThao_shouldSetReturnedOnDraft() {
        DuThaoDocument draft = newDraft(DuThaoStatus.PENDING_SIGNATURE, "Nguyen Van Lanh Dao", "lanhdao1",
            "Tran Van Chuyen Vien", "chuyenvien1", "");
        VbdiDocument vbdi = newVbdi(draft.getId());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdi.getId());
        request.setDraftId(draft.getId());
        // opinion rong -> tu sinh chuoi mac dinh (khop Domino).

        Result<UUID> result = support.execute(request, TraLaiDuThaoTarget.NGUOI_SOAN_THAO);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.RETURNED);
        Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(draftFields).containsEntry("return_document_out_for", "2");
        assertThat(draftFields).containsEntry("return_document_out_for_user", "chuyenvien1");

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        assertThat(String.valueOf(vbdiFields.get("creator_opinion"))).startsWith("Văn thư trả lại lúc");
    }

    @Test
    void execute_whenDraftIdMissingFromRequest_shouldFallbackToVbdiDraftIdField() {
        DuThaoDocument draft = newDraft(DuThaoStatus.PENDING_SIGNATURE, "s", "us", "c", "uc", "");
        VbdiDocument vbdi = newVbdi(draft.getId());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdi.getId());
        // draftId khong truyen - phai tu doc draft_id tren vbdi.

        Result<UUID> result = support.execute(request, TraLaiDuThaoTarget.LANH_DAO);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(draft.getId());
    }

    private DraftProcessDocument newProcess(UUID draftId, String actionType, String userCreator, String creator) {
        DraftProcessDocument process = new DraftProcessDocument("p", "draft_process", null, null, null, null, null);
        String userCreatorJson = userCreator == null ? "" : ",\"user_creator\":\"" + userCreator + "\"";
        process.replaceDynamicFields("{\"parentid\":\"" + draftId + "\",\"action_type\":\"" + actionType + "\","
            + "\"creator\":\"" + creator + "\"" + userCreatorJson + "}");
        return process;
    }

    @Test
    void traLaiNguoiTrinh_shouldReturnToLatestChuyenKyDuyetActor() {
        DuThaoDocument draft = newDraft(DuThaoStatus.PENDING_SIGNATURE, "Lanh Dao", "lanhdao1", "Chuyen Vien", "chuyenvien1", "");
        VbdiDocument vbdi = newVbdi(draft.getId());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        // Moi nhat truoc: vet chuyenvanthu (lanh dao) roi chuyenkyduyet (truong phong) roi chuyenkyduyet cu.
        when(draftProcessDocumentRepository.findByParentId(eq(draft.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(
                newProcess(draft.getId(), "chuyenvanthu", "lanhdao1", "Lanh Dao"),
                newProcess(draft.getId(), "chuyenkyduyet", "truongphong1", "Truong Phong"),
                newProcess(draft.getId(), "chuyenkyduyet", "chuyenvien1", "Chuyen Vien"))));

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdi.getId());
        request.setDraftId(draft.getId());

        Result<UUID> result = support.execute(request, TraLaiDuThaoTarget.NGUOI_TRINH);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.RETURNED);
        Map<String, Object> draftFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(draftFields).containsEntry("return_document_out_for", "3");
        assertThat(draftFields).containsEntry("return_document_out_for_user", "truongphong1");
        assertThat(vbdi.getStatus()).isEqualTo(VbdiStatus.RETURNED_BY_CLERK);
    }

    @Test
    void traLaiNguoiTrinh_legacyProcessWithoutUserCreator_shouldUseCreatorAsUsername() {
        DuThaoDocument draft = newDraft(DuThaoStatus.PENDING_SIGNATURE, "s", "us", "c", "uc", "");
        VbdiDocument vbdi = newVbdi(draft.getId());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(draftProcessDocumentRepository.findByParentId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(newProcess(draft.getId(), "chuyenkyduyet", null, "nguoitrinh1"))));

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdi.getId());

        assertThat(support.execute(request, TraLaiDuThaoTarget.NGUOI_TRINH).isSuccess()).isTrue();
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()))
            .containsEntry("return_document_out_for_user", "nguoitrinh1");
    }

    @Test
    void traLaiNguoiTrinh_whenNoSubmitTrail_shouldFailWithoutMutating() {
        DuThaoDocument draft = newDraft(DuThaoStatus.PENDING_SIGNATURE, "s", "us", "c", "uc", "");
        VbdiDocument vbdi = newVbdi(draft.getId());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(draftProcessDocumentRepository.findByParentId(any(), any())).thenReturn(new PageImpl<>(List.of()));

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdi.getId());

        assertThat(support.execute(request, TraLaiDuThaoTarget.NGUOI_TRINH).isSuccess()).isFalse();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.PENDING_SIGNATURE);
        verify(duThaoDocumentRepository, never()).save(any());
        verify(vbdiDocumentRepository, never()).save(any());
    }

    @Test
    void execute_whenVbdiNotFound_shouldFail() {
        UUID vbdiId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new TraLaiDuThaoRequest();
        request.setVbdiId(vbdiId);

        Result<UUID> result = support.execute(request, TraLaiDuThaoTarget.LANH_DAO);

        assertThat(result.isSuccess()).isFalse();
    }
}
