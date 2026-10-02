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
     * Không có {@code contact_unit}/{@code internal_unit} nào trong lượt gửi (chỉ coordinator) —
     * KHÔNG được gọi {@link PrepareVbdiDeliveryFilesUseCase} (tránh copy vô ích khi td-woker không
     * dùng tới, xem javadoc {@code prepareDeliveryFiles}) — coordinator KHÔNG tạo văn bản đến thật.
     */
    @Test
    void execute_withoutContactUnitOrInternalUnit_shouldNotPrepareDeliveryFiles() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        useCase.execute(request);

        verifyNoInteractions(prepareVbdiDeliveryFilesUseCase);
    }

    /**
     * Bổ sung 21/09/2026 (bugfix người dùng: rà soát "Phát hành kèm văn bản đến" phát hiện điều kiện
     * chuẩn bị tệp CŨ chỉ kiểm tra {@code contact_unit} — lỗi thời từ khi {@code internal_unit} CŨNG
     * tạo văn bản đến thật (18/09/2026, xem {@code VbdiDeliveryTaskHandler}): gửi CHỈ tới
     * {@code internal_unit} (KHÔNG có {@code contact_unit}) PHẢI vẫn chuẩn bị tệp đính kèm — trước
     * bản vá, văn bản đến tạo cho internal_unit sẽ LUÔN thiếu tệp dù văn bản đi có tệp.
     */
    @Test
    void execute_withOnlyInternalUnit_shouldPrepareDeliveryFiles() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setInternalUnit(List.of(recipient("H05.01.02", "Phong Cong nghe")));

        useCase.execute(request);

        verify(prepareVbdiDeliveryFilesUseCase, org.mockito.Mockito.times(1)).execute(any());
    }

    /**
     * Bổ sung 21/09/2026 (yêu cầu người dùng: "nếu check vào phát hành kèm văn bản đến thì khi gửi
     * phải gửi tất cả những file đính kèm của văn bản đến theo"): {@code phathanhkemvbden="1"} +
     * {@code documentin_obj} có 1 văn bản đến — id văn bản đến đó phải được forward vào
     * {@code additionalSourceDocumentIds} của {@link PrepareVbdiDeliveryFilesRequest}.
     */
    @Test
    void execute_withPhatHanhKemVbdenFlagOn_shouldIncludeDocumentInIdsInDeliveryRequest() {
        var document = issuedDocument();
        UUID vbdenId = UUID.randomUUID();
        document.replaceDynamicFields(("{\"phathanhkemvbden\":\"1\",\"documentin_obj\":"
            + "[{\"id\":\"%s\",\"code\":\"123/CV-UBND\"}]}").formatted(vbdenId));
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setContactUnit(List.of(recipient("H56.07.03", "Don vi lien he")));

        useCase.execute(request);

        ArgumentCaptor<com.td.application.documents.PrepareVbdiDeliveryFilesRequest> captor =
            ArgumentCaptor.forClass(com.td.application.documents.PrepareVbdiDeliveryFilesRequest.class);
        verify(prepareVbdiDeliveryFilesUseCase).execute(captor.capture());
        assertThat(captor.getValue().getAdditionalSourceDocumentIds()).containsExactly(vbdenId);
    }

    /** Đối chứng: {@code phathanhkemvbden} KHÔNG bật (hoặc rỗng) — KHÔNG forward id nào dù có {@code documentin_obj}. */
    @Test
    void execute_withPhatHanhKemVbdenFlagOff_shouldNotIncludeAnyDocumentInIds() {
        var document = issuedDocument();
        UUID vbdenId = UUID.randomUUID();
        document.replaceDynamicFields(("{\"documentin_obj\":[{\"id\":\"%s\"}]}").formatted(vbdenId));
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setContactUnit(List.of(recipient("H56.07.03", "Don vi lien he")));

        useCase.execute(request);

        ArgumentCaptor<com.td.application.documents.PrepareVbdiDeliveryFilesRequest> captor =
            ArgumentCaptor.forClass(com.td.application.documents.PrepareVbdiDeliveryFilesRequest.class);
        verify(prepareVbdiDeliveryFilesUseCase).execute(captor.capture());
        assertThat(captor.getValue().getAdditionalSourceDocumentIds()).isEmpty();
    }

    /** 30/09/2026: tên tệp trong {@code tepkemtheo} ("a;b;") được forward để phía nhận chia nhóm. */
    @Test
    void execute_shouldForwardTepKemTheoFilenamesToDeliveryRequest() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"tepbanhanh\":\"qd.pdf;\",\"tepkemtheo\":\"pl1.pdf; pl2.docx;\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(prepareVbdiDeliveryFilesUseCase.execute(any())).thenReturn(Result.success(List.of()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setContactUnit(List.of(recipient("H56.07.03", "Don vi lien he")));

        useCase.execute(request);

        ArgumentCaptor<com.td.application.documents.PrepareVbdiDeliveryFilesRequest> captor =
            ArgumentCaptor.forClass(com.td.application.documents.PrepareVbdiDeliveryFilesRequest.class);
        verify(prepareVbdiDeliveryFilesUseCase).execute(captor.capture());
        assertThat(captor.getValue().getAttachedFilenames()).containsExactly("pl1.pdf", "pl2.docx");
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
    void execute_whenSendAgentAlreadyMarkedAndRecipientNameAlreadyOnField_shouldNotSaveAgain() {
        var document = issuedDocument();
        // 21/09/2026 (xem javadoc addendum SendVbdiDocumentUseCase#applySendSideEffects): giu nguyen
        // y dinh test goc ("khong doi gi thi khong save lai") nhung PHAI dat san ten nguoi nhan tren
        // "coordinator_rtext" - tu khi use case gop ten nguoi nhan MOI vao field nay, resend 1 nguoi
        // nhan CHUA co ten tren field se hop le lam thay doi field (can save), khac voi truoc ban vay.
        // Bo sung 21/09/2026 (2, xem javadoc COORDINATOR_HIDE_FIELD): CUNG phai dat san
        // coordinator_hide_rtext/coordinator_hide_not_seen_rtext - tu khi resend coordinator CUNG gop
        // username vao 2 field nay, thieu san se lam thay doi field (can save) o lan resend nay.
        document.replaceDynamicFields("{\"send_agent\":\"1\",\"coordinator_rtext\":\"Nong Thanh Hoang\","
            + "\"coordinator_hide_rtext\":\"hoangnt.skhcn\",\"coordinator_hide_not_seen_rtext\":\"hoangnt.skhcn\"}");
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
     * Bổ sung 21/09/2026 (bugfix người dùng báo "Gửi bổ sung xong, tab Phát hành không thấy thông
     * tin đơn vị gửi bổ sung" — xem javadoc {@code SendVbdiDocumentUseCase#applySendSideEffects}):
     * "Gửi bổ sung" thêm 1 đơn vị nội bộ MỚI (chưa từng có trên field {@code internal_unit_rtext}) —
     * field này phải được GỘP THÊM tên đơn vị mới (giữ tên cũ, nối "; "), không ghi đè mất tên cũ.
     */
    @Test
    void execute_withGuiBoSungType_shouldMergeNewRecipientNameIntoRtextFieldKeepingExisting() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"internal_unit_rtext\":\"Phong Cong nghe\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.GUI_BO_SUNG);
        request.setInternalUnit(List.of(recipient("H56.07.03", "Trung tam Ky thuat")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .containsEntry("internal_unit_rtext", "Phong Cong nghe; Trung tam Ky thuat");
        verify(vbdiDocumentRepository).save(document);
    }

    /**
     * Gửi bổ sung LẶP LẠI cùng 1 đơn vị đã có sẵn trên field — KHÔNG thêm trùng tên (dedup theo
     * chuỗi con, không phân biệt hoa/thường — đúng {@code SendDocument.java} Domino gốc).
     */
    @Test
    void execute_withGuiBoSungType_shouldNotDuplicateNameAlreadyOnField() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"internal_unit_rtext\":\"Phong Cong nghe\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.GUI_BO_SUNG);
        request.setInternalUnit(List.of(recipient("H05.01", "Phong Cong nghe")));

        useCase.execute(request);

        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .containsEntry("internal_unit_rtext", "Phong Cong nghe");
    }

    /**
     * Bổ sung 21/09/2026 (yêu cầu người dùng: "Gửi văn bản đi" tới CÁ NHÂN phải khiến văn bản xuất
     * hiện ở danh sách cá nhân liên quan của người đó — tiền đề để "Lấy lại" có gì để gỡ, xem javadoc
     * {@code SendVbdiDocumentUseCase#COORDINATOR_HIDE_FIELD}): gửi tới coordinator phải gộp username
     * vào CẢ {@code coordinator_hide_rtext} lẫn {@code coordinator_hide_not_seen_rtext}.
     */
    @Test
    void execute_toCoordinator_shouldAddUsernameToCoordinatorHideFields() {
        var document = issuedDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .containsEntry("coordinator_hide_rtext", "hoangnt.skhcn")
            .containsEntry("coordinator_hide_not_seen_rtext", "hoangnt.skhcn");
    }

    /**
     * Bổ sung 21/09/2026 (yêu cầu người dùng: "khi lấy lại thì loại thông tin của cá nhân đấy để
     * không nhìn thấy ở danh sách cá nhân liên quan của người đấy nữa"): "Lấy lại" 1 cá nhân đã từng
     * được gửi phải GỠ username khỏi CẢ 3 field hide (kể cả {@code coordinator_hide_seen_rtext} — họ
     * có thể đã xem trước khi bị lấy lại). 30/09/2026: tên ở {@code coordinator_rtext} (tab "Phát hành")
     * được GIỮ LẠI (yêu cầu người dùng). KHÔNG động tới cá nhân KHÁC còn lại trên cùng field.
     */
    @Test
    void execute_layLaiCoordinator_shouldRemoveFromHideFieldsButKeepDisplayField() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"coordinator_rtext\":\"Nong Thanh Hoang; Tran Thi Binh\","
            + "\"coordinator_hide_rtext\":\"hoangnt.skhcn;binhtt.skhcn\","
            + "\"coordinator_hide_not_seen_rtext\":\"hoangnt.skhcn;binhtt.skhcn\","
            + "\"coordinator_hide_seen_rtext\":\"hoangnt.skhcn\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.LAY_LAI_VAN_BAN);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("coordinator_rtext", "Nong Thanh Hoang; Tran Thi Binh");
        assertThat(fields).containsEntry("coordinator_hide_rtext", "binhtt.skhcn");
        assertThat(fields).containsEntry("coordinator_hide_not_seen_rtext", "binhtt.skhcn");
        // hoangnt.skhcn da tung xem (co trong hide_seen) - van phai bi go het du da xem hay chua.
        assertThat(fields.get("coordinator_hide_seen_rtext")).isEqualTo("");
        verify(vbdiDocumentRepository).save(document);
    }

    /** 30/09/2026 (ảnh người dùng gửi): "Lấy lại" đơn vị nội bộ — tab Phát hành vẫn giữ tên đơn vị. */
    @Test
    void execute_layLaiInternalUnit_shouldKeepNameOnPhatHanhTab() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"internal_unit_rtext\":\"Trung tâm CNTT-TT\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.LAY_LAI_VAN_BAN);
        request.setInternalUnit(List.of(recipient("H05.01.09", "Trung tâm CNTT-TT")));

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        var fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("internal_unit_rtext", "Trung tâm CNTT-TT");
    }

    /**
     * Đối chứng: "Lấy lại" KHÔNG set {@code send_agent} (không thuộc họ GỬI — xem javadoc
     * {@code SEND_FAMILY_TYPES}/{@code REVOKE_FAMILY_TYPES}) dù có thay đổi field khác.
     */
    @Test
    void execute_layLaiCoordinator_shouldNotSetSendAgent() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"coordinator_hide_rtext\":\"hoangnt.skhcn\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.LAY_LAI_VAN_BAN);
        request.setCoordinator(List.of(recipient("hoangnt.skhcn", "Nong Thanh Hoang")));

        useCase.execute(request);

        assertThat(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()))
            .doesNotContainKey("send_agent");
    }

    /**
     * Đơn vị nội bộ khi "Lấy lại": 30/09/2026 GIỮ tên ở {@code internal_unit_rtext} (tab "Phát hành", yêu
     * cầu người dùng) — và KHÔNG động tới field {@code coordinator_hide_*} (chỉ áp dụng cho nhóm
     * coordinator, xem javadoc {@code applyRevokeSideEffects}).
     */
    @Test
    void execute_layLaiInternalUnit_shouldKeepDisplayFieldAndNotTouchHideFields() {
        var document = issuedDocument();
        document.replaceDynamicFields("{\"internal_unit_rtext\":\"Phong Cong nghe; Phong Ke hoach\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiSendLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SendVbdiDocumentRequest();
        request.setDocumentId(document.getId());
        request.setSendType(VbdiSendType.LAY_LAI_VAN_BAN);
        request.setInternalUnit(List.of(recipient("H05.01", "Phong Cong nghe")));

        useCase.execute(request);

        var fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("internal_unit_rtext", "Phong Cong nghe; Phong Ke hoach");
        assertThat(fields).doesNotContainKey("coordinator_hide_rtext");
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
