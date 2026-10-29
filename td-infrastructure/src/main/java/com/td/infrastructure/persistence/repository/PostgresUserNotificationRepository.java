package com.td.infrastructure.persistence.repository;

import com.td.application.realtime.UserNotificationRepository;
import com.td.domain.notifications.UserNotification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 27/10/2026 - hộp thư thông báo (DB đơn vị), xem docs/architecture/realtime-socket-notifications.md. */
@Repository
public class PostgresUserNotificationRepository implements UserNotificationRepository {

    private final UserNotificationJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresUserNotificationRepository(UserNotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    // Transaction MỚI (gọi từ luồng nền sau commit nghiệp vụ). persist() thay vì saveAll(): id tự gán nên
    // saveAll() coi là bản ghi cũ -> merge() -> thêm 1 SELECT/dòng; persist() chỉ INSERT (gộp lô theo
    // hibernate.jdbc.batch_size).
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UserNotification> saveAllInNewTransaction(List<UserNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        notifications.forEach(entityManager::persist);
        entityManager.flush();
        return notifications;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int softDeleteExpired(LocalDateTime readBefore, LocalDateTime anyBefore, int limit) {
        return jpaRepository.softDeleteExpired(readBefore, anyBefore, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserNotification> findForRecipients(Collection<String> recipientKeys, boolean unreadOnly,
                                                    String databaseType, Pageable pageable) {
        String filter = nullToEmpty(databaseType);
        List<UserNotification> top = topPerRecipient(recipientKeys, unreadOnly, filter,
            (int) pageable.getOffset() + pageable.getPageSize());
        int from = (int) Math.min(pageable.getOffset(), top.size());
        int to = Math.min(from + pageable.getPageSize(), top.size());
        long total = jpaRepository.countForRecipients(recipientKeys, unreadOnly, filter);
        return new PageImpl<>(new ArrayList<>(top.subList(from, to)), pageable, total);
    }

    /**
     * Mỗi khoá người nhận (cá nhân + đơn vị, tối đa vài khoá) lấy {@code need} dòng mới nhất bằng 1 truy vấn đi
     * thẳng index rồi trộn theo thời gian - nhanh như nhau dù bảng vài nghìn hay vài triệu dòng (xem
     * {@link UserNotificationJpaRepository#findTopForRecipient}).
     */
    private List<UserNotification> topPerRecipient(Collection<String> keys, boolean unreadOnly, String databaseType, int need) {
        PageRequest firstRows = PageRequest.of(0, Math.max(1, need));
        List<UserNotification> merged = new ArrayList<>();
        for (String key : keys) {
            merged.addAll(jpaRepository.findTopForRecipient(key, unreadOnly, databaseType, firstRows));
        }
        merged.sort(Comparator.comparing(UserNotification::getCreatedOn, Comparator.nullsLast(Comparator.reverseOrder())));
        return merged.size() > need ? merged.subList(0, need) : merged;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Collection<String> recipientKeys) {
        return jpaRepository.countUnread(recipientKeys);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserNotification> findLatestUnread(Collection<String> recipientKeys, int limit) {
        return new ArrayList<>(topPerRecipient(recipientKeys, true, "", limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> countUnreadGrouped(Collection<String> recipientKeys) {
        return jpaRepository.countUnreadGrouped(recipientKeys);
    }

    @Override
    @Transactional
    public int markRead(UUID id, Collection<String> recipientKeys, String readBy, LocalDateTime now) {
        return jpaRepository.markRead(id, recipientKeys, readBy, now);
    }

    @Override
    @Transactional
    public int markAllRead(Collection<String> recipientKeys, String databaseType, String readBy, LocalDateTime now) {
        return jpaRepository.markAllRead(recipientKeys, nullToEmpty(databaseType), readBy, now);
    }

    @Override
    @Transactional
    public int markReadByDocument(Collection<String> recipientKeys, String documentId, String readBy, LocalDateTime now) {
        return jpaRepository.markReadByDocument(recipientKeys, documentId, readBy, now);
    }

    @Override
    @Transactional
    public int hide(UUID id, Collection<String> recipientKeys, LocalDateTime now) {
        return jpaRepository.hide(id, recipientKeys, now);
    }

    // Bộ lọc "không lọc" = chuỗi rỗng (tránh tham số NULL không suy được kiểu ở Postgres).
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
