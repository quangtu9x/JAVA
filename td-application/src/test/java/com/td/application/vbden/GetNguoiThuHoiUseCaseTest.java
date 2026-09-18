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

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetNguoiThuHoiUseCase useCase;

    private GetNguoiThuHoiUseCase newUseCase() {
        return new GetNguoiThuHoiUseCase(
            documentRepository, vbdenResDocumentRepository, dynamicDocumentFieldsService, currentUserProvider);
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
