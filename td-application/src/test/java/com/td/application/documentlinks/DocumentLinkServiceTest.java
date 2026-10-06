package com.td.application.documentlinks;

import com.td.domain.documentlinks.DocumentLinkType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 06/10/2026 - bảng document_links (docs/architecture/document-links.md). */
class DocumentLinkServiceTest {

    private final InMemoryDocumentLinkRepository repository = new InMemoryDocumentLinkRepository();
    private final DocumentLinkService service = InMemoryDocumentLinkRepository.newService(repository);

    @Test
    void link_isIdempotent_andReadableBothDirections() {
        UUID hosocv = UUID.randomUUID();
        UUID duthao = UUID.randomUUID();

        assertThat(service.link(DocumentLinkType.HOSO_DUTHAO, hosocv, duthao)).isTrue();
        assertThat(service.link(DocumentLinkType.HOSO_DUTHAO, hosocv, duthao)).isFalse();

        assertThat(repository.activeRows()).hasSize(1);
        assertThat(repository.activeRows().get(0).getSourceType()).isEqualTo("hosocv");
        assertThat(repository.activeRows().get(0).getTargetType()).isEqualTo("duthao");
        assertThat(service.findTargetIds(hosocv, List.of(DocumentLinkType.HOSO_DUTHAO))).containsExactly(duthao);
        assertThat(service.findSourceIds(duthao, List.of(DocumentLinkType.HOSO_DUTHAO))).containsExactly(hosocv);
        // Khác loại liên kết -> không thấy.
        assertThat(service.findTargetIds(hosocv, List.of(DocumentLinkType.HOSO_VBDI))).isEmpty();
    }

    @Test
    void unlink_isSoftDelete_andRelinkCreatesNewRow() {
        UUID hosocv = UUID.randomUUID();
        UUID vbdi = UUID.randomUUID();
        service.link(DocumentLinkType.HOSO_VBDI, hosocv, vbdi);

        assertThat(service.unlink(DocumentLinkType.HOSO_VBDI, hosocv, vbdi)).isTrue();
        assertThat(service.unlink(DocumentLinkType.HOSO_VBDI, hosocv, vbdi)).isFalse();
        assertThat(service.findTargetIds(hosocv, List.of(DocumentLinkType.HOSO_VBDI))).isEmpty();

        assertThat(service.link(DocumentLinkType.HOSO_VBDI, hosocv, vbdi)).isTrue();
        assertThat(service.findTargetIds(hosocv, List.of(DocumentLinkType.HOSO_VBDI))).containsExactly(vbdi);
    }
}
