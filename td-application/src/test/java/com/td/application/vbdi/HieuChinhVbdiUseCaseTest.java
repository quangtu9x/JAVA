package com.td.application.vbdi;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("xem lại khi hiệu chỉnh và thao tác lưu đang gọi api domino, port
 * luôn"): test {@link HieuChinhVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class HieuChinhVbdiUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private HieuChinhVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new HieuChinhVbdiUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            authorAclFieldsService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private VbdiDocument newDocument(String documentType, String status, String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("Van ban di goc", documentType, status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    @Test
    void execute_withoutId_shouldReturnFailure() {
        var result = useCase.execute(new HieuChinhVbdiRequest());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_whenDocumentNotFound_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new HieuChinhVbdiRequest();
        request.setId(id);
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
    }

    @Test
    void execute_withNonVbdiDocument_shouldReturnFailure() {
        VbdiDocument document = newDocument("vbdi_res", VbdiStatus.DRAFT, "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    // Diff field CHI gom field da doi - phai MERGE len dynamic_fields hien co, KHONG xoa field cu
    // khong nam trong diff (khac SoanThaoVbdiUseCase, luon REPLACE toan bo luc tao moi).
    @Test
    void execute_shouldMergeChangedFieldsOntoExistingDynamicFields() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT,
            "{\"subject_note\":\"ghi chu cu\",\"document_number\":\"5\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        request.setFields(Map.of("document_number", "6"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        // "document_number" duoc FieldTypeInferrer suy ra kieu NUMBER (dung hanh vi da co - xem
        // DynamicFieldValueConverter) nen gia tri luu la 6.0 (Double), khong phai chuoi "6".
        assertThat(fields).containsEntry("document_number", 6.0);
        // Field cu khong nam trong diff phai con nguyen - dung la MERGE, khong phai REPLACE.
        assertThat(fields).containsEntry("subject_note", "ghi chu cu");
    }

    @Test
    void execute_withSubject_shouldUpdateTitle() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT, "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        request.setSubject("Trich yeu moi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getTitle()).isEqualTo("Trich yeu moi");
    }

    @Test
    void execute_withoutSubject_shouldKeepExistingTitle() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT, "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getTitle()).isEqualTo("Van ban di goc");
    }

    // Status: "0"/"2" (ma Domino tho) phai dich sang chu noi bo dung VbdiLegacyStatusCodes - dong bo
    // voi SoanThaoVbdiUseCase/GetVbdiDetailUseCase.
    @Test
    void execute_withLegacyStatusCode2_shouldTranslateToIssued() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT, "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        request.setStatus("2");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(VbdiStatus.ISSUED);
    }

    // Khong gui status (rong/null) nghia la GIU NGUYEN trang thai hien tai - KHAC SoanThaoVbdiUseCase
    // (rong mac dinh DRAFT, vi do la tao moi) - day la diem khac biet co chu y quan trong nhat.
    @Test
    void execute_withoutStatus_shouldKeepCurrentStatusUnchanged() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.PENDING_SIGNATURE, "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(VbdiStatus.PENDING_SIGNATURE);
    }

    @Test
    void execute_shouldEvictCacheAndLogUpdate() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT, "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new HieuChinhVbdiRequest();
        request.setId(document.getId());
        useCase.execute(request);

        verify(documentCacheService).evict(document.getId());
        verify(documentCacheService).evictAllListCaches();
        verify(documentLogService).logUpdate(any(), any(), any());
    }
}
