package com.td.application.realtime;

import com.td.domain.notifications.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Bản giả lập {@link UserNotificationRepository} trong bộ nhớ cho test (cùng ngữ nghĩa truy vấn JPQL). */
public class InMemoryUserNotificationRepository implements UserNotificationRepository {

    public final List<UserNotification> rows = new ArrayList<>();
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 27, 8, 0);

    @Override
    public List<UserNotification> saveAllInNewTransaction(List<UserNotification> notifications) {
        for (UserNotification n : notifications) {
            clock = clock.plusSeconds(1);
            setField(n, "createdOn", clock);
            rows.add(n);
        }
        return notifications;
    }

    @Override
    public Page<UserNotification> findForRecipients(Collection<String> keys, boolean unreadOnly, String databaseType,
                                                    Pageable pageable) {
        List<UserNotification> all = active(keys).stream()
            .filter(n -> !unreadOnly || !n.isRead())
            .filter(n -> databaseType == null || databaseType.equals(n.getDatabaseType()))
            .sorted(Comparator.comparing(UserNotification::getCreatedOn).reversed())
            .toList();
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    @Override
    public List<UserNotification> findLatestUnread(Collection<String> keys, int limit) {
        return active(keys).stream().filter(n -> !n.isRead())
            .sorted(Comparator.comparing(UserNotification::getCreatedOn).reversed()).limit(limit).toList();
    }

    @Override
    public long countUnread(Collection<String> keys) {
        return active(keys).stream().filter(n -> !n.isRead()).count();
    }

    @Override
    public List<Object[]> countUnreadGrouped(Collection<String> keys) {
        Map<String, Object[]> groups = new LinkedHashMap<>();
        active(keys).stream().filter(n -> !n.isRead())
            .sorted(Comparator.comparing(UserNotification::getCreatedOn).reversed())
            .forEach(n -> groups.compute(n.getDatabaseType() + "|" + n.getAction(), (k, g) -> g == null
                ? new Object[] {n.getDatabaseType(), n.getAction(), 1L, n.getCreatedOn()}
                : new Object[] {g[0], g[1], (Long) g[2] + 1, g[3]}));
        return new ArrayList<>(groups.values());
    }

    @Override
    public int markRead(UUID id, Collection<String> keys, String readBy, LocalDateTime now) {
        return markWhere(keys, n -> n.getId().equals(id), readBy, now);
    }

    @Override
    public int markAllRead(Collection<String> keys, String databaseType, String readBy, LocalDateTime now) {
        return markWhere(keys, n -> databaseType == null || databaseType.equals(n.getDatabaseType()), readBy, now);
    }

    @Override
    public int markReadByDocument(Collection<String> keys, String documentId, String readBy, LocalDateTime now) {
        return markWhere(keys, n -> documentId.equals(n.getDocumentId()), readBy, now);
    }

    @Override
    public int hide(UUID id, Collection<String> keys, LocalDateTime now) {
        int count = 0;
        for (UserNotification n : active(keys)) {
            if (n.getId().equals(id)) {
                n.markAsDeleted(null);
                count++;
            }
        }
        return count;
    }

    @Override
    public int softDeleteExpired(LocalDateTime readBefore, LocalDateTime anyBefore, int limit) {
        int count = 0;
        for (UserNotification n : new ArrayList<>(rows)) {
            if (count >= limit) {
                break;
            }
            boolean expired = n.getCreatedOn().isBefore(anyBefore) || (n.isRead() && n.getCreatedOn().isBefore(readBefore));
            if (!n.isDeleted() && expired) {
                n.markAsDeleted(null);
                count++;
            }
        }
        return count;
    }

    private int markWhere(Collection<String> keys, Predicate<UserNotification> filter, String readBy, LocalDateTime now) {
        int count = 0;
        for (UserNotification n : active(keys)) {
            if (!n.isRead() && filter.test(n)) {
                setField(n, "readOn", now);
                setField(n, "readBy", readBy);
                count++;
            }
        }
        return count;
    }

    private List<UserNotification> active(Collection<String> keys) {
        return rows.stream()
            .filter(n -> !n.isDeleted() && keys.contains(n.getRecipient().toLowerCase(Locale.ROOT)))
            .toList();
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ex) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
        throw new IllegalStateException("Không có field " + name);
    }
}
