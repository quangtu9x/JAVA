package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.application.documents.CopyDocumentFilesUseCase;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.SupersedeDocumentFilesUseCase;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @Mock
    private SupersedeDocumentFilesUseCase supersedeDocumentFilesUseCase;
    @Mock
    private VbdenUpdateHistoryDocumentRepository vbdenUpdateHistoryDocumentRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CapNhatVanBanThayTheThuHoiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatVanBanThayTheThuHoiUseCase(
            documentRepository, dynamicDocumentFieldsService, currentUserProvider,
            resolveIdentifierByLevelUseCase, copyDocumentFilesUseCase, documentLogService,
            documentCacheService, publishVbdiReplyStatusUseCase, listDocumentFilesUseCase,
            supersedeDocumentFilesUseCase, vbdenUpdateHistoryDocumentRepository, appUserRepository);
        org.mockito.Mockito.lenient().when(documentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(vbdenUpdateHistoryDocumentRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 0, 0L, 1, true, true));
    }

    private VbdenUpdateHistoryDocument capturedHistory() {
        ArgumentCaptor<VbdenUpdateHistoryDocument> captor = ArgumentCaptor.forClass(VbdenUpdateHistoryDocument.class);
        verify(vbdenUpdateHistoryDocumentRepository).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> updateObj(VbdenUpdateHistoryDocument history) throws Exception {
        return objectMapper.readValue(history.getUpdateObjJson(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> names(Map<String, Object> updateObj, String key) {
        return ((List<Map<String, Object>>) updateObj.get(key)).stream().map(m -> (String) m.get("name")).toList();
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
        assertThat(revokeFields.get("is_revoke")).isEqualTo("1");
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
        givenFiles(documentC.getId(), file("thaythe.pdf", 40));
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.success(1));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(docRevoke.isDeleted()).isFalse();
        assertThat(capturedHistory().getAction()).isEqualTo(VbdenUpdateHistoryDocument.ACTION_THAY_THE_THU_HOI);
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

    private static final String UNIT = "H05.01";

    private void givenRegisteredBy(BusinessDocument docRevoke, String identifierCreator) {
        BusinessDocument entry = newDocument("DRAFT",
            "{\"parentid\":\"" + docRevoke.getId() + "\",\"identifier_creator\":\"" + identifierCreator + "\"}");
        when(documentRepository.findByParentId(eq("vbden_so"), eq(docRevoke.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(entry)));
    }

    private void givenNotRegistered(BusinessDocument docRevoke) {
        when(documentRepository.findByParentId(eq("vbden_so"), eq(docRevoke.getId().toString()), any()))
            .thenReturn(Page.empty());
    }

    private void givenFiles(UUID documentId, FileDto... files) {
        when(listDocumentFilesUseCase.execute(documentId))
            .thenReturn(new PaginationResponse<>(List.of(files), 0, files.length, (long) files.length, 1, true, true));
    }

    private FileDto file(String name, long size) {
        return FileDto.builder().fileId(UUID.randomUUID()).originalFileName(name).fileSize(size).build();
    }

    @Test
    void execute_capNhat_docRevokeRegistered_shouldMergeAndDeleteC() {
        // Bugfix 13/09/2026 (bao loi nguoi dung "gui lai van ban... phai la van ban cap nhat"):
        // nghiepvu=="2" (gui lai), docRevoke DA VAO SO -> merge noi dung+tep TU C VAO docRevoke
        // (docRevoke SONG), roi HUY C (khac han nhanh "1"/"4").
        // Bugfix 24/09/2026: docRevoke gui lan dau KHONG co field bussiness_doc_type (worker khong ghi
        // "0") - truoc day bi guard =="0" loai; chi copy tep CHUA co tren docRevoke.
        BusinessDocument docRevoke = newDocument("2", "{\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}],"
                + "\"subject\":\"Van ban moi\",\"tepbanhanh\":\"vanban.pdf\"}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenRegisteredBy(docRevoke, UNIT);
        givenFiles(documentC.getId(), file("vanban.pdf", 10), file("phuluc_bosung.docx", 20));
        givenFiles(docRevoke.getId(), file("vanban.pdf", 10));
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.success(1));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<CopyDocumentFilesRequest> copyCaptor = ArgumentCaptor.forClass(CopyDocumentFilesRequest.class);
        verify(copyDocumentFilesUseCase).execute(copyCaptor.capture());
        assertThat(copyCaptor.getValue().getSourceDocumentId()).isEqualTo(documentC.getId());
        assertThat(copyCaptor.getValue().getTargetDocumentId()).isEqualTo(docRevoke.getId());
        assertThat(copyCaptor.getValue().getFilenames()).containsExactly("phuluc_bosung.docx");
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
        verify(publishVbdiReplyStatusUseCase).execute(any());
    }

    @Test
    void execute_capNhat_registeredDocIsItselfAResendCopy_shouldStillMerge() {
        // Bugfix 24/09/2026 - dung du lieu that (h05_01_sokhcn, 21/09/2026): ban gui lai lan 1 (mang
        // bussiness_doc_type="2") duoc vao so THANG, ban goc chua vao so. Gui lai lan 2 -> "Cap nhat VB"
        // phai cap nhat ban DA VAO SO (guard cu =="0" bo qua ca 2 -> khong cap nhat gi).
        BusinessDocument pendingOriginal = newDocument("0", "{\"subject\":\"Ban goc chua vao so\"}");
        BusinessDocument registered = newDocument("2", "{\"bussiness_doc_type\":\"2\",\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + pendingOriginal.getId() + "\"},"
                + "{\"id\":\"" + registered.getId() + "\"}],\"subject\":\"Van ban moi\"}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(pendingOriginal.getId())).thenReturn(Optional.of(pendingOriginal));
        when(documentRepository.findById(registered.getId())).thenReturn(Optional.of(registered));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenNotRegistered(pendingOriginal);
        givenRegisteredBy(registered, UNIT);
        givenFiles(documentC.getId(), file("du_thao.pdf", 10), file("qd.doc", 30));
        givenFiles(registered.getId(), file("du_thao.pdf", 10));
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.success(1));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(registered.getDynamicFieldsJson()).get("subject"))
            .isEqualTo("Van ban moi");
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(pendingOriginal.getDynamicFieldsJson()).get("subject"))
            .isEqualTo("Ban goc chua vao so");
        ArgumentCaptor<CopyDocumentFilesRequest> copyCaptor = ArgumentCaptor.forClass(CopyDocumentFilesRequest.class);
        verify(copyDocumentFilesUseCase).execute(copyCaptor.capture());
        assertThat(copyCaptor.getValue().getTargetDocumentId()).isEqualTo(registered.getId());
        assertThat(copyCaptor.getValue().getFilenames()).containsExactly("qd.doc");
        assertThat(documentC.isDeleted()).isTrue();
    }

    @Test
    void execute_capNhat_allFilesAlreadyPresent_shouldNotCopyAnything() {
        // CopyDocumentFilesUseCase voi bo loc rong = copy TOAN BO -> khong duoc goi khi khong co tep moi.
        BusinessDocument docRevoke = newDocument("2", "{\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}]}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenRegisteredBy(docRevoke, UNIT);
        givenFiles(documentC.getId(), file("vanban.pdf", 10));
        givenFiles(docRevoke.getId(), file("vanban.pdf", 10));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verifyNoInteractions(copyDocumentFilesUseCase);
        assertThat(documentC.isDeleted()).isTrue();
    }

    @Test
    void execute_capNhat_shouldSupersedeOldFilesAndRecordHistory() throws Exception {
        // Yeu cau nguoi dung 24/09/2026 (2): tep cu KHONG con trong bo tep moi -> danh dau "da thay the"
        // (an, khong xoa); tep trung han giu nguyen; tep moi copy sang; ghi 1 dong lich su day du.
        BusinessDocument docRevoke = newDocument("2", "{\"subject\":\"Van ban cu\",\"code\":\"12/QD-UB\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}],"
                + "\"subject\":\"Van ban moi\",\"code\":\"12/QD-UB\",\"unit_in_charge\":\"UBND tinh\","
                + "\"promulgation_date\":\"20/09/2026\"}");
        FileDto oldVersion = file("quyetdinh.pdf", 10);
        FileDto kept = file("phuluc.docx", 20);

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenRegisteredBy(docRevoke, UNIT);
        givenFiles(documentC.getId(), file("quyetdinh.pdf", 15), file("phuluc.docx", 20), file("bosung.xlsx", 30));
        givenFiles(docRevoke.getId(), oldVersion, kept);
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.success(2));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();

        VbdenUpdateHistoryDocument history = capturedHistory();
        assertThat(history.getParentId()).isEqualTo(docRevoke.getId());
        assertThat(history.getSourceDocumentId()).isEqualTo(documentC.getId());
        assertThat(history.getAction()).isEqualTo(VbdenUpdateHistoryDocument.ACTION_CAP_NHAT);
        assertThat(history.getUserCreator()).isEqualTo("vanthu01");
        verify(supersedeDocumentFilesUseCase).execute(docRevoke.getId(), List.of(oldVersion.getFileId()), history.getId());

        Map<String, Object> obj = updateObj(history);
        assertThat(names(obj, "new_files")).containsExactlyInAnyOrder("quyetdinh.pdf", "bosung.xlsx");
        assertThat(names(obj, "kept_files")).containsExactly("phuluc.docx");
        assertThat(names(obj, "superseded_files")).containsExactly("quyetdinh.pdf");
        assertThat(names(obj, "source_files")).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) obj.get("source");
        assertThat(source.get("id")).isEqualTo(documentC.getId().toString());
        assertThat(source.get("unit_in_charge")).isEqualTo("UBND tinh");
        @SuppressWarnings("unchecked")
        Map<String, Object> changed = (Map<String, Object>) obj.get("changed_fields");
        assertThat(changed).containsOnlyKeys("subject", "promulgation_date");
    }

    @Test
    void execute_capNhat_sourceWithoutFiles_shouldNotSupersedeAnything() {
        // An toan: ban gui lai khong co tep nao (vd buoc chuan bi tep phia gui loi) -> KHONG an tep cu.
        BusinessDocument docRevoke = newDocument("2", "{\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}]}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenRegisteredBy(docRevoke, UNIT);
        givenFiles(docRevoke.getId(), file("vanban.pdf", 10));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verifyNoInteractions(supersedeDocumentFilesUseCase, copyDocumentFilesUseCase);
        capturedHistory();
    }

    @Test
    void execute_capNhat_copyFails_shouldKeepOldFilesVisible() throws Exception {
        BusinessDocument docRevoke = newDocument("2", "{\"subject\":\"Van ban cu\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + docRevoke.getId() + "\"}]}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(docRevoke.getId())).thenReturn(Optional.of(docRevoke));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenRegisteredBy(docRevoke, UNIT);
        givenFiles(documentC.getId(), file("moi.pdf", 11));
        givenFiles(docRevoke.getId(), file("cu.pdf", 10));
        when(copyDocumentFilesUseCase.execute(any())).thenReturn(Result.failure("MinIO loi"));

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verifyNoInteractions(supersedeDocumentFilesUseCase);
        Map<String, Object> obj = updateObj(capturedHistory());
        assertThat(names(obj, "superseded_files")).isEmpty();
        assertThat(names(obj, "kept_files")).containsExactly("cu.pdf");
    }

    @Test
    void execute_capNhat_noRegisteredTarget_shouldFailWithoutTouchingAnything() {
        // Bugfix 24/09/2026: truoc day tra thanh cong + ghi C.reply="1" -> van ban gui lai bien mat khoi
        // danh sach cho du KHONG cap nhat duoc gi. Gio: tra loi, khong ghi/publish gi.
        BusinessDocument pendingOriginal = newDocument("0", "{\"subject\":\"Van ban cu\"}");
        BusinessDocument otherUnitRegistered = newDocument("2", "{\"subject\":\"Van ban cu don vi khac\"}");
        BusinessDocument documentC = newDocument("0",
            "{\"bussiness_doc_type\":\"2\",\"documentoutrevoke\":[{\"id\":\"" + pendingOriginal.getId() + "\"},"
                + "{\"id\":\"" + otherUnitRegistered.getId() + "\"}]}");

        when(documentRepository.findById(documentC.getId())).thenReturn(Optional.of(documentC));
        when(documentRepository.findById(pendingOriginal.getId())).thenReturn(Optional.of(pendingOriginal));
        when(documentRepository.findById(otherUnitRegistered.getId())).thenReturn(Optional.of(otherUnitRegistered));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(UNIT));
        givenNotRegistered(pendingOriginal);
        givenRegisteredBy(otherUnitRegistered, "H05.02");

        var request = new CapNhatVanBanThayTheThuHoiRequest();
        request.setId(documentC.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo(CapNhatVanBanThayTheThuHoiUseCase.NO_REGISTERED_TARGET_MESSAGE);
        assertThat(documentC.isDeleted()).isFalse();
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(documentC.getDynamicFieldsJson()).get("reply")).isNull();
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(copyDocumentFilesUseCase, publishVbdiReplyStatusUseCase,
            supersedeDocumentFilesUseCase, vbdenUpdateHistoryDocumentRepository);
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
