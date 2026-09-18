package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbden.VbdenResDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N09 (01/09/2026 — "Lịch sử xử lý" cá nhân) — xem javadoc {@link LichSuXuLyUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class LichSuXuLyUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private LichSuXuLyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LichSuXuLyUseCase(
            documentRepository, vbdenResDocumentRepository, dynamicDocumentFieldsService,
            listDocumentFilesUseCase, currentUserProvider);
        lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnEmptyPageWithoutQuerying() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute(new LichSuXuLyRequest());

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalItems()).isZero();
        verify(vbdenResDocumentRepository, never()).findByFieldValue(any(), any(), any());
    }

    @Test
    void execute_shouldMergeParentFieldsWithProcessRecordFieldsAndUseParentId() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        BusinessDocument parent = newDocument("vbden", "{"
            + "\"code\":\"123/VP\",\"subject\":\"Van ban goc\",\"unit_in_charge\":\"UBND tinh\","
            + "\"status\":\"2\",\"user_process\":[\"nguyenvana\"],"
            + "\"draftor\":\"khong duoc lay - ngoai whitelist\"}");
        UUID parentId = parent.getId();
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        VbdenResDocument processRecord = newResRecord("{"
            + "\"parentid\":\"" + parentId + "\",\"nguoi_thuc_hien\":\"nguyenvana\","
            + "\"action\":\"chuyenxuly\",\"opinion\":\"Giao xu ly\"}");
        when(vbdenResDocumentRepository.findByFieldValue(eq("nguoi_thuc_hien"), eq("nguyenvana"), any()))
            .thenReturn(new PageImpl<>(List.of(processRecord), PageRequest.of(0, 20), 1));

        var result = useCase.execute(new LichSuXuLyRequest());

        assertThat(result.getItems()).hasSize(1);
        Map<String, Object> row = result.getItems().get(0);
        // id la CUA VAN BAN CHA (khop hanh vi Domino - DocumentToObjectNode goi cha truoc).
        assertThat(row.get("id")).isEqualTo(parentId.toString());
        assertThat(row.get("code")).isEqualTo("123/VP");
        assertThat(row.get("subject")).isEqualTo("Van ban goc");
        assertThat(row.get("unit_in_charge")).isEqualTo("UBND tinh");
        // field CHA ngoai whitelist KHONG duoc lay (dung filterItems that cua LichSuXuLy.java).
        assertThat(row).doesNotContainKey("draftor");
        // field CON (getall) co mat.
        assertThat(row.get("action")).isEqualTo("chuyenxuly");
        assertThat(row.get("opinion")).isEqualTo("Giao xu ly");
        assertThat(row.get("created")).isNotNull();
        assertThat(row.get("files")).isInstanceOf(List.class);
        assertThat(row.get("res_files")).isInstanceOf(List.class);
    }

    @Test
    void execute_whenParentIdMissingOrInvalid_shouldSkipRecord() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        VbdenResDocument noParentId = newResRecord("{\"nguoi_thuc_hien\":\"nguyenvana\"}");
        VbdenResDocument invalidParentId = newResRecord(
            "{\"nguoi_thuc_hien\":\"nguyenvana\",\"parentid\":\"khong-phai-uuid\"}");
        when(vbdenResDocumentRepository.findByFieldValue(eq("nguoi_thuc_hien"), eq("nguyenvana"), any()))
            .thenReturn(new PageImpl<>(List.of(noParentId, invalidParentId), PageRequest.of(0, 20), 2));

        var result = useCase.execute(new LichSuXuLyRequest());

        assertThat(result.getItems()).isEmpty();
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void execute_whenParentDeletedOrWrongType_shouldSkipRecord() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        BusinessDocument wrongTypeParent = newDocument("vbdi", "{}");
        UUID wrongTypeParentId = wrongTypeParent.getId();
        when(documentRepository.findById(wrongTypeParentId)).thenReturn(Optional.of(wrongTypeParent));
        VbdenResDocument recordWithWrongTypeParent = newResRecord(
            "{\"parentid\":\"" + wrongTypeParentId + "\",\"nguoi_thuc_hien\":\"nguyenvana\"}");

        when(vbdenResDocumentRepository.findByFieldValue(eq("nguoi_thuc_hien"), eq("nguyenvana"), any()))
            .thenReturn(new PageImpl<>(List.of(recordWithWrongTypeParent), PageRequest.of(0, 20), 1));

        var result = useCase.execute(new LichSuXuLyRequest());

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void execute_shouldPassPageNumberAndPageSizeToRepository() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        Page<VbdenResDocument> emptyPage = new PageImpl<>(List.of(), PageRequest.of(2, 10), 0);
        when(vbdenResDocumentRepository.findByFieldValue(any(), any(), any())).thenReturn(emptyPage);

        var request = new LichSuXuLyRequest();
        request.setPageNumber(3);
        request.setPageSize(10);

        useCase.execute(request);

        verify(vbdenResDocumentRepository).findByFieldValue(
            eq("nguoi_thuc_hien"), eq("nguyenvana"), eq(PageRequest.of(2, 10)));
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Tieu de", documentType, "0", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }

    // BL-210 DoD (4), yeu cau nguoi dung 05/09/2026 ("tach vbden_res sang bang rieng"): ban ghi qua
    // trinh xu ly gio la VbdenResDocument (bang doc lap), khong con la BusinessDocument.
    private VbdenResDocument newResRecord(String dynamicFieldsJson) {
        VbdenResDocument document = new VbdenResDocument("Tieu de", "vbden_res", "DRAFT", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }
}
