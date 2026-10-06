package com.td.application.documentlinks;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documentlinks.DocumentLink;
import com.td.domain.documentlinks.DocumentLinkType;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 06/10/2026 - điểm DUY NHẤT tạo/gỡ liên kết văn bản (bảng {@code document_links}, xem
 * {@code docs/architecture/document-links.md}). Use case nghiệp vụ (GanKemVanBan, ChuyenThanhVanBanDi...)
 * gọi service này thay cho việc tự ghi field id nối ";" vào {@code dynamic_fields} của cả 2 văn bản.
 * Idempotent: gắn lại liên kết đang có / gỡ liên kết không có đều là no-op.
 */
@Service
@RequiredArgsConstructor
public class DocumentLinkService {

    private final DocumentLinkRepository documentLinkRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    /** @return true nếu tạo mới, false nếu đã có sẵn. */
    @Transactional
    public boolean link(DocumentLinkType type, UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null) {
            return false;
        }
        if (documentLinkRepository.findActive(sourceId, targetId, type.code()).isPresent()) {
            return false;
        }
        documentLinkRepository.save(new DocumentLink(type, sourceId, targetId));
        return true;
    }

    /**
     * Gắn kèm bản chụp {@code linkDataJson} (cột link_data). Liên kết đang có thì GIỮ NGUYÊN bản chụp cũ
     * (khớp hành vi dedup theo id của documentin_obj/vanban_lienquan_obj trước đây).
     * @return true nếu tạo mới, false nếu đã có sẵn.
     */
    @Transactional
    public boolean link(DocumentLinkType type, UUID sourceId, UUID targetId, String linkDataJson) {
        if (sourceId == null || targetId == null) {
            return false;
        }
        if (documentLinkRepository.findActive(sourceId, targetId, type.code()).isPresent()) {
            return false;
        }
        documentLinkRepository.save(new DocumentLink(type, sourceId, targetId, linkDataJson));
        return true;
    }

    /** Liên kết đi ra từ 1 nguồn, theo THỨ TỰ GẮN (cũ nhất trước) - dùng dựng danh sách hiển thị. */
    @Transactional(readOnly = true)
    public List<DocumentLink> findLinks(UUID sourceId, Collection<DocumentLinkType> types) {
        List<DocumentLink> links = new java.util.ArrayList<>(documentLinkRepository.findActiveBySource(sourceId, codes(types)));
        links.sort(java.util.Comparator.comparing(DocumentLink::getCreatedOn,
            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return links;
    }

    /** @return true nếu có liên kết bị gỡ (xoá mềm). */
    @Transactional
    public boolean unlink(DocumentLinkType type, UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null) {
            return false;
        }
        return documentLinkRepository.findActive(sourceId, targetId, type.code())
            .map(link -> {
                link.markAsDeleted(resolveCurrentUserId());
                documentLinkRepository.save(link);
                return true;
            })
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<UUID> findTargetIds(UUID sourceId, Collection<DocumentLinkType> types) {
        return documentLinkRepository.findActiveBySource(sourceId, codes(types)).stream()
            .map(DocumentLink::getTargetId)
            .distinct()
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> findSourceIds(UUID targetId, Collection<DocumentLinkType> types) {
        return documentLinkRepository.findActiveByTarget(targetId, codes(types)).stream()
            .map(DocumentLink::getSourceId)
            .distinct()
            .toList();
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }

    private static List<String> codes(Collection<DocumentLinkType> types) {
        return types.stream().map(DocumentLinkType::code).toList();
    }
}
