package com.td.application.hosocv;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BL-240 (HoSoCV Core) — DoD (4): download file từ hồ sơ qua {@link DownloadFilesHoSoCVUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class DownloadFilesHoSoCVUseCaseTest {

    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;

    private DownloadFilesHoSoCVUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DownloadFilesHoSoCVUseCase(listDocumentFilesUseCase);
    }

    @Test
    void execute_shouldAggregateFilesForEachReferencedDocument() {
        UUID duThaoId = UUID.randomUUID();
        UUID vbdiId = UUID.randomUUID();
        FileDto file1 = FileDto.builder().fileId(UUID.randomUUID()).documentId(duThaoId).fileName("noidung.pdf").build();
        FileDto file2 = FileDto.builder().fileId(UUID.randomUUID()).documentId(vbdiId).fileName("phieuky.pdf").build();
        when(listDocumentFilesUseCase.execute(duThaoId))
            .thenReturn(new PaginationResponse<>(List.of(file1), 0, 1, 1L, 1, true, true));
        when(listDocumentFilesUseCase.execute(vbdiId))
            .thenReturn(new PaginationResponse<>(List.of(file2), 0, 1, 1L, 1, true, true));

        var request = new DownloadFilesHoSoCVRequest();
        var ref1 = new HoSoFileRefDto();
        ref1.setDocumentType("duthao");
        ref1.setDocumentId(duThaoId);
        var ref2 = new HoSoFileRefDto();
        ref2.setDocumentType("vbdi");
        ref2.setDocumentId(vbdiId);
        request.setVanBanLienQuan(List.of(ref1, ref2));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getDocumentType()).isEqualTo("duthao");
        assertThat(result.getData().get(0).getFiles()).containsExactly(file1);
        assertThat(result.getData().get(1).getDocumentType()).isEqualTo("vbdi");
        assertThat(result.getData().get(1).getFiles()).containsExactly(file2);
    }

    @Test
    void execute_withDocumentHavingNoFiles_shouldStillIncludeEmptyGroup() {
        UUID vbdenId = UUID.randomUUID();
        when(listDocumentFilesUseCase.execute(vbdenId))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 0, 0L, 0, true, true));

        var request = new DownloadFilesHoSoCVRequest();
        var ref = new HoSoFileRefDto();
        ref.setDocumentType("vbden");
        ref.setDocumentId(vbdenId);
        request.setVanBanLienQuan(List.of(ref));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getFiles()).isEmpty();
    }

    @Test
    void execute_withEmptyRequest_shouldReturnEmptyList() {
        var result = useCase.execute(new DownloadFilesHoSoCVRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }
}
