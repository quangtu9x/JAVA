package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbden.XinYKienDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
 * 18/09/2026 (cutover "Gửi xin ý kiến từ vbden") — xem javadoc {@link GuiXinYKienTuVBdenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GuiXinYKienTuVBdenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private XinYKienDocumentRepository xinYKienDocumentRepository;
    @Mock
    private AddXinYKienDynamicDocumentUseCase addXinYKienDynamicDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GuiXinYKienTuVBdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GuiXinYKienTuVBdenUseCase(
            documentRepository, xinYKienDocumentRepository, addXinYKienDynamicDocumentUseCase,
            dynamicDocumentFieldsService, currentUserProvider, appUserRepository, documentCacheService,
            documentLogService);
        lenient().when(addXinYKienDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(xinYKienDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newUser("vanthu01", "Nguyen Van Thu")));
    }

    @Test
    void execute_withUnknownSource_shouldReturnFailure() {
        UUID idvbden = UUID.randomUUID();
        when(documentRepository.findById(idvbden)).thenReturn(Optional.empty());

        var request = new GuiXinYKienTuVBdenRequest();
        request.setIdvbden(idvbden);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbdenSource_shouldReturnFailure() {
        BusinessDocument source = newVbdenDocument("vbdi", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        var request = new GuiXinYKienTuVBdenRequest();
        request.setIdvbden(source.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldCreateNewXinYkienDocumentWithSelectedRecipientsAndCopiedFields() {
        BusinessDocument source = newVbdenDocument("vbden", """
            {"code":"3157/VP-TDNC","unit_in_charge":"Van phong UBND","status":"1",
            "lanhdaochutri":"Ong A","document_type":"Cong van","subject":"Trinh xin y kien",
            "confidentiality":"Thuong","priority":"Hoa toc","promulgation_date":"27/08/2026",
            "deadline_date":"28/08/2026","draftor":"Chuyen vien B"}
            """);
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("vbden_id")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        var request = new GuiXinYKienTuVBdenRequest();
        request.setIdvbden(source.getId());
        request.setContent("De nghi cho y kien ve du thao");
        request.setDeadlineDate("30/09/2026");
        request.setListTvubObj("""
            [{"identifier":"H56.09","xinykien":"1","username":"nguyenvana"},
             {"identifier":"H56.10","xinykien":"0","username":"nguyenvanb"}]
            """);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        Map<String, String> created = captor.getValue().getFields();
        assertThat(created).containsEntry("vbden_id", source.getId().toString());
        assertThat(created).containsEntry("code", "3157/VP-TDNC");
        assertThat(created).containsEntry("unit_in_charge", "Van phong UBND");
        assertThat(created).containsEntry("lanhdaochutri", "Ong A");
        assertThat(created).containsEntry("subject", "Trinh xin y kien");
        assertThat(created).containsEntry("is_tvnb_opinion", "true");
        assertThat(created).containsEntry("status", "0");
        assertThat(created).containsEntry("document_internet", "1");
        assertThat(created).containsEntry("reply", "0");
        assertThat(created).containsEntry("content", "De nghi cho y kien ve du thao");
        assertThat(created).containsEntry("deadline_date", "30/09/2026");
        assertThat(created.get("user_process")).contains("nguyenvana");
        assertThat(created.get("user_process")).doesNotContain("nguyenvanb");
        assertThat(created).containsEntry("user_creator", "vanthu01");
        assertThat(created).containsEntry("creator", "Nguyen Van Thu");

        // Ghi lai xinykien_id len van ban vbden NGUON.
        ArgumentCaptor<BusinessDocument> sourceCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(sourceCaptor.capture());
        Map<String, Object> savedSourceFields = dynamicDocumentFieldsService.parseDynamicFields(
            sourceCaptor.getValue().getDynamicFieldsJson());
        assertThat(savedSourceFields).containsKey("xinykien_id");
    }

    @Test
    void execute_whenAlreadySentBefore_shouldUpdateExistingDocumentWithoutResettingCreateOnlyFields() {
        BusinessDocument source = newVbdenDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        XinYKienDocument existing = new XinYKienDocument("Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        existing.replaceDynamicFields("{"
            + "\"vbden_id\":\"" + source.getId() + "\",\"user_process\":[\"nguyenvana\"],"
            + "\"received_date\":\"01/01/2026 00:00:00\",\"reply\":\"0\","
            + "\"list_tvub_obj\":[{\"identifier\":\"H56.09\",\"xinykien\":\"1\",\"username\":\"nguyenvana\","
            + "\"username_send\":\"nguoicu\",\"time\":\"01/01/2026 08:00:00\"}]}");
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("vbden_id")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(existing), PageRequest.of(0, 1), 1));

        var request = new GuiXinYKienTuVBdenRequest();
        request.setIdvbden(source.getId());
        // FE (YKienTVUBTable.jsx#handleSaveXinYKien) luon fetch TRUOC roi resend TOAN BO state (giu
        // nguyen username_send/time da fetch duoc) - nguyenvana giu nguyen node cu (co username_send),
        // nguyenvanc la nguoi MOI (node chua co username_send/time).
        request.setListTvubObj("""
            [{"identifier":"H56.09","xinykien":"1","username":"nguyenvana",
              "username_send":"nguoicu","time":"01/01/2026 08:00:00"},
             {"identifier":"H56.11","xinykien":"1","username":"nguyenvanc"}]
            """);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(existing.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<String> userProcess = (List<String>) updatedFields.get("user_process");
        assertThat(userProcess).containsExactlyInAnyOrder("nguyenvana", "nguyenvanc");
        // Field chi set luc tao moi giu nguyen, khong bi ghi de lai luc cap nhat.
        assertThat(updatedFields.get("received_date")).isEqualTo("01/01/2026 00:00:00");
        // Nguoi da gui truoc do (username_send) KHONG bi ghi de - dung "lan gui dau tien".
        assertThat(updatedFields.get("list_tvub_obj").toString()).contains("nguoicu");
        // Nguoi MOI (chua co username_send/time) duoc set lan dau.
        assertThat(updatedFields.get("list_tvub_obj").toString()).contains("vanthu01");
        verify(addXinYKienDynamicDocumentUseCase, never()).execute(any());
    }

    // Fallback: request gui listTvubObj RONG va da co document cu -> doc lai list_tvub_obj HIEN CO
    // tren document do (dung Domino "gui lai khong doi danh sach").
    @Test
    void execute_withBlankListTvubObjAndExistingDocument_shouldFallbackToStoredList() {
        BusinessDocument source = newVbdenDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        XinYKienDocument existing = new XinYKienDocument("Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        existing.replaceDynamicFields("{"
            + "\"vbden_id\":\"" + source.getId() + "\",\"user_process\":[],"
            + "\"list_tvub_obj\":[{\"identifier\":\"H56.09\",\"xinykien\":\"1\",\"username\":\"nguyenvana\"}]}");
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("vbden_id")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(existing), PageRequest.of(0, 1), 1));

        var request = new GuiXinYKienTuVBdenRequest();
        request.setIdvbden(source.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(existing.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<String> userProcess = (List<String>) updatedFields.get("user_process");
        assertThat(userProcess).contains("nguyenvana");
    }

    // "content" khac gia tri cu -> noi lich su "cu-->moi" vao "contents" truoc khi ghi de - dung Domino.
    @Test
    void execute_whenContentChanges_shouldAppendHistory() {
        BusinessDocument source = newVbdenDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        XinYKienDocument existing = new XinYKienDocument("Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        existing.replaceDynamicFields("{\"vbden_id\":\"" + source.getId() + "\",\"content\":\"Noi dung cu\"}");
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("vbden_id")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(existing), PageRequest.of(0, 1), 1));

        var request = new GuiXinYKienTuVBdenRequest();
        request.setIdvbden(source.getId());
        request.setContent("Noi dung moi");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(existing.getDynamicFieldsJson());
        assertThat(updatedFields.get("content")).isEqualTo("Noi dung moi");
        assertThat(updatedFields.get("contents")).isEqualTo("Noi dung cu-->Noi dung moi");
    }

    private BusinessDocument newVbdenDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban den", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private AppUser newUser(String username, String fullName) {
        return new AppUser("kc-" + username, username, fullName, null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
    }
}
