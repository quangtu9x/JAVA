package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documentregister.DocumentRegister;
import com.td.domain.documents.BusinessDocument;
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
 * BL-210/211 (MVP, 21/08/2026) — {@link LuuVanBanDenUseCase}: hiệu chỉnh / "vào sổ" văn bản đến ĐÃ
 * TỒN TẠI, tương thích 1 phần {@code qlvb/vbden?action=luu} — xem javadoc lớp về phạm vi MVP đã
 * xác nhận với người dùng (chỉ merge field + tạo/cập nhật bản ghi sổ, KHÔNG port diff quá trình xử
 * lý/dọn danh sách chờ vào sổ/socket).
 */
@ExtendWith(MockitoExtension.class)
class LuuVanBanDenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentRegisterRepository documentRegisterRepository;
    @Mock
    private AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    @Mock
    private com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    @Mock
    private com.td.application.common.security.CurrentUserProvider currentUserProvider;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private LuuVanBanDenUseCase useCase;

    @Test
    void execute_withoutId_shouldReturnFailure() {
        useCase = newUseCase();
        var result = useCase.execute(new LuuVanBanDenRequest());

        assertThat(result.isSuccess()).isFalse();
        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_documentNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new LuuVanBanDenRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đến");
    }

    @Test
    void execute_documentWrongType_shouldReturnFailure() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbdi", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    // Khong co documentRegisterId - chi merge field, khong dung toi so van ban.
    @Test
    void execute_plainFieldUpdate_shouldMergeFieldsAndKeepUnrelatedFieldsUnchanged() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{\"subject\":\"Cu\",\"code\":\"123/QD\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Moi");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getIdvbden()).isEqualTo(document.getId());
        assertThat(result.getData().getIdso()).isNull();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("subject", "Moi");
        assertThat(saved).containsEntry("code", "123/QD"); // giu nguyen, khong bi mat
        verify(documentRepository).save(document);
        verify(documentRegisterRepository, never()).save(any());
        verify(addDynamicDocumentUseCase, never()).execute(any());
    }

    // documentRegisterId co gia tri, idso rong -> lan dau vao so: danh so + tao MOI ban ghi vbden_so.
    @Test
    void execute_withDocumentRegisterIdAndNoIdso_shouldIssueNumberAndCreateNewEntry() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 10, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        UUID entryId = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(entryId));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getIdso()).isEqualTo(entryId);
        assertThat(register.getCodeStartNumber()).isEqualTo(11);
        // Bugfix 21/08/2026 (D.16, da xac nhan tu truoc): "document_number" ket thuc hau to
        // "_number" nen FieldTypeInferrer suy NUMBER (Double), khong con la chuoi "10" sau khi qua
        // buildDynamicFieldsJson - dung hanh vi da co cua AddDynamicDocumentUseCase/TiepNhanVbdenUseCase.
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("document_number", 10.0);
        assertThat(saved).containsEntry("document_register_id", register.getId().toString());

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getDocumentType()).isEqualTo("vbden_so");
        assertThat(captor.getValue().getFields()).containsEntry("parentid", document.getId().toString());
        assertThat(captor.getValue().getFields()).containsEntry("document_number", "10");
        verify(documentRepository, never()).findById(entryId); // khong doc lai ban ghi so vua tao
    }

    /**
     * Bổ sung 07/09/2026 (yêu cầu người dùng: hoàn thiện broker phản hồi trạng thái "vào sổ" — xem
     * javadoc {@code PublishVbdiReplyStatusUseCase}): "vào sổ" THÀNH CÔNG phải publish STATUS_03
     * "Đã tiếp nhận" — dùng ĐÚNG {@code dynamic_fields} MỚI NHẤT (sau merge) của văn bản.
     */
    @Test
    void execute_withDocumentRegisterId_shouldPublishReplyStatusDaTiepNhan() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{\"send_log_id\":\"sl-1\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 10, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        // Bugfix 07/09/2026 (2, bao loi nguoi dung: "Nguoi thuc hien" cua STATUS_03 dang la "He
        // thong tiep nhan" - phai la TAI KHOAN van thu dang thao tac).
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("03");
        assertThat(captor.getValue().getStatusLabel()).isEqualTo("Đã tiếp nhận");
        assertThat(captor.getValue().getDynamicFields()).containsEntry("send_log_id", "sl-1");
        assertThat(captor.getValue().getActorDisplayName()).isEqualTo("vanthu01");
    }

    /** KHÔNG "vào sổ" (không có documentRegisterId) — KHÔNG được publish phản hồi trạng thái nào. */
    @Test
    void execute_withoutDocumentRegisterId_shouldNotPublishReplyStatus() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setFields(new LinkedHashMap<>());

        useCase.execute(request);

        verify(publishVbdiReplyStatusUseCase, never()).execute(any());
    }

    // documentRegisterId + idso da co - hieu chinh lai ban ghi so DA TON TAI (khong tao moi).
    @Test
    void execute_withDocumentRegisterIdAndExistingIdso_shouldUpdateExistingEntryInstead() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 20, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        BusinessDocument entry = newDocument("vbden_so", "{\"document_number\":\"5\",\"document_register\":\"So cu\"}");
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setIdso(entry.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getIdso()).isEqualTo(entry.getId());
        Map<String, Object> savedEntry = dynamicDocumentFieldsService.parseDynamicFields(entry.getDynamicFieldsJson());
        assertThat(savedEntry).containsEntry("document_number", 20.0); // so moi ghi de so cu (Double - hau to "_number")
        assertThat(savedEntry).containsEntry("document_register", register.getName());
        assertThat(savedEntry).containsEntry("parentid", document.getId().toString());
        verify(documentRepository).save(entry);
        verify(addDynamicDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_withDocumentRegisterIdAndExistingIdsoNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 1, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        UUID idso = UUID.randomUUID();
        when(documentRepository.findById(idso)).thenReturn(Optional.empty());

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setIdso(idso);
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownDocumentRegisterId_shouldReturnFailureWithoutSavingDocument() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{\"subject\":\"Cu\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        UUID registerId = UUID.randomUUID();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.empty());

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(registerId);
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_withManualNumberingRegisterAndNoSuppliedNumber_shouldReturnFailure() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(false, 1, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(documentRepository, never()).save(any());
        verify(addDynamicDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_whenRegisterEntryCreationFails_shouldReturnFailureEvenThoughDocumentSaved() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 1, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("loi tao ban ghi so"));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(documentRepository).save(document); // van ban goc van duoc luu
    }

    // R4 (22/08/2026): documentRegisterId co gia tri + field org dang luu dang JSON array (quy uoc
    // ChuyenXuLyVbdenUseCase) -> loai identifier_creator khoi org field, danh dau *_registered, GHI
    // LAI DUOI DANG LIST (khong phai chuoi) de tuong thich ChuyenXuLy doc sau nay.
    @Test
    void execute_withDocumentRegisterId_shouldRemoveIdentifierFromOrgQueueFields_whenStoredAsList() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden",
            "{\"unit_process\":[\"G10\",\"G20\"],\"unit_coordinate\":[\"G10\"],\"contact_unit_identifier\":[\"G10\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 1, ""); // identifier = "G10" (helper newRegister)
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("unit_process")).isEqualTo(java.util.List.of("G20"));
        assertThat(saved.get("unit_process_registered")).isEqualTo(java.util.List.of("G10"));
        assertThat(saved.get("unit_coordinate")).isEqualTo(java.util.List.of());
        assertThat(saved.get("unit_coordinate_registered")).isEqualTo(java.util.List.of("G10"));
        assertThat(saved.get("contact_unit_identifier")).isEqualTo(java.util.List.of());
        assertThat(saved.get("contact_unit_registered")).isEqualTo(java.util.List.of("G10"));
    }

    // R4: field org dang luu dang chuoi phan tach ";" (quy uoc AddDynamicDocumentUseCase/
    // FieldTypeInferrer, TRUOC KHI ChuyenXuLy tung dong toi) - phai doc duoc, va GHI LAI DUOI DANG
    // LIST (khong con la chuoi) sau khi sua, dung nhu truong hop tren.
    @Test
    void execute_withDocumentRegisterId_shouldRemoveIdentifierFromOrgQueueFields_whenStoredAsScalarString() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{\"unit_process\":\"G10;G30\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 1, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("unit_process")).isEqualTo(java.util.List.of("G30"));
        assertThat(saved.get("unit_process_registered")).isEqualTo(java.util.List.of("G10"));
    }

    // R4: field CA NHAN (user_process) chi bi loai identifier_creator NEU no co mat TRUC TIEP trong
    // do (truong hop don vi khong chon nguoi dai dien) - gia tri khac (username thuc) giu nguyen.
    @Test
    void execute_withDocumentRegisterId_shouldRemoveIdentifierFromPersonalFieldOnlyWhenPresentDirectly() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden",
            "{\"unit_process\":[\"G10\"],\"user_process\":[\"G10\",\"userA\"]}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        DocumentRegister register = newRegister(true, 1, "");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId())).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        request.setDocumentRegisterId(register.getId());
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("unit_process")).isEqualTo(java.util.List.of());
        assertThat(saved.get("unit_process_registered")).isEqualTo(java.util.List.of("G10"));
        assertThat(saved.get("user_process")).isEqualTo(java.util.List.of("userA"));
    }

    // R4: khong co documentRegisterId (chi hieu chinh field thuong) -> KHONG dung toi org-queue
    // fields, du van ban da co san field do voi gia tri trung identifier_creator.
    @Test
    void execute_plainFieldUpdateWithoutDocumentRegisterId_shouldNotTouchOrgQueueFields() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden",
            "{\"unit_process\":[\"G10\"],\"identifier_creator\":\"G10\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Moi");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("unit_process")).isEqualTo(java.util.List.of("G10"));
        assertThat(saved.get("unit_process_registered")).isNull();
    }

    // Bugfix 22/08/2026 (bao loi nguoi dung: "chua thay hien thi trich yeu" - TiepNhanVbdenUseCase
    // luu "subject" vao COT entity.title, khong vao dynamic_fields) - Luu() phai dong bo lai
    // entity.title moi khi request co gui "subject", tranh 2 gia tri lech nhau sau hieu chinh.
    @Test
    void execute_withSubjectField_shouldSyncEntityTitle() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Trich yeu moi");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getTitle()).isEqualTo("Trich yeu moi");
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("subject", "Trich yeu moi");
    }

    // Khong gui "subject" (hieu chinh field khac) -> giu nguyen title cu, khong ghi de thanh rong/null.
    @Test
    void execute_withoutSubjectField_shouldNotTouchEntityTitle() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        document.update("Trich yeu cu", null, null, null, null, null, null);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_in_charge", "Don vi khac");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getTitle()).isEqualTo("Trich yeu cu");
    }

    // R6-lite (22/08/2026, xem javadoc LuuVanBanDenUseCase#applyDefaultAssignerAsProcessor) - da
    // xac nhan qua du lieu mau thuc te docs/views_domino/data_vbden_mau.txt: chon "Lanh dao phan
    // cong" MOI (khac truoc khi luu) + KHONG dung toi widget (user_process rong sau merge) -> tu
    // gan user_process = user_assigner, tra ve trong LuuVanBanDenResultDto de FE biet ma goi tiep
    // ChuyenXuLy (append chuyenxuly_obj/tinh lai status).
    @Test
    void execute_withNewAssignerAndNoUserProcess_shouldDefaultUserProcessToAssigner() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("assigner", "Le Phu Ha");
        fields.put("user_assigner", "lpha");
        fields.put("user_process", "");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getUserProcess()).isEqualTo("lpha");
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("user_process")).isEqualTo(java.util.List.of("lpha"));
    }

    // Widget DA gan ai do (user_process khac rong sau merge) -> KHONG ghi de, giu dung lua chon
    // thuc cua nguoi dung.
    @Test
    void execute_withNewAssignerButUserProcessAlreadySet_shouldNotOverride() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_assigner", "lpha");
        fields.put("user_process", "nva");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getUserProcess()).isEqualTo("nva");
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("user_process")).isEqualTo("nva"); // giu nguyen dang scalar da gui, khong bi ep lai
    }

    // assigner KHONG doi so voi truoc khi luu (hieu chinh field khac, khong dung gi den lanh dao) ->
    // KHONG default lai - tranh ghi de neu nguoi dung da chu dong xoa user_process truoc do.
    @Test
    void execute_withUnchangedAssignerAndEmptyUserProcess_shouldNotDefault() {
        useCase = newUseCase();
        BusinessDocument document = newDocument("vbden", "{\"user_assigner\":\"lpha\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuVanBanDenRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Moi");
        fields.put("user_assigner", "lpha"); // gui lai gia tri CU, khong doi
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getUserProcess()).isEmpty();
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved.get("user_process")).isNull();
    }

    private LuuVanBanDenUseCase newUseCase() {
        return new LuuVanBanDenUseCase(documentRepository, dynamicDocumentFieldsService,
            documentRegisterRepository, addDynamicDocumentUseCase, publishVbdiReplyStatusUseCase,
            currentUserProvider, authorAclFieldsService, com.td.application.realtime.RealtimeNotifierTestSupport.noop());
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Tieu de", documentType, "0", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }

    private DocumentRegister newRegister(boolean autoIncrement, int codeStartNumber, String codeStartNumbers) {
        return new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null, null, null,
            null, null, null, "Nguyễn Văn A", codeStartNumber, autoIncrement, true, codeStartNumbers);
    }
}
