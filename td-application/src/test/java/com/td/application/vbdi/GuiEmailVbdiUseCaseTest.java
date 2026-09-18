package com.td.application.vbdi;

import com.td.application.common.email.EmailMessage;
import com.td.application.common.email.EmailSender;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.vbdi.VbdiDocument;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): gửi văn bản đi qua email công vụ qua
 * {@link GuiEmailVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GuiEmailVbdiUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @Mock
    private EmailSender emailSender;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GuiEmailVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GuiEmailVbdiUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, listDocumentFilesUseCase, emailSender,
            documentLogService, documentCacheService);
    }

    @Test
    void execute_withUnknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GuiEmailVbdiRequest();
        request.setId(id);
        request.setToEmails(List.of("a@example.com"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(emailSender, never()).send(any());
    }

    @Test
    void execute_whenNotIssuedYet_shouldReturnFailure() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.PENDING_SIGNATURE);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GuiEmailVbdiRequest();
        request.setId(document.getId());
        request.setToEmails(List.of("a@example.com"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(emailSender, never()).send(any());
    }

    @Test
    void execute_shouldSendEmailWithAttachmentsAndRecordSentDate() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.ISSUED);
        document.replaceDynamicFields("{\"document_number\":\"5/2026-VBD\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID fileId = UUID.randomUUID();
        FileDto file = FileDto.builder().fileId(fileId).documentId(document.getId())
            .fileName("stored.pdf").originalFileName("vbdi.pdf").build();
        when(listDocumentFilesUseCase.execute(document.getId()))
            .thenReturn(new PaginationResponse<>(List.of(file), 1, 20, 1L, 1, true, true));
        when(emailSender.send(any())).thenReturn(Result.success(null));

        var request = new GuiEmailVbdiRequest();
        request.setId(document.getId());
        request.setToEmails(List.of("nguoinhan@example.com"));
        request.setMessage("Kinh gui van ban");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        EmailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("nguoinhan@example.com");
        assertThat(sent.getSubject()).contains("5/2026-VBD");
        assertThat(sent.getAttachments()).hasSize(1);
        assertThat(sent.getAttachments().get(0).getFileId()).isEqualTo(fileId);
        assertThat(sent.getAttachments().get(0).getFileName()).isEqualTo("vbdi.pdf");

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("email_sent_to", "nguoinhan@example.com");
        verify(documentLogService).logUpdate(any(), any(), any());
    }

    @Test
    void execute_whenEmailSendFails_shouldNotRecordSentDate() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.ISSUED);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(listDocumentFilesUseCase.execute(document.getId()))
            .thenReturn(new PaginationResponse<>(List.of(), 1, 20, 0L, 0, true, true));
        when(emailSender.send(any())).thenReturn(Result.failure("SMTP loi"));

        var request = new GuiEmailVbdiRequest();
        request.setId(document.getId());
        request.setToEmails(List.of("a@example.com"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(vbdiDocumentRepository, never()).save(any());
    }

    private VbdiDocument newDocument(String documentType, String status) {
        VbdiDocument document = new VbdiDocument("Van ban di", documentType, status, null, null, null, null);
        document.replaceDynamicFields("{}");
        return document;
    }
}
