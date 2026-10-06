package com.td.application.documentlinks;

import com.td.domain.documentlinks.DocumentLink;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 06/10/2026 - bảng {@code document_links} (xem {@code docs/architecture/document-links.md}). Mọi truy
 * vấn chỉ trả dòng CHƯA xoá mềm.
 */
public interface DocumentLinkRepository {

    <S extends DocumentLink> S save(S entity);

    Optional<DocumentLink> findActive(UUID sourceId, UUID targetId, String linkType);

    /** Liên kết đi ra từ 1 văn bản nguồn (vd mọi dự thảo của 1 hồ sơ), mới nhất trước. */
    List<DocumentLink> findActiveBySource(UUID sourceId, Collection<String> linkTypes);

    /** Liên kết trỏ tới 1 văn bản đích (vd mọi hồ sơ chứa 1 dự thảo), mới nhất trước. */
    List<DocumentLink> findActiveByTarget(UUID targetId, Collection<String> linkTypes);
}
