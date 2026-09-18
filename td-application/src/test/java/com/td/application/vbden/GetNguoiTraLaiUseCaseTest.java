package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbden.VbdenResDocument;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 14/09/2026 ("chức năng Trả lại ... đang gọi
 * qlvb/vbden/GetNguoiTraLai?databasetype=vbden -> port sang java") — xem javadoc
 * {@link GetNguoiTraLaiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetNguoiTraLaiUseCaseTest {

    @Mock
    private VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GetNguoiTraLaiUseCase useCase;
    private UUID parentId;

    @BeforeEach
    void setUp() {
        useCase = new GetNguoiTraLaiUseCase(
            vbdenResDocumentRepository, dynamicDocumentFieldsService, currentUserProvider);
        parentId = UUID.randomUUID();
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo2"));
    }

    @Test
    void execute_withNullId_shouldReturnEmptySuccess() {
        var request = new GetNguoiTraLaiRequest();

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_withNullRequest_shouldReturnEmptySuccess() {
        var result = useCase.execute(null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_currentUserInUserProcessObj_shouldReturnRecordCreator() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"chuyenxuly\",\"nguoi_thuc_hien\":\"vanthu01\",\"creator\":\"Nguyen Van A\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"demo2\\\",\\\"name\\\":\\\"Demo Hai\\\"}]\"}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly(Map.of("username", "vanthu01", "name", "Nguyen Van A"));
    }

    // Bugfix 14/09/2026 (báo lỗi người dùng: trả về trống cho demo2, văn bản
    // 2b834a46-e700-4264-af54-5f619b62d766) — xem javadoc VbdenObjSnapshotFields: dữ liệu THẬT lưu
    // "user_process_obj" dưới dạng MẢNG JSON LỒNG (jsonb_typeof = "array", KHÔNG PHẢI "string") vì
    // FieldTypeInferrer suy luận field kết thúc "_obj" là FieldType.JSON và tự parse lúc ghi — khác
    // hẳn fixture ở 2 test phía trên (chuỗi JSON escaped, chỉ đúng giả định SAI của code trước bản
    // fix này). Test này dùng ĐÚNG hình dạng thật để tránh tái diễn.
    @Test
    void execute_userProcessObjStoredAsNativeNestedArray_shouldReturnRecordCreator() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"chuyenxuly\",\"nguoi_thuc_hien\":\"lpha\",\"creator\":\"Le Phu Ha\","
            + "\"user_process_obj\":[{\"id\":\"1355f7cb-ea1f-4ccb-a89b-0bb6033616cb\","
            + "\"name\":\"Truong phong\",\"username\":\"demo2\"}]}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly(Map.of("username", "lpha", "name", "Le Phu Ha"));
    }

    @Test
    void execute_userProcessObjEmptyNativeArray_shouldFallbackToNativeUserAssignerObj() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"chuyenxuly\",\"nguoi_thuc_hien\":\"vanthu01\",\"creator\":\"Nguyen Van A\","
            + "\"user_process_obj\":[],\"user_assigner_obj\":[{\"username\":\"demo2\"}]}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void execute_currentUserMatchesByUserReceiver_shouldReturnRecordCreator() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"chuyenxuly\",\"nguoi_thuc_hien\":\"vanthu01\",\"creator\":\"Nguyen Van A\","
            + "\"user_process_obj\":\"[{\\\"user_receiver\\\":\\\"DEMO2\\\"}]\"}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void execute_userProcessObjEmptyArray_shouldFallbackToUserAssignerObj() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"chuyenxuly\",\"nguoi_thuc_hien\":\"vanthu01\",\"creator\":\"Nguyen Van A\","
            + "\"user_process_obj\":\"[]\",\"user_assigner_obj\":\"[{\\\"username\\\":\\\"demo2\\\"}]\"}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void execute_currentUserNotInSnapshot_shouldNotReturnEntry() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"chuyenxuly\",\"nguoi_thuc_hien\":\"vanthu01\",\"creator\":\"Nguyen Van A\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"someoneelse\\\"}]\"}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_actionNotChuyenXuLy_shouldBeIgnored() {
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);
        String fields = "{\"action\":\"ketthucxuly\",\"nguoi_thuc_hien\":\"vanthu01\",\"creator\":\"Nguyen Van A\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"demo2\\\"}]\"}";
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(newResRecord(fields)), PageRequest.of(0, 1000), 1));

        var result = useCase.execute(request);

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_currentUsernameUnresolvable_shouldReturnEmptySuccess() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        var request = new GetNguoiTraLaiRequest();
        request.setId(parentId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    private VbdenResDocument newResRecord(String dynamicFieldsJson) {
        VbdenResDocument document = new VbdenResDocument("Xu ly", "vbden_res", "DRAFT", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
