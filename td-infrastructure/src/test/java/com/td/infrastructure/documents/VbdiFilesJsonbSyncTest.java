package com.td.infrastructure.documents;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbdi.VbdiDocument;
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
 * Yêu cầu người dùng 28/08/2026 ("tương lai sẽ chuyển JSONB sang Elasticsearch để tìm kiếm, muốn
 * lưu cả files vào JSONB để khi tìm kiếm kết quả chỉ hiển thị, không phải gọi ngược vào database"):
 * test {@link VbdiFilesJsonbSync}.
 */
@ExtendWith(MockitoExtension.class)
class VbdiFilesJsonbSyncTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private VbdiFilesJsonbSync sync;

    private VbdiFilesJsonbSync newSync() {
        return new VbdiFilesJsonbSync(vbdiDocumentRepository, fileMetadataRepo, dynamicDocumentFieldsService);
    }

    private FileMetadataEntity newFile(String name, long size, String extension) {
        return FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .originalFilename(name)
            .fileSize(size)
            .fileExtension(extension)
            .build();
    }

    @Test
    void syncIfVbdiDocument_whenDocumentIsVbdi_shouldOverwriteFilesInDynamicFields() {
        sync = newSync();
        var document = new VbdiDocument("Cong van", "vbdi", "ISSUED", null, null, null, null);
        document.replaceDynamicFields("{\"subject\":\"Trich yeu\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(fileMetadataRepo.findAllByDocumentId(document.getId()))
            .thenReturn(List.of(newFile("bao_cao.docx", 1024L, "docx")));

        sync.syncIfVbdiDocument(document.getId());

        verify(vbdiDocumentRepository).save(document);
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
    void syncIfVbdiDocument_whenNotVbdiDocument_shouldDoNothing() {
        sync = newSync();
        UUID documentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentId)).thenReturn(Optional.empty());

        sync.syncIfVbdiDocument(documentId);

        verify(vbdiDocumentRepository, never()).save(any());
    }

    @Test
    void syncIfVbdiDocument_whenNullDocumentId_shouldDoNothingWithoutQuerying() {
        sync = newSync();

        sync.syncIfVbdiDocument(null);

        verify(vbdiDocumentRepository, never()).findById(any());
    }

    @Test
    void syncIfVbdiDocument_whenRepositoryThrows_shouldNotPropagateException() {
        sync = newSync();
        UUID documentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentId)).thenThrow(new RuntimeException("boom"));

        // Best-effort: khong duoc lam vo luong upload/xoa tep chinh (xem javadoc lop).
        sync.syncIfVbdiDocument(documentId);
    }
}
