package com.td.application.duthao;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BL-230 (DuThao Core) — Slice 1: {@link GetDuThaoDetailUseCase} chỉ trả về document thực sự là
 * dự thảo ({@code databasetype=duthao}).
 *
 * <p>Cập nhật 24/08/2026 (yêu cầu người dùng "tạo bảng riêng, để độc lập dữ liệu"): use case đọc
 * TRỰC TIẾP {@link DuThaoDocumentRepository} (bảng vật lý {@code duthao_documents}) — không còn uỷ
 * quyền cho {@code GetDynamicDocumentUseCase} (bảng {@code documents} dùng chung, sẽ KHÔNG tìm thấy
 * document loại này nữa) — xem javadoc lớp {@link GetDuThaoDetailUseCase}.</p>
 *
 * <p>Cập nhật 27/08/2026 (yêu cầu người dùng "quản lý tệp đính kèm theo id cha"): "files" là danh
 * sách PHẲNG (1 query {@link ListDocumentFilesUseCase#execute}) theo {@code document_id} = id dự
 * thảo — KHÔNG còn tổng hợp qua nhiều vết {@code draft_process} con (thiết kế trước đó, đã đảo
 * lại trong cùng ngày).</p>
 */
@ExtendWith(MockitoExtension.class)
class GetDuThaoDetailUseCaseTest {

    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetDuThaoDetailUseCase useCase;

    @Test
    void execute_withoutId_shouldReturnFailure() {
        useCase = newUseCase();
        var result = useCase.execute(new GetDuThaoDetailRequest());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withDuThaoDocument_shouldReturnFields() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{\"unit_in_charge\":\"So Noi Vu\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        stubEmptyFiles(document.getId());

        var request = new GetDuThaoDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("unit_in_charge", "So Noi Vu");
        assertThat(result.getData()).containsEntry("databasetype", "duthao");
        assertThat(result.getData()).containsEntry("files", List.of());
    }

    // Bugfix 27/08/2026: "files" cua document chinh la 1 danh sach PHANG theo document_id = id du
    // thao (khop moi module khac dung chung /api/v1/documents/{id}/files) - khoa dung shape
    // {name, size, type} FE dang doc (Files/index.jsx), khong phai shape FileDto goc.
    @Test
    void execute_withFilesOnDraft_shouldReturnFlatFileList() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(listDocumentFilesUseCase.execute(document.getId())).thenReturn(
            new PaginationResponse<>(
                List.of(newFileDto("bao_cao.docx", 1024L), newFileDto("phu_luc.pdf", 2048L)),
                0, 20, 2L, 1, true, true));

        var request = new GetDuThaoDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getData().get("files");
        assertThat(files).hasSize(2);
        assertThat(files).anySatisfy(file -> {
            assertThat(file).containsEntry("name", "bao_cao.docx");
            assertThat(file).containsEntry("size", 1024L);
            assertThat(file).containsEntry("type", "docx");
        });
        assertThat(files).anySatisfy(file -> {
            assertThat(file).containsEntry("name", "phu_luc.pdf");
            assertThat(file).containsEntry("size", 2048L);
            assertThat(file).containsEntry("type", "pdf");
        });
    }

    @Test
    void execute_withNonDuThaoDocument_shouldReturnFailure() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("draft_process", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new GetDuThaoDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản dự thảo");
    }

    @Test
    void execute_whenDocumentNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GetDuThaoDetailRequest();
        request.setId(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản dự thảo");
    }

    // TaoDuThaoUseCase luu "subject" vao cot tinh "title", khong vao dynamic_fields - dam bao
    // fallback putIfAbsent hoat dong dung cho van ban vua tao qua Java (chua tung Luu()).
    @Test
    void execute_whenSubjectMissingFromDynamicFields_shouldFallbackToEntityTitle() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{}", "Trich yeu goc");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        stubEmptyFiles(document.getId());

        var request = new GetDuThaoDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Trich yeu goc");
    }

    // Van ban da tung HIEU CHINH qua Luu() co "subject" rieng trong dynamic_fields - KHONG duoc de
    // len (putIfAbsent, khong phai put).
    @Test
    void execute_whenSubjectAlreadyInDynamicFields_shouldKeepIt() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{\"subject\":\"Trich yeu da sua\"}", "Trich yeu goc");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        stubEmptyFiles(document.getId());

        var request = new GetDuThaoDetailRequest();
        request.setId(document.getId().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Trich yeu da sua");
    }

    private GetDuThaoDetailUseCase newUseCase() {
        return new GetDuThaoDetailUseCase(
            duThaoDocumentRepository, listDocumentFilesUseCase, dynamicDocumentFieldsService,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository));
    }

    private void stubEmptyFiles(UUID documentId) {
        when(listDocumentFilesUseCase.execute(documentId))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
    }

    private FileDto newFileDto(String originalFileName, long fileSize) {
        return FileDto.builder()
            .fileId(UUID.randomUUID())
            .originalFileName(originalFileName)
            .fileSize(fileSize)
            .build();
    }

    private DuThaoDocument newDocument(String documentType, String dynamicFieldsJson) {
        return newDocument(documentType, dynamicFieldsJson, "Tieu de");
    }

    private DuThaoDocument newDocument(String documentType, String dynamicFieldsJson, String title) {
        DuThaoDocument document = new DuThaoDocument(title, documentType, "0", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }
}
