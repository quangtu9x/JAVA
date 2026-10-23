package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbden.VbdenResDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

/**
 * 18/09/2026 (cutover {@code ModalThuHoiVanBan.jsx}, nhánh "Chọn đơn vị/người để thu hồi") — xem
 * javadoc {@link GetNguoiThuHoiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetNguoiThuHoiUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private com.td.application.sharedcore.GetLegacyOrgLookupForDungChungUseCase orgLookup;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetNguoiThuHoiUseCase useCase;

    private GetNguoiThuHoiUseCase newUseCase() {
        return new GetNguoiThuHoiUseCase(
            documentRepository, vbdenResDocumentRepository, dynamicDocumentFieldsService, currentUserProvider,
            new VbdenRevokeRecipientMatcher(orgLookup));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GetNguoiThuHoiRequest();
        request.setId(id);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldGroupStillPendingRecipientsByCategory() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"nguyenvana\"],"
            + "\"user_coordinate\":[\"tranthib\"],"
            + "\"user_to_read\":[\"levanc\"],"
            + "\"user_track\":[\"phamvand\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"vanthu01\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"username\":\"nguyenvana\",\"name\":\"Nguyen Van A\"}],"
            + "\"user_coordinate_obj\":[{\"username\":\"tranthib\",\"name\":\"Tran Thi B\"}],"
            + "\"user_to_read_obj\":[{\"username\":\"levanc\",\"name\":\"Le Van C\"}],"
            + "\"user_track_obj\":[{\"username\":\"phamvand\",\"name\":\"Pham Van D\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, List<Map<String, Object>>> data = result.getData();
        assertThat(data.get("chutri")).extracting(m -> m.get("username")).containsExactly("nguyenvana");
        assertThat(data.get("phoihop")).extracting(m -> m.get("username")).containsExactly("tranthib");
        assertThat(data.get("xemdebiet")).extracting(m -> m.get("username")).containsExactly("levanc");
        assertThat(data.get("theodoi")).extracting(m -> m.get("username")).containsExactly("phamvand");
    }

    // Nguoi trong snapshot nhung DA xu ly xong (khong con trong user_process hien tai) KHONG duoc
    // dua vao ket qua - dung Domino goc (chi hien thi nguoi con "chua xu ly").
    @Test
    void execute_shouldExcludeRecipientsAlreadyProcessed() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"vanthu01\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"username\":\"nguyenvana\"},{\"username\":\"daxulyxongroi\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.getData().get("chutri")).extracting(m -> m.get("username"))
            .containsExactly("nguyenvana");
    }

    // 11/10/2026 (demo9): chuyen bang "Phan xu ly" (action=phanxuly) cung danh dau user_delivered nen
    // PHAI thu hoi duoc - truoc day bi bo qua -> "Da khong con nguoi/don vi co the thu hoi".
    @Test
    void execute_phanXuLyTransfer_shouldBeRevocable() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo9"));
        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo9\","
            + "\"action\":\"phanxuly\","
            + "\"user_process_obj\":[{\"username\":\"nguyenvana\",\"name\":\"Nguyen Van A\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        assertThat(useCase.execute(request).getData().get("chutri")).extracting(m -> m.get("username"))
            .containsExactly("nguyenvana");
    }

    // Nguoi da nhan nhung khong con thu hoi duoc -> "khongthethuhoi" kem ly do (FE giai thich).
    @Test
    void execute_shouldExplainRecipientsNoLongerRevocable() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"nguyenvana\"],\"user_processed\":[\"daxuly\"],\"user_processing\":[\"dangxuly\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo9"));
        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo9\",\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"username\":\"nguyenvana\",\"name\":\"A\"},{\"username\":\"daxuly\",\"name\":\"B\"},"
            + "{\"username\":\"dangxuly\",\"name\":\"C\"},{\"username\":\"dachuyen\",\"name\":\"D\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        List<Map<String, Object>> khongThe = useCase.execute(request).getData().get("khongthethuhoi");
        assertThat(khongThe).extracting(m -> m.get("name") + "|" + m.get("ly_do")).containsExactly(
            "B|đã xử lý", "C|đã bắt đầu xử lý", "D|đã chuyển tiếp cho người khác");
    }

    // 11/10/2026 du lieu that VB 33/BC-SKHCN: chuyen cho DON VI (dai dien demo15), demo15 da ket thuc xu ly
    // nhung ma don vi van con trong unit_process -> KHONG con thu hoi duoc.
    @Test
    void execute_unitWhoseMembersFinished_shouldNotBeRevocable() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[],\"unit_process\":[\"H05.01.05.02\"],"
            + "\"user_processed\":[\"demo15\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo9"));
        when(orgLookup.getOrganizationMembers("ORG-QTHT", true)).thenReturn(List.of(
            Map.of("username", "demo15"), Map.of("username", "demo17")));
        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo9\",\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"id\":\"ORG-QTHT\",\"name\":\"Phong QTHT\",\"identifier\":\"H05.01.05.02\","
            + "\"user_receiver\":\"demo15\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));
        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        var data = useCase.execute(request).getData();

        assertThat(data.get("chutri")).isEmpty();
        assertThat(data.get("khongthethuhoi")).extracting(m -> m.get("name") + "|" + m.get("ly_do"))
            .containsExactly("Phong QTHT|đã xử lý");
    }

    // VB 10/BC-SKHCN: demo15 chuyen tiep demo17 CUNG phong -> phong van con dang giu van ban -> thu hoi duoc.
    @Test
    void execute_unitStillHeldByMember_shouldBeRevocable() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"demo17\"],\"unit_process\":[\"H05.01.05.02\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo9"));
        when(orgLookup.getOrganizationMembers("ORG-QTHT", true)).thenReturn(List.of(
            Map.of("username", "demo15"), Map.of("username", "demo17")));
        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo9\",\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"id\":\"ORG-QTHT\",\"name\":\"Phong QTHT\",\"identifier\":\"H05.01.05.02\","
            + "\"user_receiver\":\"demo15\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));
        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        var data = useCase.execute(request).getData();

        assertThat(data.get("chutri")).extracting(m -> m.get("name")).containsExactly("Phong QTHT");
        assertThat(data.get("khongthethuhoi")).isEmpty();
    }

    // 12/10/2026 du lieu that demo11 (VB 1/QD-SNNMT): chuyen demo14 -> thu hoi, chuyen demo13 -> thu hoi.
    // Ly do phai noi ro "ban da thu hoi", khong gop chung voi "chuyen tiep".
    @Test
    void execute_recipientsRevokedByMe_shouldSayRevoked() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"demo11\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo11"));
        java.time.LocalDateTime t0 = java.time.LocalDateTime.of(2026, 10, 12, 10, 0);
        VbdenResDocument chuyen14 = newResRecord("{\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo11\","
            + "\"action\":\"chuyenxuly\",\"user_process_obj\":[{\"username\":\"demo14\",\"name\":\"CV6\"}]}");
        chuyen14.setCreatedOn(t0);
        VbdenResDocument thuhoi14 = newResRecord("{\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo11\","
            + "\"action\":\"thuhoi\",\"user_process_obj\":[{\"username\":\"demo14\",\"name\":\"CV6\"}]}");
        thuhoi14.setCreatedOn(t0.plusMinutes(1));
        VbdenResDocument chuyen13 = newResRecord("{\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"demo11\","
            + "\"action\":\"chuyenxuly\",\"user_process_obj\":[{\"username\":\"demo13\",\"name\":\"CV5\"}]}");
        chuyen13.setCreatedOn(t0.plusMinutes(2));
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(chuyen14, thuhoi14, chuyen13), PageRequest.of(0, 1000), 3));
        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        List<Map<String, Object>> khongThe = useCase.execute(request).getData().get("khongthethuhoi");

        // demo14: thu hoi SAU lan chuyen -> "ban da thu hoi"; demo13: khong con giu, chua tung bi thu hoi -> chuyen tiep.
        assertThat(khongThe).extracting(m -> m.get("name") + "|" + m.get("ly_do"))
            .containsExactlyInAnyOrder("CV6|bạn đã thu hồi", "CV5|đã chuyển tiếp cho người khác");
    }

    // Ban ghi lich su do NGUOI KHAC tao (khong phai nguoi dang thao tac) khong duoc tinh.
    @Test
    void execute_shouldIgnoreHistoryRecordsCreatedByOtherUsers() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"nguoikhac\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"username\":\"nguyenvana\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new GetNguoiThuHoiRequest();
        request.setId(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.getData().get("chutri")).isEmpty();
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private VbdenResDocument newResRecord(String dynamicFieldsJson) {
        VbdenResDocument document = new VbdenResDocument("Xu ly", "vbden_res", "DRAFT", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
