package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbden.XinYKienDocument;
import com.td.domain.vbdi.VbdiDocument;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB) — xem javadoc {@link GuiXinYKienUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GuiXinYKienUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private XinYKienDocumentRepository xinYKienDocumentRepository;
    @Mock
    private AddXinYKienDynamicDocumentUseCase addXinYKienDynamicDocumentUseCase;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GuiXinYKienUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GuiXinYKienUseCase(
            vbdiDocumentRepository, xinYKienDocumentRepository, addXinYKienDynamicDocumentUseCase,
            dynamicDocumentFieldsService, documentCacheService, documentLogService);
        lenient().when(addXinYKienDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(xinYKienDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void execute_withUnknownSource_shouldReturnFailure() {
        UUID idvbdi = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(idvbdi)).thenReturn(Optional.empty());

        var request = new GuiXinYKienRequest();
        request.setIdvbdi(idvbdi);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbdiSource_shouldReturnFailure() {
        VbdiDocument source = newVbdiDocument("vbden", "{}");
        when(vbdiDocumentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        var request = new GuiXinYKienRequest();
        request.setIdvbdi(source.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutAnyRecipient_shouldReturnFailure() {
        VbdiDocument source = newVbdiDocument("vbdi", "{\"list_tvub_obj\":[]}");
        when(vbdiDocumentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        var request = new GuiXinYKienRequest();
        request.setIdvbdi(source.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldCreateNewXinYkienDocumentWithSelectedRecipientsAndCopiedFields() {
        VbdiDocument source = newVbdiDocument("vbdi", """
            {"list_tvub_obj":[
              {"identifier":"H56.09","xinykien":"1","username":"nguyenvana"},
              {"identifier":"H56.10","xinykien":"0","username":"nguyenvanb"}
            ],
            "subject":"Trinh xin y kien","confidentiality":"Thuong","priority":"Hoa toc",
            "promulgation_date":"27/08/2026","deadline_date":"28/08/2026",
            "unit_publish":"UBND tinh","unit_publish_identifier":"H56",
            "unit_in_charge":"Van phong UBND","code_full":"3157/VP-TDNC","creator":"Truong Thanh Quang"}
            """);
        when(vbdiDocumentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("id_documentout")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        var request = new GuiXinYKienRequest();
        request.setIdvbdi(source.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest created = captor.getValue();
        assertThat(created.getDocumentType()).isEqualTo("vbden_xinykien");
        assertThat(created.getFields()).containsEntry("id_documentout", source.getId().toString());
        assertThat(created.getFields()).containsEntry("unit_in_charge_identifier", "H56");
        assertThat(created.getFields()).containsEntry("unit_in_charge", "UBND tinh");
        assertThat(created.getFields()).containsEntry("unit_draftor", "Van phong UBND");
        assertThat(created.getFields()).containsEntry("code", "3157/VP-TDNC");
        assertThat(created.getFields()).containsEntry("subject", "Trinh xin y kien");
        assertThat(created.getFields()).containsEntry("is_tvub_opinion", "true");
        assertThat(created.getFields()).containsEntry("reply", "0");
        assertThat(created.getFields()).containsEntry("document_internet", "1");
        // status ("0") la default cua AddXinYKienDynamicDocumentUseCase khi request.getStatus() null -
        // GuiXinYKienUseCase khong tu set (dung tien le AddDuThaoDynamicDocumentUseCase), da co test
        // rieng cho use case do.
        // nguyenvana (xinykien=1) duoc them, nguyenvanb (xinykien=0) khong duoc them.
        assertThat(created.getFields().get("user_process")).contains("nguyenvana");
        assertThat(created.getFields().get("user_process")).doesNotContain("nguyenvanb");
    }

    @Test
    void execute_withNoibo_shouldReadListTvnbObjAndSetTvnbFlag() {
        VbdiDocument source = newVbdiDocument("vbdi", """
            {"list_tvnb_obj":[{"identifier":"H56.09","xinykien":"1","username":"nguyenvanc"}]}
            """);
        when(vbdiDocumentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("id_documentout")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        var request = new GuiXinYKienRequest();
        request.setIdvbdi(source.getId());
        request.setNoibo(true);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("is_tvnb_opinion", "true");
        assertThat(captor.getValue().getFields().get("user_process")).contains("nguyenvanc");
    }

    @Test
    void execute_whenAlreadySentBefore_shouldUpdateExistingDocumentWithoutResettingCreateOnlyFields() {
        VbdiDocument source = newVbdiDocument("vbdi", """
            {"list_tvub_obj":[{"identifier":"H56.09","xinykien":"1","username":"nguyenvanb"}]}
            """);
        when(vbdiDocumentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        XinYKienDocument existing = new XinYKienDocument("Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        existing.replaceDynamicFields(
            "{\"id_documentout\":\"" + source.getId() + "\",\"user_process\":[\"nguyenvana\"],"
                + "\"received_date\":\"01/01/2026 00:00:00\",\"reply\":\"0\"}");
        when(xinYKienDocumentRepository.findByAnyFieldContains(
                eq("vbden_xinykien"), eq(List.of("id_documentout")), eq(source.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(existing), PageRequest.of(0, 1), 1));

        var request = new GuiXinYKienRequest();
        request.setIdvbdi(source.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(existing.getDynamicFieldsJson());
        // nguoi cu van con, nguoi moi duoc them - khong bi ghi de mat.
        @SuppressWarnings("unchecked")
        List<String> userProcess = (List<String>) updatedFields.get("user_process");
        assertThat(userProcess).containsExactlyInAnyOrder("nguyenvana", "nguyenvanb");
        // field CHI set luc tao moi giu nguyen gia tri cu, khong bi ghi de lai luc cap nhat.
        assertThat(updatedFields.get("received_date")).isEqualTo("01/01/2026 00:00:00");
        verify(addXinYKienDynamicDocumentUseCase, org.mockito.Mockito.never()).execute(any());
    }

    private VbdiDocument newVbdiDocument(String documentType, String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("Van ban di", documentType, "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
