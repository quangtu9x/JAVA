package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.PrepareVbdiDeliveryFilesUseCase;
import com.td.application.sharedcore.OrganizationAgencyResolver;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.sharedcore.Organization;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác"), tham khảo
 * {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md}: test
 * {@link SendVbdiDocumentUseCase} — guard ISSUED, dedup theo identifier, ghi 1 dòng/nơi nhận.
 */
@ExtendWith(MockitoExtension.class)
class SendVbdiDocumentUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private VbdiSendLogDocumentRepository vbdiSendLogDocumentRepository;
    @Mock
    private AddVbdiSendLogUseCase addVbdiSendLogUseCase;
    @Mock
    private PrepareVbdiDeliveryFilesUseCase prepareVbdiDeliveryFilesUseCase;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAgencyResolver organizationAgencyResolver;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private SendVbdiDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendVbdiDocumentUseCase(vbdiDocumentRepository, vbdiSendLogDocumentRepository,
            addVbdiSendLogUseCase, dynamicDocumentFieldsService, prepareVbdiDeliveryFilesUseCase,
            organizationRepository, organizationAgencyResolver);
    }

    private VbdiDocument issuedDocument() {
        return new VbdiDocument("Cong van", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
    }

    private VbdiSendRecipientRequest recipient(String identifier, String name) {
        var recipient = new VbdiSendRecipientRequest();
        recipient.setIdentifier(identifier);
        recipient.setName(name);
        return recipient;
    }

    @Test
    void execute_shouldWriteOneLogRowPerRecipientAcrossGroups() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));
        request.setInternalUnit(List.of(recipient("H56.07.03", "Trung tam Ky thuat")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(2);
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase, org.mockito.Mockito.times(2)).execute(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(AddVbdiSendLogRequest::getRecipientGroup)
            .containsExactlyInAnyOrder(VbdiSendRecipientGroup.COORDINATOR, VbdiSendRecipientGroup.INTERNAL_UNIT);
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("Gửi văn bản");
    }

    /**
     * Bổ sung 07/09/2026 (yêu cầu người dùng "không muốn nhân tệp đính kèm... ánh xạ để đọc trực
     * tiếp... khi phát hành sẽ gửi thêm 'files'"): {@code prepareDeliveryFiles()} phải gọi
     * {@link PrepareVbdiDeliveryFilesUseCase} ĐÚNG 1 LẦN cho cả lượt gửi (KHÔNG phải theo từng
     * recipient/nhóm) khi có ít nhất 1 nơi nhận {@code contact_unit}, và forward CÙNG 1 danh sách
     * file đã copy vào MỌI {@code AddVbdiSendLogRequest} (mọi nhóm, không chỉ contact_unit).
     */
    @Test
    void execute_withContactUnit_shouldPrepareDeliveryFilesExactlyOnceAndForwardToAllRecipients() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"unit_publish_identifier\":\"H05.01\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        var fileRef = com.td.application.documents.VbdiDeliveryFileRef.builder()
            .objectKey("vbdi-delivery/H05.01/2026/09/07/" + document.getId() + "/abc.pdf")
            .bucketName("td-webapi-files")
            .originalFilename("to_trinh.pdf")
            .size(1024L)
            .build();
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of(fileRef)));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));
        request.setContactUnit(List.of(recipient("H56.07.03", "Don vi lien he")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        // DUNG 1 lan cho ca luot gui, khong phai 2 lan (theo 2 nhom co nguoi nhan).
        ArgumentCaptor<com.td.application.documents.PrepareVbdiDeliveryFilesRequest> prepareCaptor =
            ArgumentCaptor.forClass(com.td.application.documents.PrepareVbdiDeliveryFilesRequest.class);
        verify(prepareVbdiDeliveryFilesUseCase, org.mockito.Mockito.times(1)).execute(prepareCaptor.capture());
        assertThat(prepareCaptor.getValue().getDocumentId()).isEqualTo(document.getId());
        assertThat(prepareCaptor.getValue().getSenderIdentifier()).isEqualTo("H05.01");

        ArgumentCaptor<AddVbdiSendLogRequest> logCaptor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase, org.mockito.Mockito.times(2)).execute(logCaptor.capture());
        // CA 2 nhom (coordinator lan contact_unit) deu nhan CUNG 1 danh sach file da copy san.
        assertThat(logCaptor.getAllValues())
            .allSatisfy(req -> assertThat(req.getFiles()).containsExactly(fileRef));
    }

    /**
     * Không có {@code contact_unit} nào trong lượt gửi (chỉ coordinator/internal_unit) —
     * KHÔNG được gọi {@link PrepareVbdiDeliveryFilesUseCase} (tránh copy vô ích khi td-woker không
     * dùng tới, xem javadoc {@code prepareDeliveryFiles}).
     */
    @Test
    void execute_withoutContactUnit_shouldNotPrepareDeliveryFiles() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        useCase.execute(request);

        verifyNoInteractions(prepareVbdiDeliveryFilesUseCase);
    }

    @Test
    void execute_whenDocumentNotIssued_shouldFailWithoutWriting() {
        var document = new VbdiDocument("Cong van", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("chưa ban hành");
        verifyNoInteractions(addVbdiSendLogUseCase);
    }

    @Test
    void execute_whenNoRecipients_shouldFail() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Chưa chọn nơi nhận");
        verifyNoInteractions(addVbdiSendLogUseCase);
    }

    // Dedup: sendType=senddocument (mac dinh) bo qua nguoi nhan DA co it nhat 1 vet gui truoc do -
    // xem javadoc SendVbdiDocumentUseCase / VbdiSendLogDocumentRepository#existsBy...
    @Test
    void execute_withInitialSendType_shouldSkipAlreadySentRecipient() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiSendLogDocumentRepository.existsByParentIdAndRecipientGroupAndRecipientIdentifier(
            document.getId(), VbdiSendRecipientGroup.COORDINATOR, "hoangnt.skhcn")).thenReturn(true);

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        var result = useCase.execute(request);

        // Co nguoi nhan trong request (khong phai "chua chon noi nhan") - chi la DA gui truoc do nen
        // bi dedup bo qua -> thanh cong voi so dong ghi = 0, KHONG phai loi.
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isZero();
        verifyNoInteractions(addVbdiSendLogUseCase);
        // 30/08/2026 (sua loi that: nguoi dung test lai tren van ban DA gui truoc do -> written=0
        // do dedup het, nhung van phai set send_agent="1" - xem javadoc SEND_FAMILY_TYPES dinh chinh).
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .containsEntry("send_agent", "1");
        verify(vbdiDocumentRepository).save(document);
    }

    @Test
    void execute_withResendType_shouldAlwaysWriteWithoutCheckingDedup() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.RESEND_DOCUMENT);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(1);
        verify(addVbdiSendLogUseCase).execute(any());
        // resenddocument KHONG dedup - khong can hoi bang log da gui truoc do (khac senddocument).
        verifyNoInteractions(vbdiSendLogDocumentRepository);
    }

    // 29/08/2026 (port "Thu hồi toàn bộ/thu hồi theo đơn vị" — RevokeDocumentOut.java/
    // RevokeAllDocumentOut.java, xem javadoc VbdiSendType/SendVbdiDocumentUseCase): 3 sendType "thu
    // hồi" dùng CHUNG cơ chế ghi log (guard ISSUED, LUÔN ghi không dedup) — chỉ khác nhãn status.
    @Test
    void execute_withThuHoiVanBanType_shouldWriteWithThuHoiNgayLabel() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.THU_HOI_VAN_BAN);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(1);
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("Thu hồi ngay");
        // thuhoivanban KHONG dedup (giong resenddocument/guibosung) - khong hoi bang log da gui.
        verifyNoInteractions(vbdiSendLogDocumentRepository);
    }

    @Test
    void execute_withLayLaiVanBanType_shouldWriteWithLayLaiNgayLabel() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.LAY_LAI_VAN_BAN);
        request.setInternalUnit(List.of(recipient("H56.07.03", "Trung tam Ky thuat")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("Lấy lại ngay");
    }

    @Test
    void execute_withThuHoiToanBoType_shouldWriteForAllGroupsWithLayLaiLabel() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        // Co contact_unit trong request nay -> prepareDeliveryFiles() se goi use case nay 1 lan.
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.THU_HOI_TOAN_BO);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));
        request.setContactUnit(List.of(recipient("H56.07.03", "Don vi lien he")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(2);
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase, org.mockito.Mockito.times(2)).execute(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(AddVbdiSendLogRequest::getStatus)
            .containsExactly("Lấy lại", "Lấy lại");
    }

    // 29/08/2026 (port "gửi văn bản thay thế" — SendDocument.java actionname=UnitRevoke, xem javadoc
    // Java VbdiSendType#UNIT_REVOKE): cùng cơ chế "luôn ghi, không dedup" như các sendType khác.
    @Test
    void execute_withUnitRevokeType_shouldWriteWithReplacementNoticeLabel() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.UNIT_REVOKE);
        request.setInternalUnit(List.of(recipient("H56.07.03", "Trung tam Ky thuat")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("Gửi văn bản thay thế/thu hồi");
        verifyNoInteractions(vbdiSendLogDocumentRepository);
    }

    // 29/08/2026 (người dùng báo "thao tác gửi văn bản chưa thấy đổi trạng thái văn bản" — xem
    // javadoc SendVbdiDocumentUseCase#SEND_AGENT_FIELD): xác nhận send_agent="1" được set sau khi
    // gửi thành công (sendType thuộc họ "gửi"), và KHÔNG set cho sendType "thu hồi".
    @Test
    void execute_withSendDocumentTypeAndWrittenRows_shouldMarkSendAgent() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        useCase.execute(request);

        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .containsEntry("send_agent", "1");
        verify(vbdiDocumentRepository).save(document);
    }

    @Test
    void execute_withThuHoiVanBanType_shouldNotMarkSendAgent() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.THU_HOI_VAN_BAN);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        useCase.execute(request);

        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .doesNotContainKey("send_agent");
        verify(vbdiDocumentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void execute_whenSendAgentAlreadyMarked_shouldNotSaveAgain() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"send_agent\":\"1\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.RESEND_DOCUMENT);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        useCase.execute(request);

        verify(vbdiDocumentRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * Bổ sung 18/09/2026 (hoàn thiện "phát hành vbdi → đến nội bộ", yêu cầu người dùng "có trường
     * hợp chọn đơn vị nội bộ ở Cơ quan, đơn vị thuộc Tỉnh"): 1 tổ chức được chọn ở tab
     * {@code contactUnit} nhưng thực ra thuộc CHÍNH agency của người gửi (khớp
     * {@code OrganizationAgencyResolver#resolveAgencyId} giữa recipient và người gửi hiện tại) phải
     * được ghi log với {@code recipientGroup = internal_unit}, KHÔNG phải {@code contact_unit} —
     * xem javadoc {@code SendVbdiDocumentUseCase#reclassifyIfOwnAgency}.
     */
    @Test
    void execute_withContactUnitMatchingSenderOwnAgency_shouldReclassifyToInternalUnit() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var recipientOrg = new Organization("H05.01.02", "Phong Cong nghe", null, null,
            "department", 4, "Phong Cong nghe", 0, 0, null, null, null, null, null, null, null, null, null);
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.01.02"))
            .thenReturn(Optional.of(recipientOrg));
        UUID senderAgencyId = UUID.randomUUID();
        when(organizationAgencyResolver.resolveAgencyId(recipientOrg.getId())).thenReturn(Optional.of(senderAgencyId));
        when(organizationAgencyResolver.resolveCurrentUserAgencyId()).thenReturn(Optional.of(senderAgencyId));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setContactUnit(List.of(recipient("H05.01.02", "Phong Cong nghe")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase).execute(captor.capture());
        assertThat(captor.getValue().getRecipientGroup()).isEqualTo(VbdiSendRecipientGroup.INTERNAL_UNIT);
    }

    /**
     * Đối chứng test trên: agency của recipient KHÁC agency của người gửi (Sở khác thật) — giữ
     * nguyên {@code contact_unit}, không reclassify nhầm.
     */
    @Test
    void execute_withContactUnitDifferentAgency_shouldStayContactUnit() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var recipientOrg = new Organization("H56.07.03", "So khac", null, null,
            "agency", 2, "So khac", 0, 0, null, null, null, null, null, null, null, null, null);
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H56.07.03"))
            .thenReturn(Optional.of(recipientOrg));
        when(organizationAgencyResolver.resolveAgencyId(recipientOrg.getId())).thenReturn(Optional.of(UUID.randomUUID()));
        when(organizationAgencyResolver.resolveCurrentUserAgencyId()).thenReturn(Optional.of(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setContactUnit(List.of(recipient("H56.07.03", "So khac")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AddVbdiSendLogRequest> captor = ArgumentCaptor.forClass(AddVbdiSendLogRequest.class);
        verify(addVbdiSendLogUseCase).execute(captor.capture());
        assertThat(captor.getValue().getRecipientGroup()).isEqualTo(VbdiSendRecipientGroup.CONTACT_UNIT);
    }

    @Test
    void execute_whenDocumentMissing_shouldFail() {
        UUID documentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentId)).thenReturn(Optional.empty());

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(documentId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
        verifyNoInteractions(addVbdiSendLogUseCase);
    }
}
