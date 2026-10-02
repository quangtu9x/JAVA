package com.td.infrastructure.onlyoffice;

import com.td.application.duthao.DraftProcessDocumentRepository;
import com.td.domain.duthao.DraftProcessDocument;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Yêu cầu người dùng 25/09/2026 — xem javadoc {@link LinkOnlyOfficeEditedFileToProcessUseCaseImpl}. */
@ExtendWith(MockitoExtension.class)
class LinkOnlyOfficeEditedFileToProcessUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepository;
    @Mock
    private DraftProcessDocumentRepository draftProcessDocumentRepository;

    @InjectMocks
    private LinkOnlyOfficeEditedFileToProcessUseCaseImpl useCase;

    private final UUID draftId = UUID.randomUUID();

    private FileMetadataEntity editedFile() {
        return FileMetadataEntity.builder().id(UUID.randomUUID()).documentId(draftId)
            .sourceFileId(UUID.randomUUID()).description("cu").build();
    }

    private DraftProcessDocument process(UUID id) {
        DraftProcessDocument p = mock(DraftProcessDocument.class);
        when(p.getId()).thenReturn(id);
        return p;
    }

    @Test
    void linksProcessAndStoresDescription_whenProcessBelongsToSameDraft() {
        FileMetadataEntity file = editedFile();
        UUID processId = UUID.randomUUID();
        when(fileMetadataRepository.findById(file.getId())).thenReturn(Optional.of(file));
        DraftProcessDocument processDoc = process(processId);
        when(draftProcessDocumentRepository.findByParentId(eq(draftId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(processDoc)));

        var result = useCase.execute(file.getId(), processId, "Đã sửa tệp \"a.docx\" qua OnlyOffice (giữ nguyên bản gốc)");

        assertThat(result.isSuccess()).isTrue();
        assertThat(file.getProcessId()).isEqualTo(processId);
        assertThat(file.getDescription()).startsWith("Đã sửa tệp \"a.docx\"");
        verify(fileMetadataRepository).save(file);
    }

    @Test
    void rejects_whenProcessBelongsToAnotherDraft() {
        FileMetadataEntity file = editedFile();
        when(fileMetadataRepository.findById(file.getId())).thenReturn(Optional.of(file));
        when(draftProcessDocumentRepository.findByParentId(eq(draftId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of()));

        assertThat(useCase.execute(file.getId(), UUID.randomUUID(), "x").isSuccess()).isFalse();
        verify(fileMetadataRepository, never()).save(any());
    }

    @Test
    void rejects_normalUploadedFile_withoutSourceFile() {
        FileMetadataEntity file = FileMetadataEntity.builder().id(UUID.randomUUID()).documentId(draftId).build();
        when(fileMetadataRepository.findById(file.getId())).thenReturn(Optional.of(file));

        assertThat(useCase.execute(file.getId(), UUID.randomUUID(), "x").isSuccess()).isFalse();
        verify(fileMetadataRepository, never()).save(any());
    }
}
