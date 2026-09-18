package com.td.application.vbdi;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BL-220 (VBDi Core), tách bảng riêng vbdi_documents: {@link GetVbdiDetailUseCase} chỉ trả về
 * document thực sự là văn bản đi ({@code databasetype=vbdi}), đọc TRỰC TIẾP
 * {@link VbdiDocumentRepository} (bảng vật lý {@code vbdi_documents}) — không còn uỷ quyền cho
 * {@code GetDynamicDocumentUseCase} (bảng {@code documents} dùng chung, sẽ KHÔNG tìm thấy document
 * loại này nữa sau khi tách) — xem javadoc lớp {@link GetVbdiDetailUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetVbdiDetailUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetVbdiDetailUseCase useCase;

    private void stubEmptyFiles() {
        // 28/08/2026 ("đã port đính kèm sang JAVA chưa"): GetVbdiDetailUseCase giờ luôn gọi
        // listDocumentFilesUseCase - stub mặc định trang rỗng để các test không quan tâm "files"
        // không bị NPE; test riêng execute_withFiles_shouldIncludeFilesInResponse override lại.
        lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
    }

    @Test
    void execute_withoutId_shouldReturnFailure() {
        useCase = newUseCase();
        var result = useCase.execute(new GetVbdiDetailRequest());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withVbdiDocument_shouldReturnFields() {
        useCase = newUseCase();
        stubEmptyFiles();
        VbdiDocument document = newDocument("vbdi", "{\"document_number\":\"5/2026-VBD\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("document_number", "5/2026-VBD");
        assertThat(result.getData()).containsEntry("databasetype", "vbdi");
        assertThat(result.getData()).containsEntry("files", List.of());
        // "port xem chi tiet van ban di" 28/08/2026: status phai la ma Domino tho "0" (KHONG phai
        // "DRAFT" - se lam ChiTietModal.jsx#shouldBeReadOnly khoa read-only vinh vien).
        assertThat(result.getData()).containsEntry("status", "0");
    }

    // "port xem chi tiet van ban di" 28/08/2026: 2 ma DA CO bang chung dxl (VbdiStatus.DRAFT/ISSUED)
    // phai dich nguoc dung chieu SoanThaoVbdiUseCase#LEGACY_STATUS_CODES; PENDING_SIGNATURE (chua
    // xac nhan ma Domino) phai GIU NGUYEN chu (passthrough).
    @Test
    void execute_withIssuedStatus_shouldTranslateToLegacyCode2() {
        useCase = newUseCase();
        stubEmptyFiles();
        VbdiDocument document = new VbdiDocument("Cong van", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        document.replaceDynamicFields("{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("status", "2");
    }

    @Test
    void execute_withPendingSignatureStatus_shouldPassThroughUnchanged() {
        useCase = newUseCase();
        stubEmptyFiles();
        VbdiDocument document = new VbdiDocument("Cong van", "vbdi", VbdiStatus.PENDING_SIGNATURE, null, null, null, null);
        document.replaceDynamicFields("{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("status", VbdiStatus.PENDING_SIGNATURE);
    }

    // 28/08/2026 ("đã port đính kèm sang JAVA chưa"): "files" phải xuất hiện trong response, đúng
    // shape {name,size,type,file_id} Files/filesVBdi.jsx đang đọc (resdata?.files).
    @Test
    void execute_withFiles_shouldIncludeFilesInResponse() {
        useCase = newUseCase();
        VbdiDocument document = newDocument("vbdi", "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        UUID fileId = UUID.randomUUID();
        var fileDto = FileDto.builder()
            .fileId(fileId)
            .originalFileName("bao_cao.docx")
            .fileSize(1024L)
            .build();
        when(listDocumentFilesUseCase.execute(document.getId()))
            .thenReturn(new PaginationResponse<>(List.of(fileDto), 0, 20, 1L, 1, true, true));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> files = (List<java.util.Map<String, Object>>) result.getData().get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0))
            .containsEntry("name", "bao_cao.docx")
            .containsEntry("size", 1024L)
            .containsEntry("type", "docx")
            .containsEntry("file_id", fileId);
    }

    @Test
    void execute_withNonVbdiDocument_shouldReturnFailure() {
        useCase = newUseCase();
        // Sau khi tách, bang vbdi_documents CHI chua 2 document_type cua cum vbdi (vbdi/vbdi_res) -
        // guard nay van giu an toan voi du lieu la/phan loai sai.
        VbdiDocument document = newDocument("vbdi_res", "{}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
    }

    @Test
    void execute_whenDocumentNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GetVbdiDetailRequest();
        request.setId(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
    }

    // SoanThaoVbdiUseCase luu "subject" vao cot tinh "title", khong vao dynamic_fields - dam bao
    // fallback putIfAbsent hoat dong dung cho van ban vua tao qua Java.
    @Test
    void execute_whenSubjectMissingFromDynamicFields_shouldFallbackToEntityTitle() {
        useCase = newUseCase();
        stubEmptyFiles();
        VbdiDocument document = newDocument("vbdi", "{}", "Trich yeu goc");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Trich yeu goc");
    }

    // Van ban da tung co "subject" rieng trong dynamic_fields - KHONG duoc de len (putIfAbsent).
    @Test
    void execute_whenSubjectAlreadyInDynamicFields_shouldKeepIt() {
        useCase = newUseCase();
        stubEmptyFiles();
        VbdiDocument document = newDocument("vbdi", "{\"subject\":\"Trich yeu da sua\"}", "Trich yeu goc");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetVbdiDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Trich yeu da sua");
    }

    private GetVbdiDetailUseCase newUseCase() {
        return new GetVbdiDetailUseCase(vbdiDocumentRepository, listDocumentFilesUseCase, dynamicDocumentFieldsService);
    }

    private VbdiDocument newDocument(String documentType, String dynamicFieldsJson) {
        return newDocument(documentType, dynamicFieldsJson, "Tieu de");
    }

    private VbdiDocument newDocument(String documentType, String dynamicFieldsJson, String title) {
        VbdiDocument document = new VbdiDocument(title, documentType, "DRAFT", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }
}