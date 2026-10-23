package com.td.application.vbdi;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.VbdiDeliveryFileRef;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.vbdi.VbdiSendLogDocument;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishNationalExchangeSendUseCaseTest {

    @Mock
    private OutboxPublisherService outboxPublisherService;
    @Mock
    private VbdiSendLogDocumentRepository sendLogRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private CurrentTenantProvider currentTenantProvider;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final DynamicDocumentFieldsService fields = new DynamicDocumentFieldsService();

    private PublishNationalExchangeSendUseCase useCase(boolean enabled) {
        lenient().when(currentTenantProvider.currentTenantId()).thenReturn(Optional.of("H05_01"));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu.skhcn"));
        return new PublishNationalExchangeSendUseCase(enabled, outboxPublisherService, sendLogRepository,
            vbdiDocumentRepository, fields, currentTenantProvider, currentUserProvider);
    }

    private VbdiDocument document(String json) {
        return new VbdiDocument("V/v liên thông", "vbdi", VbdiStatus.ISSUED, null, null, null, null)
            .replaceDynamicFields(json);
    }

    private List<PublishNationalExchangeSendUseCase.ExternalRecipient> recipients() {
        return List.of(
            new PublishNationalExchangeSendUseCase.ExternalRecipient(UUID.randomUUID(), "000.00.00.G22", "VPCP"),
            new PublishNationalExchangeSendUseCase.ExternalRecipient(UUID.randomUUID(), "000.00.00.H41", "Nghệ An"));
    }

    @Test
    void disabled_recordsFailureForEveryRecipientWithoutPublishing() {
        boolean published = useCase(false).execute(document("{}"), VbdiSendType.SEND_DOCUMENT, recipients(), List.of());

        assertThat(published).isFalse();
        ArgumentCaptor<VbdiSendLogDocument> captor = ArgumentCaptor.forClass(VbdiSendLogDocument.class);
        verify(sendLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo("Gửi lỗi");
            assertThat(row.getRecipientGroup()).isEqualTo("external_unit");
            assertThat(row.getContent()).contains("chưa bật kết nối Trục");
        });
        verifyNoInteractions(outboxPublisherService);
    }

    @Test
    void missingAgencyCode_recordsFailureWithReason() {
        boolean published = useCase(true).execute(document("{}"),
            VbdiSendType.SEND_DOCUMENT, recipients(), List.of());

        assertThat(published).isFalse();
        ArgumentCaptor<VbdiSendLogDocument> captor = ArgumentCaptor.forClass(VbdiSendLogDocument.class);
        verify(sendLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getContent()).contains("mã định danh điện tử");
        verifyNoInteractions(outboxPublisherService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabled_publishesOneUnitWithAllRecipientsAndAgencyCodeFromDocumentIdentifier() {
        var file = new VbdiDeliveryFileRef();
        file.setObjectKey("vbdi-delivery/a.pdf");
        file.setOriginalFilename("a.pdf");
        var recipients = recipients();

        boolean published = useCase(true).execute(document("""
                {"unit_publish_identifier":"H05.01.05","unit_publish":"Trung tâm CNTT","code_full":"12/TTCNTT",
                 "promulgation_date":"14/10/2026","priority":"Thượng khẩn","page_amount":3.0,
                 "delivery_method":"Điện tử và giấy","signer":"Nguyễn Văn A"}
                """), VbdiSendType.SEND_DOCUMENT, recipients, List.of(file));

        assertThat(published).isTrue();
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbdi"), any(), captor.capture());
        OutboundMessage message = captor.getValue();
        assertThat(message.taskType()).isEqualTo("guinhanquocgia.send");
        assertThat(message.units()).hasSize(1);
        Map<String, Object> payload = (Map<String, Object>) message.units().get(0).payload();
        assertThat(payload.get("senderAgencyCode")).isEqualTo("H05.01.05");
        assertThat(payload.get("senderTenantId")).isEqualTo("H05_01");
        assertThat(payload.get("docType")).isEqualTo("new");
        List<Map<String, Object>> sentRecipients = (List<Map<String, Object>>) payload.get("recipients");
        assertThat(sentRecipients).extracting(r -> r.get("sendLogId"))
            .containsExactly(recipients.get(0).sendLogId().toString(), recipients.get(1).sendLogId().toString());
        Map<String, Object> doc = (Map<String, Object>) payload.get("document");
        assertThat(doc.get("codeNumber")).isEqualTo("12");
        assertThat(doc.get("codeNotation")).isEqualTo("TTCNTT");
        assertThat(doc.get("priority")).isEqualTo(2);
        assertThat(doc.get("pageAmount")).isEqualTo(3);
        assertThat(doc.get("paper")).isEqualTo(true);
        assertThat((List<?>) payload.get("files")).hasSize(1);
    }

    @Test
    void revocationSendsNoFiles() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        useCase(true).execute(document("{\"unit_publish_identifier\":\"H05.01\",\"code_full\":\"1/QD\"}"),
            VbdiSendType.THU_HOI_TOAN_BO, recipients(), List.of(new VbdiDeliveryFileRef()));

        verify(outboxPublisherService).enqueue(eq("vbdi"), any(), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captor.getValue().units().get(0).payload();
        assertThat(payload.get("docType")).isEqualTo("revocation");
        assertThat((List<?>) payload.get("files")).isEmpty();
    }

    @Test
    void docTypeMapping() {
        assertThat(PublishNationalExchangeSendUseCase.docType(VbdiSendType.RESEND_DOCUMENT, Map.of())).isEqualTo("new");
        assertThat(PublishNationalExchangeSendUseCase.docType(VbdiSendType.GUI_BO_SUNG, Map.of())).isEqualTo("update");
        assertThat(PublishNationalExchangeSendUseCase.docType(VbdiSendType.LAY_LAI_VAN_BAN, Map.of())).isEqualTo("revocation");
        assertThat(PublishNationalExchangeSendUseCase.docType(VbdiSendType.UNIT_REVOKE,
            Map.of("replaces_document_id", UUID.randomUUID().toString()))).isEqualTo("replacement");
        assertThat(PublishNationalExchangeSendUseCase.docType(VbdiSendType.UNIT_REVOKE, Map.of())).isEqualTo("new");
    }

    @Test
    void codeSplitAndPriority() {
        assertThat(PublishNationalExchangeSendUseCase.splitCode(Map.of("document_number", 92.0, "code", "BC-SKHCN")))
            .containsExactly("92", "BC-SKHCN");
        assertThat(PublishNationalExchangeSendUseCase.splitCode(Map.of("code_full", "7/QĐ-UBND")))
            .containsExactly("7", "QĐ-UBND");
        assertThat(PublishNationalExchangeSendUseCase.priority("Hỏa tốc")).isEqualTo(3);
        assertThat(PublishNationalExchangeSendUseCase.priority("Khẩn")).isEqualTo(1);
        assertThat(PublishNationalExchangeSendUseCase.priority("Thường")).isZero();
    }
}
