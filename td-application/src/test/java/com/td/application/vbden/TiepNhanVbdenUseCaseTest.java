package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.domain.documentregister.DocumentRegister;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-210 (VBDen Core) — DoD (1): tiếp nhận văn bản đến luôn cố định {@code documentType="vbden"},
 * uỷ quyền toàn bộ cho {@link AddDynamicDocumentUseCase} (BL-101, đã tự sinh số đến qua BL-104).
 *
 * <p>BL-210/211 (bổ sung 19/08/2026): khi có {@code documentRegisterId}, số đến scope theo ĐÚNG sổ
 * đó (BL-116) — xem {@link TiepNhanVbdenUseCase#applyDocumentRegisterNumbering}.</p>
 */
@ExtendWith(MockitoExtension.class)
class TiepNhanVbdenUseCaseTest {

    @Mock
    private AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    @Mock
    private DocumentRegisterRepository documentRegisterRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private TiepNhanVbdenUseCase useCase;

    @Test
    void execute_withoutDocumentRegisterId_shouldDelegateToAddDynamicDocumentUseCaseWithVbdenDocumentType() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        UUID id = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        var request = new TiepNhanVbdenRequest();
        request.setSubject("Van ban den ve viec ...");
        request.setStatus("1");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("received_date", "2026-08-04");
        fields.put("unit_in_charge", "So Noi Vu");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(id);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());

        AddDynamicDocumentRequest captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("vbden");
        assertThat(captured.getTitle()).isEqualTo("Van ban den ve viec ...");
        assertThat(captured.getStatus()).isEqualTo("1");
        assertThat(captured.getFields()).containsEntry("received_date", "2026-08-04");
        assertThat(captured.getFields()).containsEntry("unit_in_charge", "So Noi Vu");
        verify(documentRegisterRepository, never()).save(any());
    }

    @Test
    void execute_withDocumentRegisterIdAndAutoIncrement_shouldIssueAndRecordNumber() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        DocumentRegister register = newRegister(true, 10, "");
        UUID registerId = register.getId();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.of(register));
        UUID id = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        var request = new TiepNhanVbdenRequest();
        request.setSubject("Van ban den vao so");
        request.setDocumentRegisterId(registerId);
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        // Slice 9: gio goi 2 lan - 1 cho van ban goc (vbden), 1 cho ban ghi so rieng (vbden_so).
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase, times(2)).execute(captor.capture());
        AddDynamicDocumentRequest parentRequest = captor.getAllValues().get(0);
        assertThat(parentRequest.getDocumentType()).isEqualTo("vbden");
        assertThat(parentRequest.getFields()).containsEntry("document_number", "10");
        assertThat(parentRequest.getFields()).containsEntry("document_register_id", registerId.toString());
        assertThat(parentRequest.getFields()).containsEntry("document_register", register.getName());
        assertThat(register.getCodeStartNumber()).isEqualTo(11);
        assertThat(register.getCodeStartNumbers()).isEqualTo("10");
        verify(documentRegisterRepository).save(register);
    }

    // Slice 9 (doi chieu lai Domino GetSoVanBanDen.java - 2 database rieng vbden/vbden_so): tao
    // THEM 1 document con "vbden_so" (parentid = van ban goc) - xem javadoc TiepNhanVbdenUseCase.
    @Test
    void execute_withDocumentRegisterId_shouldAlsoCreateSeparateRegisterEntryDocument() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        DocumentRegister register = newRegister(true, 5, "");
        UUID registerId = register.getId();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.of(register));
        UUID parentId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any()))
            .thenReturn(Result.success(parentId))
            .thenReturn(Result.success(entryId));

        var request = new TiepNhanVbdenRequest();
        request.setSubject("Van ban den vao so");
        request.setDocumentRegisterId(registerId);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("received_date", "2026-08-21");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(parentId);
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase, times(2)).execute(captor.capture());
        AddDynamicDocumentRequest entryRequest = captor.getAllValues().get(1);
        assertThat(entryRequest.getDocumentType()).isEqualTo("vbden_so");
        assertThat(entryRequest.getFields()).containsEntry("parentid", parentId.toString());
        assertThat(entryRequest.getFields()).containsEntry("document_number", "5");
        assertThat(entryRequest.getFields()).containsEntry("document_register_id", registerId.toString());
        assertThat(entryRequest.getFields()).containsEntry("document_register", register.getName());
        assertThat(entryRequest.getFields()).containsEntry("received_date", "2026-08-21");
    }

    @Test
    void execute_whenRegisterEntryDocumentCreationFails_shouldReturnFailureEvenThoughParentSucceeded() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        DocumentRegister register = newRegister(true, 5, "");
        UUID registerId = register.getId();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any()))
            .thenReturn(Result.success(UUID.randomUUID()))
            .thenReturn(Result.failure("loi luu ban ghi so"));

        var request = new TiepNhanVbdenRequest();
        request.setDocumentRegisterId(registerId);
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withDocumentRegisterIdAndFeSuppliedNumber_shouldHonorItAndStillRecordHistory() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        DocumentRegister register = newRegister(true, 1, "");
        UUID registerId = register.getId();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.of(register));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TiepNhanVbdenRequest();
        request.setSubject("Van ban den vao so");
        request.setDocumentRegisterId(registerId);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("document_number", "99");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(register.getCodeStartNumber()).isEqualTo(100);
        assertThat(register.getCodeStartNumbers()).isEqualTo("99");
    }

    @Test
    void execute_withDocumentRegisterIdAndManualNumbering_withoutSuppliedNumber_shouldFail() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        DocumentRegister register = newRegister(false, 1, "");
        UUID registerId = register.getId();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.of(register));

        var request = new TiepNhanVbdenRequest();
        request.setDocumentRegisterId(registerId);
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(addDynamicDocumentUseCase, never()).execute(any());
        verify(documentRegisterRepository, never()).save(any());
    }

    @Test
    void execute_withLockedDocumentRegister_shouldFail() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        DocumentRegister register = newRegister(true, 1, "");
        register.setLocked(true);
        UUID registerId = register.getId();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.of(register));

        var request = new TiepNhanVbdenRequest();
        request.setDocumentRegisterId(registerId);
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(addDynamicDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_withUnknownDocumentRegisterId_shouldFail() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        UUID registerId = UUID.randomUUID();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.empty());

        var request = new TiepNhanVbdenRequest();
        request.setDocumentRegisterId(registerId);
        request.setFields(new LinkedHashMap<>());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(addDynamicDocumentUseCase, never()).execute(any());
    }

    // R6-lite (22/08/2026, xem javadoc TiepNhanVbdenUseCase#applyDefaultAssignerAsProcessor) - da
    // xac nhan qua du lieu mau thuc te docs/views_domino/data_vbden_mau.txt: chi chon "Lanh dao
    // phan cong" (user_assigner), KHONG dung toi widget "Don vi/nguoi xu ly van ban" (user_process
    // rong) -> tu gan user_process = user_assigner, de van ban vao duoc hang doi "cho xu ly".
    @Test
    void execute_withAssignerButNoUserProcess_shouldDefaultUserProcessToAssigner() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        UUID id = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        var request = new TiepNhanVbdenRequest();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("assigner", "Le Phu Ha");
        fields.put("user_assigner", "lpha");
        fields.put("user_process", "");
        request.setFields(fields);

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_process", "lpha");
    }

    // Nguoc lai: widget DA gan ai do (user_process khac rong) -> KHONG ghi de, giu dung lua chon
    // thuc cua nguoi dung (co the khac assigner).
    @Test
    void execute_withAssignerAndUserProcessAlreadySet_shouldNotOverride() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TiepNhanVbdenRequest();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_assigner", "lpha");
        fields.put("user_process", "nva");
        request.setFields(fields);

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_process", "nva");
    }

    // Khong chon lanh dao phan cong -> khong co gi de default, user_process van rong.
    @Test
    void execute_withoutAssigner_shouldLeaveUserProcessEmpty() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TiepNhanVbdenRequest();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_process", "");
        request.setFields(fields);

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_process", "");
    }

    // Bo sung 03/09/2026 (bao loi nguoi dung: nut "Lay lai VB" khong bao gio hien vi Java chua tung
    // ghi field "author" - xem javadoc TiepNhanVbdenUseCase#AUTHOR_FIELD).
    @Test
    void execute_shouldSeedAuthorFieldWithCurrentUsername() {
        useCase = new TiepNhanVbdenUseCase(addDynamicDocumentUseCase, documentRegisterRepository, currentUserProvider);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TiepNhanVbdenRequest();
        request.setFields(new LinkedHashMap<>());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("author", "vanthu01");
    }

    private DocumentRegister newRegister(boolean autoIncrement, int codeStartNumber, String codeStartNumbers) {
        return new DocumentRegister("Sổ văn bản đến", "Văn bản đến", 2026, "G10", null, null, null, null,
            null, null, null, "Nguyễn Văn A", codeStartNumber, autoIncrement, true, codeStartNumbers);
    }
}
