package com.td.application.vbdi;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.vbdi.VbdiSendLogDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác"): test
 * {@link AddVbdiSendLogUseCase} ghi vết vào {@code vbdi_send_log_documents}.
 */
@ExtendWith(MockitoExtension.class)
class AddVbdiSendLogUseCaseTest {

    @Mock
    private VbdiSendLogDocumentRepository vbdiSendLogDocumentRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentTenantProvider currentTenantProvider;
    @Mock
    private OutboxPublisherService outboxPublisherService;

    private AddVbdiSendLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddVbdiSendLogUseCase(
            vbdiSendLogDocumentRepository, vbdiDocumentRepository, currentUserProvider, documentLogService,
            new DynamicDocumentFieldsService(), organizationRepository, appUserRepository, currentTenantProvider,
            outboxPublisherService);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xuanlt.skhcn"));
        lenient().when(currentTenantProvider.currentTenantId()).thenReturn(Optional.of("skhcn"));
        lenient().when(organizationRepository.findByIdentifierAndDeletedOnIsNull(any()))
            .thenReturn(Optional.empty());
    }

    private VbdiDocument existingVbdi(String documentType) {
        return new VbdiDocument("Cong van", documentType, VbdiStatus.ISSUED, null, null, null, null);
    }

    private AddVbdiSendLogRequest request(UUID parentId) {
        var request = new AddVbdiSendLogRequest();
        request.setParentId(parentId);
        request.setRecipientGroup(VbdiSendRecipientGroup.COORDINATOR);
        request.setRecipientIdentifier("hoangnt.skhcn");
        request.setRecipientName("Nong Thanh Hoang");
        return request;
    }

    @Test
    void execute_shouldSaveEntryWithServerStampedUserCreator() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<VbdiSendLogDocument> captor = ArgumentCaptor.forClass(VbdiSendLogDocument.class);
        verify(vbdiSendLogDocumentRepository).save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getParentId()).isEqualTo(document.getId());
        assertThat(entity.getRecipientGroup()).isEqualTo(VbdiSendRecipientGroup.COORDINATOR);
        assertThat(entity.getSendType()).isEqualTo(VbdiSendType.SEND_DOCUMENT);
        // Danh tinh thuc do server gan (SERVER-AUTHORITATIVE).
        assertThat(entity.getUserCreator()).isEqualTo("xuanlt.skhcn");
        verify(documentLogService).logCreate(org.mockito.ArgumentMatchers.eq(saved.getId()), org.mockito.ArgumentMatchers.anyMap());
    }

    /**
     * Yêu cầu người dùng 04/09/2026 ("hoàn thiện Gửi văn bản để publish RabbitMQ/Kafka"): sau khi
     * ghi Nhật ký gửi nhận thành công, PHẢI enqueue outbox envelope {@code vbdi.delivery} — đúng 1
     * unit, taskType/sendLogId/recipient khớp dòng vừa ghi.
     */
    @Test
    void execute_shouldEnqueueVbdiDeliveryOutboxMessage() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        useCase.execute(request(document.getId()));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(org.mockito.ArgumentMatchers.eq("vbdi"),
            org.mockito.ArgumentMatchers.eq(saved.getId()), captor.capture());
        var message = captor.getValue();
        assertThat(message.taskType()).isEqualTo("vbdi.delivery");
        assertThat(message.units()).hasSize(1);
        var unit = message.units().get(0);
        assertThat(unit.tenantId()).isEqualTo("skhcn");
        var payload = (VbdiDeliveryMessagePayload) unit.payload();
        assertThat(payload.getSendLogId()).isEqualTo(saved.getId().toString());
        assertThat(payload.getDocumentId()).isEqualTo(document.getId().toString());
        assertThat(payload.getRecipientGroup()).isEqualTo(VbdiSendRecipientGroup.COORDINATOR);
        assertThat(payload.getRecipientIdentifier()).isEqualTo("hoangnt.skhcn");
        assertThat(payload.getSenderTenantId()).isEqualTo("skhcn");
    }

    /**
     * Bổ sung 08/09/2026 (người dùng báo lỗi thật: bấm "Lấy lại VB" — log td-worker báo DONE nhưng
     * văn bản đến của đơn vị VẪN còn nguyên trong danh sách): 3 sendType họ "thu hồi"/"lấy lại"
     * ({@link VbdiSendType#THU_HOI_VAN_BAN}/{@link VbdiSendType#LAY_LAI_VAN_BAN}/
     * {@link VbdiSendType#THU_HOI_TOAN_BO}) PHẢI enqueue taskType {@code vbdi.revoke} (route sang
     * {@code VbdiRevokeTaskHandler}, td-woker), KHÔNG PHẢI {@code vbdi.delivery} như 1 lượt gửi mới
     * (Domino {@code RevokeDocumentOut.java} gửi mail form {@code feedback_DocumentOut}, KHÁC hẳn
     * {@code documentin}/{@code SendDocument.java} — xem javadoc {@code AddVbdiSendLogUseCase
     * #REVOKE_FAMILY_SEND_TYPES}).
     */
    @Test
    void execute_revokeFamilySendTypes_shouldEnqueueVbdiRevokeTaskTypeInstead() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.LAY_LAI_VAN_BAN,
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.02", "So NN&MT", null, "Lay lai ngay", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var request = request(document.getId());
        request.setSendType(VbdiSendType.LAY_LAI_VAN_BAN);
        request.setRecipientGroup(VbdiSendRecipientGroup.CONTACT_UNIT);
        request.setRecipientIdentifier("H05.02");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        assertThat(captor.getValue().taskType()).isEqualTo("vbdi.revoke");
    }

    /**
     * 14/10/2026: nơi nhận ngoài tỉnh đi qua Trục quốc gia (PublishNationalExchangeSendUseCase) — KHÔNG phát
     * vbdi.delivery/vbdi.revoke (vbdi.revoke chạy ở tenant đơn vị gửi từng đánh dấu nhầm văn bản đến nội bộ).
     */
    @Test
    void execute_externalUnit_shouldNotEnqueueDeliveryOrRevoke() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.THU_HOI_TOAN_BO,
            VbdiSendRecipientGroup.EXTERNAL_UNIT, "000.00.00.G22", "VPCP", null, "Lay lai", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var request = request(document.getId());
        request.setSendType(VbdiSendType.THU_HOI_TOAN_BO);
        request.setRecipientGroup(VbdiSendRecipientGroup.EXTERNAL_UNIT);
        request.setRecipientIdentifier("000.00.00.G22");
        assertThat(useCase.execute(request).isSuccess()).isTrue();

        verifyNoInteractions(outboxPublisherService);
    }

    /** {@link VbdiSendType#THU_HOI_VAN_BAN}/{@link VbdiSendType#THU_HOI_TOAN_BO} — cùng cơ chế. */
    @Test
    void execute_thuHoiVanBanAndThuHoiToanBo_shouldAlsoEnqueueVbdiRevokeTaskType() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.THU_HOI_VAN_BAN,
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.02", "So NN&MT", null, "Thu hoi ngay", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var request = request(document.getId());
        request.setSendType(VbdiSendType.THU_HOI_VAN_BAN);
        request.setRecipientGroup(VbdiSendRecipientGroup.CONTACT_UNIT);
        request.setRecipientIdentifier("H05.02");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        assertThat(captor.getValue().taskType()).isEqualTo("vbdi.revoke");
    }

    /** {@link VbdiSendType#UNIT_REVOKE} ("gửi văn bản thay thế/thu hồi") VẪN thuộc họ GỬI - KHÔNG đổi. */
    @Test
    void execute_unitRevokeSendType_stillEnqueuesVbdiDeliveryNotRevoke() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.UNIT_REVOKE,
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.02", "So NN&MT", null, "Gui van ban thay the/thu hoi", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var request = request(document.getId());
        request.setSendType(VbdiSendType.UNIT_REVOKE);
        request.setRecipientGroup(VbdiSendRecipientGroup.CONTACT_UNIT);
        request.setRecipientIdentifier("H05.02");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        assertThat(captor.getValue().taskType()).isEqualTo("vbdi.delivery");
    }

    /**
     * Bugfix 06/09/2026 (nhầm nhóm ban đầu — xem javadoc {@code resolveTargetTenantId}):
     * {@code contact_unit} (cơ quan/đơn vị KHÁC thuộc tỉnh) phải tra tenant đích THẬT qua
     * {@code Organization.linkedTenantId}, KHÔNG dùng placeholder tenant người gửi.
     */
    @Test
    void execute_contactUnit_shouldResolveRealLinkedTenant() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.01", "So Khoa hoc va Cong nghe", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);
        var org = new Organization("H05.01", "So Khoa hoc va Cong nghe", null, null,
            null, 0, null, 0, null, null, null, null, null, null, null, null, null, null);
        org.updateLinkedTenant("h05_01");
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01")).thenReturn(Optional.of(org));

        var request = request(document.getId());
        request.setRecipientGroup(VbdiSendRecipientGroup.CONTACT_UNIT);
        request.setRecipientIdentifier("H05.01");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        assertThat(captor.getValue().units().get(0).tenantId()).isEqualTo("h05_01");
    }

    /**
     * {@code internal_unit} (đơn vị nội bộ CỦA CHÍNH cơ quan gửi, không "thuộc tỉnh") vẫn dùng
     * placeholder tenant người gửi — KHÔNG đổi hành vi (đã đổi placeholder từ internal_unit sang
     * contact_unit ở mục trên, internal_unit giữ nguyên như "external_unit").
     */
    @Test
    void execute_internalUnit_shouldUseSenderTenantAsPlaceholder() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.INTERNAL_UNIT, "phong-a", "Phong A", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var request = request(document.getId());
        request.setRecipientGroup(VbdiSendRecipientGroup.INTERNAL_UNIT);
        request.setRecipientIdentifier("phong-a");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        assertThat(captor.getValue().units().get(0).tenantId()).isEqualTo("skhcn");
    }

    /**
     * Bổ sung 18/09/2026 (hoàn thiện "phát hành vbdi → đến nội bộ"): {@code internal_unit} PHẢI
     * resolve {@code recipientReceiverUsername} qua {@code Organization.receiverId} →
     * {@code AppUser.username} (khớp Domino {@code dbqlvb.getDocumentByUNID(id)} lấy
     * {@code user_receiver}) — xem javadoc {@code AddVbdiSendLogUseCase#resolveReceiverUsername}.
     */
    @Test
    void execute_internalUnit_shouldResolveReceiverUsernameFromOrganizationReceiver() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.INTERNAL_UNIT, "H05.01.02", "Phong Cong nghe", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var receiver = new AppUser("kc-1", "vanthu.pcn", "Van Thu Phong CN", null, null, null, true);
        when(appUserRepository.findByIdAndDeletedOnIsNull(receiver.getId())).thenReturn(Optional.of(receiver));
        var org = new Organization("H05.01.02", "Phong Cong nghe", null, null,
            "department", 4, null, 0, null, receiver.getId().toString(), null, null, null, null, null, null, null, null);
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01.02")).thenReturn(Optional.of(org));

        var request = request(document.getId());
        request.setRecipientGroup(VbdiSendRecipientGroup.INTERNAL_UNIT);
        request.setRecipientIdentifier("H05.01.02");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        var payload = (VbdiDeliveryMessagePayload) captor.getValue().units().get(0).payload();
        assertThat(payload.getRecipientReceiverUsername()).isEqualTo("vanthu.pcn");
    }

    /** {@code contact_unit} KHÔNG resolve {@code recipientReceiverUsername} (CHỈ áp dụng internal_unit). */
    @Test
    void execute_contactUnit_shouldNotResolveReceiverUsername() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.CONTACT_UNIT, "H05.02", "So NN&MT", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        var request = request(document.getId());
        request.setRecipientGroup(VbdiSendRecipientGroup.CONTACT_UNIT);
        request.setRecipientIdentifier("H05.02");
        useCase.execute(request);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        var payload = (VbdiDeliveryMessagePayload) captor.getValue().units().get(0).payload();
        assertThat(payload.getRecipientReceiverUsername()).isNull();
        verifyNoInteractions(appUserRepository);
    }

    /**
     * Bugfix 07/09/2026 (người dùng gửi mẫu Domino thật {@code docs/views_domino/
     * data_vbdi_SendDocument_output.json}, báo "dữ liệu vẫn chưa khớp hoàn toàn với DOMINO cũ"):
     * {@code documentSnapshot} phải mang thêm 10 field mô tả (trước đó chỉ có code/subject/
     * promulgationDate) đọc từ {@code dynamic_fields} của văn bản đi gốc — xem javadoc
     * {@code publishDeliveryMessage}.
     */
    @Test
    void execute_shouldForwardDescriptiveFieldsFromParentDocumentIntoSnapshot() {
        var document = existingVbdi("vbdi");
        document.replaceDynamicFields("""
            {"unit_publish_identifier":"H05.01","unit_publish":"So Khoa hoc va Cong nghe",
             "document_number":"123/QD-SKHCN","promulgation_date":"01/09/2026",
             "draftor":"Van thu Cuc","signer":"Le Phu Ha","signer_position":"Giam doc",
             "creator":"vanthu01","delivery_method":"Dien tu","document_type":"Bao cao",
             "sector":"Khoa hoc cong nghe","confidentiality":"Thuong","page_amount":"5",
             "priority":"Hoa toc","document_replace_retrieve":"2"}
            """);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        useCase.execute(request(document.getId()));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        var payload = (VbdiDeliveryMessagePayload) captor.getValue().units().get(0).payload();
        var snapshot = payload.getDocumentSnapshot();
        // Khong co "code_full"/"code" rieng trong dynamic_fields o tren - roi vao nhanh fallback
        // cuoi cua resolveCode() (chi con document_number) - xem test rieng resolveCode_* cho ca
        // 3 nhanh uu tien day du.
        assertThat(snapshot.getCode()).isEqualTo("123/QD-SKHCN");
        assertThat(snapshot.getPromulgationDate()).isEqualTo("01/09/2026");
        assertThat(snapshot.getDraftor()).isEqualTo("Van thu Cuc");
        assertThat(snapshot.getSigner()).isEqualTo("Le Phu Ha");
        assertThat(snapshot.getSignerPosition()).isEqualTo("Giam doc");
        assertThat(snapshot.getCreator()).isEqualTo("vanthu01");
        assertThat(snapshot.getDeliveryMethod()).isEqualTo("Dien tu");
        assertThat(snapshot.getDocumentType()).isEqualTo("Bao cao");
        assertThat(snapshot.getSector()).isEqualTo("Khoa hoc cong nghe");
        assertThat(snapshot.getConfidentiality()).isEqualTo("Thuong");
        assertThat(snapshot.getPageAmount()).isEqualTo("5");
        assertThat(snapshot.getPriority()).isEqualTo("Hoa toc");
        // Ghi VERBATIM (khong dich sang bussiness_doc_type/paper/documentreply cua vbden) - da chot
        // voi nguoi dung, xem javadoc publishDeliveryMessage.
        assertThat(snapshot.getDocumentReplaceRetrieve()).isEqualTo("2");
    }

    /**
     * Bugfix 07/09/2026 (người dùng phát hiện): {@code code_full} PHẢI được ưu tiên trước
     * {@code document_number} — đây là giá trị FE đã tính sẵn ĐẦY ĐỦ ("123/QĐ-UBND"), khác hẳn
     * {@code document_number} (chỉ "123", thiếu ký hiệu) — xem javadoc {@code resolveCode}.
     */
    @Test
    void execute_resolveCode_shouldPreferCodeFullOverDocumentNumber() {
        var document = existingVbdi("vbdi");
        document.replaceDynamicFields(
            "{\"code_full\":\"123/QD-UBND\",\"document_number\":\"123\",\"code\":\"QD-UBND\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        useCase.execute(request(document.getId()));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        var payload = (VbdiDeliveryMessagePayload) captor.getValue().units().get(0).payload();
        assertThat(payload.getDocumentSnapshot().getCode()).isEqualTo("123/QD-UBND");
    }

    /**
     * Bugfix 07/09/2026: khi văn bản đi THIẾU {@code code_full} (dữ liệu cũ/chưa đồng bộ) nhưng có
     * đủ {@code document_number} + {@code code}, PHẢI tự ghép lại ("123/QD-UBND") thay vì chỉ lấy
     * {@code document_number} trơ trọi ("123") — xem javadoc {@code resolveCode}.
     */
    @Test
    void execute_resolveCode_shouldCombineDocumentNumberAndCodeWhenCodeFullMissing() {
        var document = existingVbdi("vbdi");
        document.replaceDynamicFields("{\"document_number\":\"123\",\"code\":\"QD-UBND\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);

        useCase.execute(request(document.getId()));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(any(), any(), captor.capture());
        var payload = (VbdiDeliveryMessagePayload) captor.getValue().units().get(0).payload();
        assertThat(payload.getDocumentSnapshot().getCode()).isEqualTo("123/QD-UBND");
    }

    /** Lỗi khi enqueue outbox KHÔNG được làm hỏng kết quả "Gửi văn bản" (side-effect best-effort). */
    @Test
    void execute_whenOutboxEnqueueFails_shouldStillSucceed() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiSendLogDocument(document.getId(), VbdiSendType.SEND_DOCUMENT,
            VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn", "Nong Thanh Hoang", null, "Gui van ban", null, "xuanlt.skhcn");
        when(vbdiSendLogDocumentRepository.save(any())).thenReturn(saved);
        when(outboxPublisherService.enqueue(any(), any(), any())).thenThrow(new RuntimeException("broker down"));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_whenParentMissing_shouldFail() {
        UUID parentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(parentId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(parentId));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
        verifyNoInteractions(vbdiSendLogDocumentRepository);
    }

    @Test
    void execute_whenParentWrongType_shouldFail() {
        var document = existingVbdi("vbdi_res");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdiSendLogDocumentRepository);
    }

    @Test
    void execute_whenParentIdMissing_shouldFailWithoutQuerying() {
        var result = useCase.execute(new AddVbdiSendLogRequest());

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdiDocumentRepository);
        verifyNoInteractions(vbdiSendLogDocumentRepository);
    }
}
