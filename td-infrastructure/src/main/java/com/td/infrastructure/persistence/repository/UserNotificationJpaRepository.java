package com.td.infrastructure.persistence.repository;

import com.td.domain.notifications.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 27/10/2026 - bảng user_notifications (hộp thư thông báo), xem docs/architecture/realtime-socket-notifications.md. */
public interface UserNotificationJpaRepository extends JpaRepository<UserNotification, UUID> {

    @Query("SELECT COUNT(n) FROM UserNotification n WHERE lower(n.recipient) IN :keys AND n.deletedOn IS NULL"
        + " AND n.readOn IS NULL")
    long countUnread(@Param("keys") Collection<String> keys);

    /**
     * 1 khoá người nhận (bằng =, không IN): Postgres đi thẳng index (lower(recipient), created_on DESC) và dừng ở
     * LIMIT - chi phí theo số dòng cần lấy, KHÔNG theo kích thước bảng. Với IN nhiều khoá, planner chọn quét ngược
     * index created_on rồi lọc (đo 32ms/300k dòng, tăng tuyến tính) - xem PostgresUserNotificationRepository.
     */
    @Query("SELECT n FROM UserNotification n WHERE lower(n.recipient) = :key AND n.deletedOn IS NULL"
        + " AND (:unreadOnly = false OR n.readOn IS NULL)"
        + " AND (:databaseType = '' OR n.databaseType = :databaseType)"
        + " ORDER BY n.createdOn DESC")
    List<UserNotification> findTopForRecipient(@Param("key") String key, @Param("unreadOnly") boolean unreadOnly,
                                               @Param("databaseType") String databaseType, Pageable pageable);

    @Query("SELECT COUNT(n) FROM UserNotification n WHERE lower(n.recipient) IN :keys AND n.deletedOn IS NULL"
        + " AND (:unreadOnly = false OR n.readOn IS NULL)"
        + " AND (:databaseType = '' OR n.databaseType = :databaseType)")
    long countForRecipients(@Param("keys") Collection<String> keys, @Param("unreadOnly") boolean unreadOnly,
                            @Param("databaseType") String databaseType);

    @Query("SELECT n.databaseType, n.action, COUNT(n), MAX(n.createdOn) FROM UserNotification n"
        + " WHERE lower(n.recipient) IN :keys AND n.deletedOn IS NULL AND n.readOn IS NULL"
        + " GROUP BY n.databaseType, n.action ORDER BY MAX(n.createdOn) DESC")
    List<Object[]> countUnreadGrouped(@Param("keys") Collection<String> keys);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readOn = :now, n.readBy = :readBy WHERE n.id = :id"
        + " AND lower(n.recipient) IN :keys AND n.readOn IS NULL AND n.deletedOn IS NULL")
    int markRead(@Param("id") UUID id, @Param("keys") Collection<String> keys, @Param("readBy") String readBy,
                 @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readOn = :now, n.readBy = :readBy WHERE lower(n.recipient) IN :keys"
        + " AND (:databaseType = '' OR n.databaseType = :databaseType)"
        + " AND n.readOn IS NULL AND n.deletedOn IS NULL")
    int markAllRead(@Param("keys") Collection<String> keys, @Param("databaseType") String databaseType,
                    @Param("readBy") String readBy, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readOn = :now, n.readBy = :readBy WHERE lower(n.recipient) IN :keys"
        + " AND n.documentId = :documentId AND n.readOn IS NULL AND n.deletedOn IS NULL")
    int markReadByDocument(@Param("keys") Collection<String> keys, @Param("documentId") String documentId,
                           @Param("readBy") String readBy, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserNotification n SET n.deletedOn = :now WHERE n.id = :id AND lower(n.recipient) IN :keys"
        + " AND n.deletedOn IS NULL")
    int hide(@Param("id") UUID id, @Param("keys") Collection<String> keys, @Param("now") LocalDateTime now);

    // Dọn theo lô (LIMIT) để không khoá/ghi WAL quá lớn 1 lần; dùng IX_user_notifications_created_on (V1.0.79).
    @Modifying
    @Query(value = "UPDATE user_notifications SET deleted_on = NOW() WHERE id IN ("
        + " SELECT id FROM user_notifications WHERE deleted_on IS NULL AND created_on < :anyBefore"
        + " UNION ALL"
        + " SELECT id FROM user_notifications WHERE deleted_on IS NULL AND read_on IS NOT NULL"
        + "   AND created_on >= :anyBefore AND created_on < :readBefore"
        + " LIMIT :limit)", nativeQuery = true)
    int softDeleteExpired(@Param("readBefore") LocalDateTime readBefore, @Param("anyBefore") LocalDateTime anyBefore,
                          @Param("limit") int limit);
}
