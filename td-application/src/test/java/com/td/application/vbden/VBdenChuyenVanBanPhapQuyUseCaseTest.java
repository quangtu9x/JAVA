package com.td.application.vbden;

import com.td.application.common.email.EmailMessage;
import com.td.application.common.email.EmailSender;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N16 (03/09/2026 — "Chuyển VBQP") — xem javadoc {@link VBdenChuyenVanBanPhapQuyUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class VBdenChuyenVanBanPhapQuyUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @Mock
    private EmailSender emailSender;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private VBdenChuyenVanBanPhapQuyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VBdenChuyenVanBanPhapQuyUseCase(
            documentRepository, addDynamicDocumentUseCase, dynamicDocumentFieldsService,
            listDocumentFilesUseCase, emailSender, documentLogService, documentCacheService);
        lenient().when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 1, 20, 0L, 0, true, true));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new VBdenChuyenVanBanPhapQuyRequest();
        request.setIdqlvb(id);
        request.setMailin("vbqp@example.com");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verify(emailSender, never()).send(any());
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument document = newDocument("vbden_res", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new VBdenChuyenVanBanPhapQuyRequest();
        request.setIdqlvb(document.getId());
        request.setMailin("vbqp@example.com");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verify(emailSender, never()).send(any());
    }

    @Test
    void execute_withBlankMailin_shouldReturnFailureWithoutSendingEmail() {
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new VBdenChuyenVanBanPhapQuyRequest();
        request.setIdqlvb(document.getId());
        request.setMailin(" ; ");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(emailSender, never()).send(any());
    }

    @Test
    void execute_shouldCreateVbpqDocumentWithMappedFields() {
        BusinessDocument document = newDocument("vbden",
            "{\"code\":\"123/QD-UBND\",\"unit_in_charge\":\"So Noi vu\",\"sector\":\"Noi vu\","
                + "\"promulgation_date\":\"01/09/2026\",\"signer\":\"Nguyen Van A\","
                + "\"signer_position\":\"Giam doc\",\"document_type\":\"Quyet dinh\"}");
        document.update("Quyet dinh ve viec ABC", null, null, null, null, null, null);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(emailSender.send(any())).thenReturn(Result.success(null));

        var request = new VBdenChuyenVanBanPhapQuyRequest();
        request.setIdqlvb(document.getId());
        request.setMailin("vbqp@example.com");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_vbpq");
        assertThat(childRequest.getFields()).containsEntry("idqlvb", document.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("OutNum", "123");
        assertThat(childRequest.getFields()).containsEntry("OutSymbol", "QD-UBND");
        assertThat(childRequest.getFields()).containsEntry("isuse", "So Noi vu");
        assertThat(childRequest.getFields()).containsEntry("OutLinhVuc", "Noi vu");
        assertThat(childRequest.getFields()).containsEntry("OutSigner", "Nguyen Van A");
        assertThat(childRequest.getFields()).containsEntry("OutSigner_Pos", "Giam doc");
        assertThat(childRequest.getFields()).containsEntry("OutType", "Quyet dinh");
    }

    @Test
    void execute_shouldSendEmailWithAttachmentsToMailinAndRecordSentField() {
        BusinessDocument document = newDocument("vbden", "{\"code\":\"123/QD-UBND\"}");
        document.update("Quyet dinh ve viec ABC", null, null, null, null, null, null);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        UUID fileId = UUID.randomUUID();
        FileDto file = FileDto.builder().fileId(fileId).documentId(document.getId())
            .fileName("stored.pdf").originalFileName("vbden.pdf").build();
        when(listDocumentFilesUseCase.execute(document.getId()))
            .thenReturn(new PaginationResponse<>(List.of(file), 1, 20, 1L, 1, true, true));
        when(emailSender.send(any())).thenReturn(Result.success(null));

        var request = new VBdenChuyenVanBanPhapQuyRequest();
        request.setIdqlvb(document.getId());
        request.setMailin("vbqp@example.com");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        EmailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("vbqp@example.com");
        assertThat(sent.getSubject()).contains("123/QD-UBND");
        assertThat(sent.getAttachments()).hasSize(1);
        assertThat(sent.getAttachments().get(0).getFileId()).isEqualTo(fileId);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("vbqp_sent_to", "vbqp@example.com");
        verify(documentLogService).logUpdate(any(), any(), any());
    }

    @Test
    void execute_whenEmailSendFails_shouldReturnFailureAndNotRecordSentField() {
        BusinessDocument document = newDocument("vbden", "{}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(emailSender.send(any())).thenReturn(Result.failure("SMTP loi"));

        var request = new VBdenChuyenVanBanPhapQuyRequest();
        request.setIdqlvb(document.getId());
        request.setMailin("vbqp@example.com");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("SMTP loi");
        verify(documentRepository, never()).save(any());
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
