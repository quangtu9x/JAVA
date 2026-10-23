package com.td.application.vbdi;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 07/09/2026 (yêu cầu người dùng: hoàn thiện broker phản hồi trạng thái "vào sổ"/"phân xử
 * lý" — xem javadoc {@link PublishVbdiReplyStatusUseCase}).
 */
@ExtendWith(MockitoExtension.class)
class PublishVbdiReplyStatusUseCaseTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OutboxPublisherService outboxPublisherService;
    @Mock
    private CurrentTenantProvider currentTenantProvider;

    private PublishVbdiReplyStatusUseCase useCase;

    private PublishVbdiReplyStatusUseCase newUseCase() {
        return new PublishVbdiReplyStatusUseCase(organizationRepository, outboxPublisherService, currentTenantProvider,
            new PublishNationalStatusUseCase(true, outboxPublisherService, currentTenantProvider));
    }

    private Map<String, Object> validFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("send_log_id", "sl-1");
        fields.put("sender_identifier", "H05.01");
        fields.put("parentid_send", "f9803543-75e8-4e01-a53f-f6ce3e249e63");
        fields.put("contact_unit_identifier", "H05.02");
        return fields;
    }

    private Organization organizationLinkedTo(String tenantId) {
        var org = new Organization("H05.01", "So Khoa hoc va Cong nghe", null, null,
            null, 0, null, 0, null, null, null, null, null, null, null, null, null, null);
        org.updateLinkedTenant(tenantId);
        return org;
    }

    /** To chuc TON TAI (vd don vi noi bo cung tenant) nhung KHONG co linked_tenant_id rieng. */
    private Organization organizationWithoutLinkedTenant() {
        return new Organization("H05.01", "So Khoa hoc va Cong nghe", null, null,
            null, 0, null, 0, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void execute_withValidSendLogAndLinkedTenant_shouldEnqueueOutboxMessage() {
        useCase = newUseCase();
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01"))
            .thenReturn(Optional.of(organizationLinkedTo("h05_01_sokhcn")));

        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(validFields());
        request.setStatusCode("03");
        request.setStatusLabel("Đã tiếp nhận");
        request.setActorDisplayName("vanthu01");

        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbdi_reply"), any(), captor.capture());
        var message = captor.getValue();
        assertThat(message.taskType()).isEqualTo("vbdi.reply");
        assertThat(message.units()).hasSize(1);
        var unit = message.units().get(0);
        assertThat(unit.tenantId()).isEqualTo("h05_01_sokhcn");
        var payload = (VbdiReplyMessagePayload) unit.payload();
        assertThat(payload.getSendLogId()).isEqualTo("sl-1");
        assertThat(payload.getDocumentId()).isEqualTo("f9803543-75e8-4e01-a53f-f6ce3e249e63");
        assertThat(payload.getStatusCode()).isEqualTo("03");
        assertThat(payload.getStatusLabel()).isEqualTo("Đã tiếp nhận");
        assertThat(payload.getRecipientIdentifier()).isEqualTo("H05.02");
        assertThat(payload.getActorDisplayName()).isEqualTo("vanthu01");
    }

    @Test
    void execute_withoutSendLogId_shouldSkipSilently() {
        useCase = newUseCase();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sender_identifier", "H05.01"); // co sender nhung khong co send_log_id

        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(fields);
        request.setStatusCode("03");

        useCase.execute(request);

        verifyNoInteractions(organizationRepository, outboxPublisherService);
    }

    @Test
    void execute_withNullDynamicFields_shouldSkipSilently() {
        useCase = newUseCase();
        var request = new PublishVbdiReplyStatusRequest();
        request.setStatusCode("03");

        useCase.execute(request);

        verifyNoInteractions(organizationRepository, outboxPublisherService);
    }

    /** KHONG tim thay to chuc nao voi identifier do - khong co gi de suy luan them, bo qua. */
    @Test
    void execute_whenOrganizationNotFound_shouldSkipWithoutEnqueue() {
        useCase = newUseCase();
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01")).thenReturn(Optional.empty());

        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(validFields());
        request.setStatusCode("03");

        useCase.execute(request);

        verify(outboxPublisherService, never()).enqueue(any(), any(), any());
        verifyNoInteractions(currentTenantProvider);
    }

    /**
     * Bugfix 07/09/2026 (5, báo lỗi người dùng: publish "im lặng" không log gì, Worker không nhận
     * được message nào) — tổ chức TỒN TẠI nhưng KHÔNG có linked_tenant_id riêng (đúng nghĩa
     * "internal_unit" — đơn vị nội bộ CÙNG tenant với đơn vị nhận) — PHẢI fallback về tenant HIỆN
     * TẠI, KHÔNG được bỏ qua im lặng như quyết định SAI trước đó.
     */
    @Test
    void execute_whenOrganizationExistsWithoutLinkedTenant_shouldFallbackToCurrentTenant() {
        useCase = newUseCase();
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01"))
            .thenReturn(Optional.of(organizationWithoutLinkedTenant()));
        when(currentTenantProvider.currentTenantId()).thenReturn(Optional.of("h05_02_snnmt"));

        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(validFields());
        request.setStatusCode("03");
        request.setStatusLabel("Đã tiếp nhận");

        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbdi_reply"), any(), captor.capture());
        var unit = captor.getValue().units().get(0);
        assertThat(unit.tenantId()).isEqualTo("h05_02_snnmt");
    }

    /** Khong co linked_tenant_id VA khong xac dinh duoc tenant hien tai - het cach, bo qua. */
    @Test
    void execute_whenOrganizationExistsWithoutLinkedTenantAndNoCurrentTenant_shouldSkipWithoutEnqueue() {
        useCase = newUseCase();
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01"))
            .thenReturn(Optional.of(organizationWithoutLinkedTenant()));
        when(currentTenantProvider.currentTenantId()).thenReturn(Optional.empty());

        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(validFields());
        request.setStatusCode("03");

        useCase.execute(request);

        verify(outboxPublisherService, never()).enqueue(any(), any(), any());
    }

    /** Lỗi bất kỳ (vd outbox lỗi) KHÔNG được ném ra ngoài — best-effort, giống AddVbdiSendLogUseCase. */
    @Test
    void execute_whenOutboxThrows_shouldNotPropagateException() {
        useCase = newUseCase();
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01"))
            .thenReturn(Optional.of(organizationLinkedTo("h05_01_sokhcn")));
        when(outboxPublisherService.enqueue(any(), any(), any())).thenThrow(new RuntimeException("broker down"));

        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(validFields());
        request.setStatusCode("03");

        useCase.execute(request); // khong duoc nem exception
    }

    /**
     * 14/10/2026: văn bản đến TỪ TRỤC QUỐC GIA (send_log_id "national:...") — phản hồi qua TD Gateway
     * (guinhanquocgia.status.send, mã nội bộ + ResponseFor), không tra bảng tổ chức, không phát vbdi.reply.
     */
    @Test
    @SuppressWarnings("unchecked")
    void execute_nationalIncoming_shouldPublishNationalStatusSend() {
        useCase = newUseCase();
        when(currentTenantProvider.currentTenantId()).thenReturn(Optional.of("H05_21"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("send_log_id", "national:truc-doc-1");
        fields.put("parentid_send", "truc-doc-1");
        fields.put("sender_identifier", "G12.98.24.05");
        fields.put("contact_unit_identifier", "H05.21.35");
        fields.put("code", "12/BHXH");
        fields.put("promulgation_date", "2026-10-14");
        fields.put("national_document_id", "G12.98.24.05,2026/10/14,12/BHXH");
        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(fields);
        request.setStatusCode("03");
        request.setStatusLabel("Đã tiếp nhận");
        request.setActorDisplayName("Văn thư");

        useCase.execute(request);

        verifyNoInteractions(organizationRepository);
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbdi_reply"), any(), captor.capture());
        assertThat(captor.getValue().taskType()).isEqualTo("guinhanquocgia.status.send");
        Map<String, Object> payload = (Map<String, Object>) captor.getValue().units().get(0).payload();
        assertThat(payload.get("senderAgencyCode")).isEqualTo("H05.21.35");
        assertThat(payload.get("recipientAgencyCode")).isEqualTo("G12.98.24.05");
        Map<String, Object> status = (Map<String, Object>) payload.get("status");
        assertThat(status.get("statusCode")).isEqualTo("03");
        assertThat(status.get("responseForDocumentId")).isEqualTo("G12.98.24.05,2026/10/14,12/BHXH");
        assertThat(status.get("responseForCode")).isEqualTo("12/BHXH");
    }

    @Test
    void execute_nationalIncomingWhenDisabled_shouldDoNothing() {
        useCase = new PublishVbdiReplyStatusUseCase(organizationRepository, outboxPublisherService, currentTenantProvider,
            new PublishNationalStatusUseCase(false, outboxPublisherService, currentTenantProvider));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("send_log_id", "national:truc-doc-1");
        fields.put("sender_identifier", "G12.98.24.05");
        var request = new PublishVbdiReplyStatusRequest();
        request.setDynamicFields(fields);
        request.setStatusCode("06");

        useCase.execute(request);

        verifyNoInteractions(outboxPublisherService, organizationRepository);
    }
}
