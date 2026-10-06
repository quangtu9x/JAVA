package com.td.application.documentlinks;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documentlinks.DocumentLink;
import com.td.domain.documentlinks.DocumentLinkType;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 06/10/2026 - fake bảng document_links cho unit test (giữ thứ tự chèn, mới nhất trước khi đọc). */
public class InMemoryDocumentLinkRepository implements DocumentLinkRepository {

    private final List<DocumentLink> rows = new ArrayList<>();
    // created_on tăng dần theo thứ tự chèn (như @PrePersist thật) - findLinks sắp theo cột này.
    private java.time.LocalDateTime clock = java.time.LocalDateTime.of(2026, 1, 1, 0, 0);

    // Constructor AuditableEntity gán sẵn now() (2 liên kết tạo liền nhau có thể trùng giờ) -> luôn đóng
    // dấu tăng dần lúc chèn để thứ tự gắn xác định.
    private void stamp(DocumentLink link) {
        clock = clock.plusSeconds(1);
        link.setCreatedOn(clock);
    }

    /** DocumentLinkService thật trên fake này (người dùng hiện tại rỗng). */
    public static DocumentLinkService newService(InMemoryDocumentLinkRepository repository) {
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        Mockito.lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        return new DocumentLinkService(repository, currentUserProvider, Mockito.mock(AppUserRepository.class));
    }

    /** RelatedDocumentSnapshots thật trên fake này. */
    public static RelatedDocumentSnapshots newSnapshots(InMemoryDocumentLinkRepository repository) {
        return new RelatedDocumentSnapshots(newService(repository));
    }

    public InMemoryDocumentLinkRepository seed(DocumentLinkType type, UUID sourceId, UUID targetId) {
        DocumentLink link = new DocumentLink(type, sourceId, targetId);
        stamp(link);
        rows.add(link);
        return this;
    }

    public List<DocumentLink> activeRows() {
        return rows.stream().filter(row -> !row.isDeleted()).toList();
    }

    @Override
    public <S extends DocumentLink> S save(S entity) {
        if (!rows.contains(entity)) {
            stamp(entity);
            rows.add(entity);
        }
        return entity;
    }

    @Override
    public Optional<DocumentLink> findActive(UUID sourceId, UUID targetId, String linkType) {
        return activeRows().stream()
            .filter(row -> row.getSourceId().equals(sourceId) && row.getTargetId().equals(targetId)
                && row.getLinkType().equals(linkType))
            .findFirst();
    }

    @Override
    public List<DocumentLink> findActiveBySource(UUID sourceId, Collection<String> linkTypes) {
        return newestFirst(activeRows().stream()
            .filter(row -> row.getSourceId().equals(sourceId) && linkTypes.contains(row.getLinkType()))
            .toList());
    }

    @Override
    public List<DocumentLink> findActiveByTarget(UUID targetId, Collection<String> linkTypes) {
        return newestFirst(activeRows().stream()
            .filter(row -> row.getTargetId().equals(targetId) && linkTypes.contains(row.getLinkType()))
            .toList());
    }

    private List<DocumentLink> newestFirst(List<DocumentLink> list) {
        List<DocumentLink> copy = new ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }
}
