package com.td.application.documentlinks;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.documentlinks.DocumentLinkType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 06/10/2026 - giai đoạn 2 bảng document_links (docs/architecture/document-links.md). */
class RelatedDocumentSnapshotsTest {

    private final InMemoryDocumentLinkRepository repository = new InMemoryDocumentLinkRepository();
    private final RelatedDocumentSnapshots snapshots = InMemoryDocumentLinkRepository.newSnapshots(repository);

    @Test
    void decorate_shouldRebuildBothFieldsInAttachOrder_andOverrideStaleValues() {
        UUID vbdi = UUID.randomUUID();
        UUID vbden1 = UUID.randomUUID();
        UUID vbden2 = UUID.randomUUID();
        UUID related = UUID.randomUUID();
        snapshots.linkItem(DocumentLinkType.VBDI_TRALOI_VBDEN, vbdi, Map.of("id", vbden1.toString(), "code", "A"));
        snapshots.linkItem(DocumentLinkType.VBDI_TRALOI_VBDEN, vbdi, Map.of("id", vbden2.toString(), "code", "B"));
        snapshots.linkItem(DocumentLinkType.VBDI_KEMTHEO_VBDEN, vbdi,
            Map.of("id", related.toString(), "database_type", "vbden"));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("documentin_obj", List.of(Map.of("id", "cu")));

        snapshots.decorate(fields, vbdi, RelatedDocumentSnapshots.SOURCE_VBDI);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> replies = (List<Map<String, Object>>) fields.get("documentin_obj");
        assertThat(replies).extracting(item -> item.get("code")).containsExactly("A", "B");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relatedItems = (List<Map<String, Object>>) fields.get("vanban_lienquan_obj");
        assertThat(relatedItems).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("database_type", "vbden"));
    }

    @Test
    void decorate_withoutLinks_shouldRemoveKeys() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("vanban_lienquan_obj", "[{\"id\":\"cu\"}]");

        snapshots.decorate(fields, UUID.randomUUID(), RelatedDocumentSnapshots.SOURCE_DUTHAO);

        assertThat(fields).doesNotContainKeys("documentin_obj", "vanban_lienquan_obj");
    }

    @Test
    void strip_thenImport_shouldTurnPayloadIntoLinks_skippingNonUuidAndGarbage() {
        UUID draft = UUID.randomUUID();
        UUID vbden = UUID.randomUUID();
        UUID vbdi = UUID.randomUUID();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("subject", "Trich yeu");
        payload.put("documentin_obj", "[{\"id\":\"" + vbden + "\",\"code\":\"DEN\"},{\"id\":\"AB12CD34EF56AB12CD34EF56AB12CD34\"}]");
        payload.put("vanban_lienquan_obj", "[{id=1, code=rac}]");

        Map<String, Object> removed = snapshots.strip(payload);
        assertThat(payload).containsOnlyKeys("subject");

        removed.put("vanban_lienquan_obj", List.of(Map.of("id", vbdi.toString(), "database_type", "vbdi")));
        snapshots.importFromPayload(removed, draft, RelatedDocumentSnapshots.SOURCE_DUTHAO);

        assertThat(repository.activeRows())
            .extracting(link -> link.getLinkType() + ":" + link.getTargetId())
            .containsExactlyInAnyOrder("duthao_traloi_vbden:" + vbden, "duthao_kemtheo_vbdi:" + vbdi);
        assertThat(RelatedDocumentSnapshots.toItems("[{id=1, code=rac}]")).isEmpty();
    }

    // 09/11/2026: bản chụp FE gửi không có files -> lấy tệp của văn bản đích lúc gắn; đã có files thì giữ nguyên.
    @Test
    void linkItem_shouldFillFilesOfTarget_whenSnapshotHasNone() {
        ListDocumentFilesUseCase listFiles = Mockito.mock(ListDocumentFilesUseCase.class);
        RelatedDocumentSnapshots withFiles = new RelatedDocumentSnapshots(
            InMemoryDocumentLinkRepository.newService(repository), listFiles);
        UUID draft = UUID.randomUUID();
        UUID vbden = UUID.randomUUID();
        UUID vbdenCoTep = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileDto file = FileDto.builder().fileId(fileId).originalFileName("CongVan.PDF").fileSize(10L).isPrimary(true).build();
        Mockito.when(listFiles.execute(vbden)).thenReturn(new PaginationResponse<>(List.of(file), 1, 1, 1, 1, true, true));

        withFiles.linkItem(DocumentLinkType.DUTHAO_TRALOI_VBDEN, draft, Map.of("id", vbden.toString(), "code", "A"));
        withFiles.linkItem(DocumentLinkType.DUTHAO_TRALOI_VBDEN, draft,
            Map.of("id", vbdenCoTep.toString(), "files", List.of(Map.of("name", "san.pdf"))));

        List<Map<String, Object>> items = withFiles.replyItems(draft, RelatedDocumentSnapshots.SOURCE_DUTHAO);
        assertThat(items).hasSize(2);
        assertThat(firstFile(items.get(0)))
            .containsEntry("name", "CongVan.PDF")
            .containsEntry("type", "pdf")
            .containsEntry("file_id", fileId.toString())
            .containsEntry("is_primary", true);
        assertThat(firstFile(items.get(1))).containsEntry("name", "san.pdf");
        Mockito.verify(listFiles, Mockito.never()).execute(vbdenCoTep);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstFile(Map<String, Object> item) {
        List<Map<String, Object>> files = (List<Map<String, Object>>) item.get("files");
        assertThat(files).hasSize(1);
        return files.get(0);
    }
}
