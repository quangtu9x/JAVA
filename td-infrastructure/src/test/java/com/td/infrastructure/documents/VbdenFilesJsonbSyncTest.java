package com.td.infrastructure.documents;

import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documents.BusinessDocument;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 04/09/2026 ("Tệp đính kèm: khi gắn tệp đính kèm lưu luôn thông tin files vào
 * dynamic_fields để không phải đọc lại DB để map, nếu không có mới đọc lại (văn bản đi đã làm như
 * vậy)"): test {@link VbdenFilesJsonbSync} — port y hệt {@code VbdiFilesJsonbSyncTest} sang vbden.
 */
@ExtendWith(MockitoExtension.class)
class VbdenFilesJsonbSyncTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private VbdenFilesJsonbSync sync;

    private VbdenFilesJsonbSync newSync() {
        return new VbdenFilesJsonbSync(documentRepository, fileMetadataRepo, dynamicDocumentFieldsService);
    }

    private FileMetadataEntity newFile(String name, long size, String extension) {
        return FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .originalFilename(name)
            .fileSize(size)
            .fileExtension(extension)
            .build();
    }

    private BusinessDocument newDocument(String documentType) {
        return new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
    }

    @Test
    void syncIfVbdenDocument_whenDocumentIsVbden_shouldOverwriteFilesInDynamicFields() {
        sync = newSync();
        var document = newDocument("vbden");
        document.replaceDynamicFields("{\"subject\":\"Trich yeu\"}");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(fileMetadataRepo.findAllByDocumentId(document.getId()))
            .thenReturn(List.of(newFile("bao_cao.docx", 1024L, "docx")));

        sync.syncIfVbdenDocument(document.getId());

        verify(documentRepository).save(document);
        var fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsKey("files");
        // Field khac van con nguyen - chi ghi de "files", khong xoa field khac (merge, khong overwrite toan bo).
        assertThat(fields).containsEntry("subject", "Trich yeu");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> files = (List<java.util.Map<String, Object>>) fields.get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("name", "bao_cao.docx").containsEntry("type", "docx");
    }

    @Test
    void syncIfVbdenDocument_whenDocumentNotFound_shouldDoNothing() {
        sync = newSync();
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        sync.syncIfVbdenDocument(documentId);

        verify(documentRepository, never()).save(any());
    }

    // Khac VbdiFilesJsonbSync: vbden dung CHUNG bang "documents" voi nhieu document_type khac (vbden_res/
    // vbden_so/vbcddh/...) qua DocumentRepository chung - PHAI kiem tra them documentType, "tim thay
    // qua repository" khong du de suy ra la vbden (khac vbdi co bang rieng).
    @Test
    void syncIfVbdenDocument_whenDocumentIsNotVbdenType_shouldDoNothing() {
        sync = newSync();
        var document = newDocument("vbden_res");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        sync.syncIfVbdenDocument(document.getId());

        verify(documentRepository, never()).save(any());
        verify(fileMetadataRepo, never()).findAllByDocumentId(any());
    }

    @Test
    void syncIfVbdenDocument_whenNullDocumentId_shouldDoNothingWithoutQuerying() {
        sync = newSync();

        sync.syncIfVbdenDocument(null);

        verify(documentRepository, never()).findById(any());
    }

    @Test
    void syncIfVbdenDocument_whenRepositoryThrows_shouldNotPropagateException() {
        sync = newSync();
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenThrow(new RuntimeException("boom"));

        // Best-effort: khong duoc lam vo luong upload/xoa tep chinh (xem javadoc lop).
        sync.syncIfVbdenDocument(documentId);
    }
}
