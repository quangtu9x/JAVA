package com.td.application.duthao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.AddVbdiDynamicDocumentUseCase;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-230 (DuThao Core) — DoD (3): chuyển dự thảo thành văn bản đi qua
 * {@link ChuyenThanhVanBanDiUseCase}.
 *
 * <p>Đính chính 27/08/2026 ("kiểm tra dữ liệu đang copy nguyên của dự thảo sang, port lại cho
 * đúng"): viết lại toàn bộ để khớp thiết kế whitelist thật (49 field cố định + field gán tay riêng)
 * thay vì "copy toàn bộ trừ blocklist" của bản trước.</p>
 */
@ExtendWith(MockitoExtension.class)
class ChuyenThanhVanBanDiUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private CopyDocumentFilesUseCase copyDocumentFilesUseCase;
    @Mock
    private AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentRepository documentRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository =
        new com.td.application.documentlinks.InMemoryDocumentLinkRepository();
    private ChuyenThanhVanBanDiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChuyenThanhVanBanDiUseCase(
            duThaoDocumentRepository,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newService(linkRepository),
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository),
            addVbdiDynamicDocumentUseCase, dynamicDocumentFieldsService,
            documentLogService, documentCacheService, copyDocumentFilesUseCase, addDraftProcessDocumentUseCase,
            currentUserProvider, documentRepository,
            new DraftProcessActorFields(org.mockito.Mockito.mock(com.td.application.sharedcore.AppUserRepository.class)));
        // Mac dinh khong co nhiem vu CDDH nao lien ket - cac test khong quan tam vbcddh khong can
        // stub rieng.
        lenient().when(documentRepository.findByFieldValue(anyString(), anyString(), anyString(), any()))
            .thenReturn(Page.empty());
    }

    @Test
    void execute_withUnknownDraft_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonDuThaoDocument_shouldReturnFailure() {
        DuThaoDocument document = newDraft("vbden", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    // 06/10/2026 (bang document_links): phat hanh -> duthao_vbdi + hoso_vbdi_phathanh cho moi ho so dang
    // chua du thao (tab "Van ban phat hanh" cua ho so hien van ban di moi).
    @Test
    void execute_shouldLinkDraftToNewVbdiAndHoSoIssuance() {
        DuThaoDocument draft = newDraft("duthao", "{\"name\":\"DT-002\"}");
        UUID hosocvId = UUID.randomUUID();
        linkRepository.seed(com.td.domain.documentlinks.DocumentLinkType.HOSO_DUTHAO, hosocvId, draft.getId());
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());
        useCase.execute(request);

        assertThat(linkRepository.activeRows())
            .extracting(link -> link.getLinkType() + ":" + link.getSourceId() + ">" + link.getTargetId())
            .contains("duthao_vbdi:" + draft.getId() + ">" + vbdiId,
                "hoso_vbdi_phathanh:" + hosocvId + ">" + vbdiId);
    }

    @Test
    void execute_shouldCopyOnlyWhitelistFieldsAndMarkDraftPublished() {
        // unit_in_charge/signer: trong whitelist that (49 field) -> phai sang. user_coordinator/
        // opinion/ghi_chu_noi_bo: KHONG nam trong whitelist -> KHONG duoc sang, du KHONG nam trong
        // blocklist cu (dung diem dinh chinh chinh cua dot nay).
        DuThaoDocument draft = newDraft("duthao",
            "{\"unit_in_charge\":\"So Noi Vu\",\"signer\":\"Nguyen Van A\",\"user_coordinator\":[\"x\"],"
            + "\"opinion\":\"y kien noi bo\",\"ghi_chu_noi_bo\":\"khong duoc sang\","
            + "\"name\":\"DT-001\",\"user_creator\":\"nva\",\"creator\":\"Nguyen Van A\","
            + "\"creator_id\":\"u-1\",\"document_profiles_id\":\"hs-1\","
            + "\"unit_publish_id\":\"up-1\",\"unit_publish_identifier\":\"UP.01\","
            + "\"unit_publish_identifier_other\":\"UP.OTHER\","
            + "\"tepbanhanh\":\"baocao.docx;\",\"tepkemtheo\":\"phuluc.pdf;\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(vbdiId);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("vbdi");
        assertThat(captured.getStatus()).isEqualTo(VbdiStatus.DRAFT);

        // Whitelist that (49 field) - co sang.
        assertThat(captured.getFields()).containsEntry("unit_in_charge", "So Noi Vu");
        assertThat(captured.getFields()).containsEntry("signer", "Nguyen Van A");
        // Field gan tay rieng biet sau vong lap whitelist (dong 160-359 Domino).
        assertThat(captured.getFields()).containsEntry("form", "DocumentOut");
        assertThat(captured.getFields()).containsEntry("draft_id", draft.getId().toString());
        assertThat(captured.getFields()).containsEntry("draft_identifier_id", "DT-001");
        assertThat(captured.getFields()).containsEntry("user_draft", "nva");
        assertThat(captured.getFields()).containsEntry("draftor", "Nguyen Van A");
        assertThat(captured.getFields()).containsEntry("draftor_id", "u-1");
        assertThat(captured.getFields()).containsEntry("recipients_copy", "Nguyen Van A");
        // 06/10/2026: lien ket ho so qua bang document_links, khong chep field document_profiles_from_draft_id.
        assertThat(captured.getFields()).doesNotContainKey("document_profiles_from_draft_id");
        assertThat(captured.getFields()).containsEntry("unit_publish_id", "up-1");
        assertThat(captured.getFields()).containsEntry("unit_publish_identifier", "UP.01");
        assertThat(captured.getFields()).containsEntry("unit_publish_identifier_other", "UP.OTHER");
        // "author" giờ do AuthorAclFieldsService tự áp dụng BÊN TRONG addVbdiDynamicDocumentUseCase
        // (generic, xem AuthorAclFieldsServiceTest) - use case này không tự tính nữa, không assert ở đây.
        // KHÔNG nằm trong whitelist thật - dù KHÔNG nằm trong blocklist cũ.
        assertThat(captured.getFields()).doesNotContainKey("user_coordinator");
        assertThat(captured.getFields()).doesNotContainKey("opinion");
        assertThat(captured.getFields()).doesNotContainKey("ghi_chu_noi_bo");

        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.PUBLISHED);
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(updatedFields).containsEntry("document_out_id", vbdiId.toString());
        // Domino dong 463-465: draft_end/draft_end_date.
        assertThat(updatedFields).containsEntry("draft_end", "1");
        assertThat(updatedFields).containsKey("draft_end_date");
        verify(documentLogService).logUpdate(any(), any(), any());

        // Copy tep: loc theo ten trong tepbanhanh/tepkemtheo (xem dinh chinh lan 2, javadoc lop).
        ArgumentCaptor<CopyDocumentFilesRequest> copyCaptor = ArgumentCaptor.forClass(CopyDocumentFilesRequest.class);
        verify(copyDocumentFilesUseCase).execute(copyCaptor.capture());
        assertThat(copyCaptor.getValue().getSourceDocumentId()).isEqualTo(draft.getId());
        assertThat(copyCaptor.getValue().getTargetDocumentId()).isEqualTo(vbdiId);
        assertThat(copyCaptor.getValue().getModule()).isEqualTo("vbdi");
        assertThat(copyCaptor.getValue().getFilenames()).containsExactlyInAnyOrder("baocao.docx", "phuluc.pdf");

        ArgumentCaptor<AddDynamicDocumentRequest> trailCaptor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(trailCaptor.capture());
        var trailFields = trailCaptor.getValue().getFields();
        assertThat(trailFields).containsEntry("parentid", draft.getId().toString());
        assertThat(trailFields).containsEntry("action_type", "chuyenvanthu");
        assertThat(trailFields).containsEntry("status", DuThaoStatus.PUBLISHED);
        assertThat(trailFields).containsEntry("oldstatus", "DRAFTING");
        assertThat(trailFields).containsEntry("receiver", "Văn thư");
    }

    @Test
    void execute_withoutTepBanHanhOrTepKemTheo_shouldNotCopyAnyFile() {
        DuThaoDocument draft = newDraft("duthao", "{\"unit_in_charge\":\"So Noi Vu\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(copyDocumentFilesUseCase, never()).execute(any());
    }

    // 06/10/2026: tab "Noi nhan van ban" cua du thao phai sang tab "Phat hanh" cua van ban di
    // (Domino DraftToDocumentOut dong 168-296).
    @Test
    void execute_shouldMapRecipientObjsToVbdiPhatHanhFields() throws Exception {
        DuThaoDocument draft = newDraft("duthao", "{"
            + "\"internal_unit_obj\":[{\"id\":\"i1\",\"name\":\"Phong A\",\"identifier\":\"PA\"},{\"id\":\"i2\",\"name\":\"Phong B\",\"identifier\":\"PB\"}],"
            + "\"external_unit_obj\":\"[{\\\"id\\\":\\\"e1\\\",\\\"name\\\":\\\"Bo X\\\"}]\","
            + "\"contact_unit_obj\":[],"
            + "\"individual_obj\":[{\"id\":\"u1\",\"name\":\"Nguyen Van B\",\"username\":\"nvb\"}],"
            + "\"recipient_other\":\"Luu VT\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());
        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        var fields = captor.getValue().getFields();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(fields).containsEntry("internal_unit_rtext", "Phong A; Phong B");
        assertThat(mapper.readTree(fields.get("internal_unit_obj_log_rtext")).get(1).get("identifier").asText()).isEqualTo("PB");
        assertThat(fields).containsEntry("external_unit_rtext", "Bo X");
        assertThat(mapper.readTree(fields.get("external_unit_obj_log_rtext")).get(0).get("id").asText()).isEqualTo("e1");
        assertThat(fields).containsEntry("coordinator_rtext", "Nguyen Van B");
        assertThat(mapper.readTree(fields.get("coordinator_obj_log_rtext")).get(0).get("username").asText()).isEqualTo("nvb");
        assertThat(fields).doesNotContainKeys("contact_unit_rtext", "contact_unit_obj_log_rtext");
        assertThat(fields).containsEntry("recipient_other", "Luu VT");
    }

    // Bugfix 04/09/2026 (bao loi nguoi dung: "Chuyen van thu" lam mat documentin_obj cua van ban di
    // vua tao - xem javadoc putIfPresent()): documentin_obj (hau to "_obj") duoc
    // DynamicDocumentFieldsService PARSE SAN thanh List<Map> that (khong con la String) khi doc lai
    // draftFields - truoc day putIfPresent goi thang value.toString() tren List/Map, cho ra chuoi
    // Java toString() mac dinh (dung "=" thay ":", khong co ngoac kep) - KHONG PHAI JSON hop le, ghi
    // hong vinh vien vao van ban di moi. Test nay xac nhan gia tri duoc RE-SERIALIZE dung JSON.
    @Test
    void execute_withDocumentinObjAsNestedJson_shouldReserializeAsValidJson() throws Exception {
        // 06/10/2026 (bang document_links giai doan 2): "Tra loi cho van ban den" / "Van ban kem theo" cua du
        // thao chep sang van ban di bang LIEN KET + ban chup (khong chep field _obj qua whitelist nua).
        UUID vbdenId = UUID.randomUUID();
        UUID relatedVbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("duthao", "{}");
        var snapshots = com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository);
        snapshots.linkItem(com.td.domain.documentlinks.DocumentLinkType.DUTHAO_TRALOI_VBDEN, draft.getId(),
            Map.of("id", vbdenId.toString(), "code", "DEN-01", "subject", "Kiem tra"));
        snapshots.linkItem(com.td.domain.documentlinks.DocumentLinkType.DUTHAO_KEMTHEO_VBDI, draft.getId(),
            Map.of("id", relatedVbdiId.toString(), "code", "DI-02", "database_type", "vbdi"));
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());
        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKeys("documentin_obj", "vanban_lienquan_obj");
        assertThat(snapshots.replyItems(vbdiId, "vbdi")).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("id", vbdenId.toString()).containsEntry("code", "DEN-01"));
        assertThat(snapshots.relatedItems(vbdiId, "vbdi")).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("id", relatedVbdiId.toString()).containsEntry("code", "DI-02"));
    }

    @Test
    void execute_shouldSyncIdVbdiOntoLinkedVbcddhTasks() {
        DuThaoDocument draft = newDraft("duthao", "{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        BusinessDocument nhiemVu = new BusinessDocument("Nhiem vu 1", "vbcddh", "1", null, null, null, null);
        nhiemVu.replaceDynamicFields("{\"idvanban\":\"" + draft.getId() + "\",\"form\":\"nhiemvu\"}");
        when(documentRepository.findByFieldValue(eq("vbcddh"), eq("idvanban"), eq(draft.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(nhiemVu)));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        useCase.execute(request);

        ArgumentCaptor<BusinessDocument> savedCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(savedCaptor.capture());
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(savedCaptor.getValue().getDynamicFieldsJson());
        assertThat(savedFields).containsEntry("idvbdi", vbdiId.toString());
    }

    @Test
    void execute_whenVbcddhSyncThrows_shouldStillSucceed() {
        DuThaoDocument draft = newDraft("duthao", "{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));
        when(documentRepository.findByFieldValue(anyString(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("db down"));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(addDraftProcessDocumentUseCase).execute(any());
    }

    @Test
    void execute_withOpinion_shouldIncludeOpinionInProcessTrail() {
        DuThaoDocument draft = newDraft("duthao", "{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());
        request.setOpinion("Đề nghị văn thư phát hành sớm");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> trailCaptor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(trailCaptor.capture());
        assertThat(trailCaptor.getValue().getFields()).containsEntry("opinion", "Đề nghị văn thư phát hành sớm");
    }

    @Test
    void execute_whenAlreadyLinked_shouldBeIdempotentAndNotCreateAnother() {
        UUID existingVbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("duthao", "{\"document_out_id\":\"" + existingVbdiId + "\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(existingVbdiId);
        verify(addVbdiDynamicDocumentUseCase, never()).execute(any());
        verify(duThaoDocumentRepository, never()).save(any());
        verify(copyDocumentFilesUseCase, never()).execute(any());
        verify(addDraftProcessDocumentUseCase, never()).execute(any());
        verify(documentRepository, never()).findByFieldValue(anyString(), anyString(), anyString(), any());
    }

    @Test
    void execute_whenVbdiCreationFails_shouldNotMarkDraftPublished() {
        DuThaoDocument draft = newDraft("duthao", "{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("loi"));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(draft.getStatus()).isEqualTo("DRAFTING");
        verify(duThaoDocumentRepository, times(1)).findById(any());
        verify(duThaoDocumentRepository, never()).save(any());
        verify(copyDocumentFilesUseCase, never()).execute(any());
        verify(addDraftProcessDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_whenCopyFilesThrows_shouldStillSucceed() {
        DuThaoDocument draft = newDraft("duthao", "{\"tepbanhanh\":\"baocao.docx;\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbdiId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbdiId));
        when(copyDocumentFilesUseCase.execute(any())).thenThrow(new RuntimeException("minio down"));

        var request = new ChuyenThanhVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.PUBLISHED);
        verify(addDraftProcessDocumentUseCase).execute(any());
    }

    private DuThaoDocument newDraft(String documentType, String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Du thao", documentType, "DRAFTING", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
