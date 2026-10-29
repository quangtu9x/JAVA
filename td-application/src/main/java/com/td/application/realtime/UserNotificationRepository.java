package com.td.application.realtime;

import com.td.domain.notifications.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Bảng {@code user_notifications} (DB đơn vị). {@code recipientKeys} luôn viết THƯỜNG (so khớp
 * {@code lower(recipient)}). Mọi truy vấn chỉ trả dòng chưa xoá mềm.
 */
public interface UserNotificationRepository {

    /** Ghi trong transaction RIÊNG (gọi sau commit nghiệp vụ - lỗi ở đây không được lan ra nghiệp vụ). */
    List<UserNotification> saveAllInNewTransaction(List<UserNotification> notifications);

    Page<UserNotification> findForRecipients(Collection<String> recipientKeys, boolean unreadOnly, String databaseType,
                                             Pageable pageable);

    long countUnread(Collection<String> recipientKeys);

    /** {@code limit} thông báo chưa đọc mới nhất - không kèm câu COUNT như Page. */
    List<UserNotification> findLatestUnread(Collection<String> recipientKeys, int limit);

    /** Mỗi phần tử: [databaseType, action, count(Long), latestCreatedOn(LocalDateTime)]. */
    List<Object[]> countUnreadGrouped(Collection<String> recipientKeys);

    int markRead(UUID id, Collection<String> recipientKeys, String readBy, LocalDateTime now);

    int markAllRead(Collection<String> recipientKeys, String databaseType, String readBy, LocalDateTime now);

    int markReadByDocument(Collection<String> recipientKeys, String documentId, String readBy, LocalDateTime now);

    int hide(UUID id, Collection<String> recipientKeys, LocalDateTime now);

    /**
     * Dọn hộp thư (xoá mềm, transaction riêng): tối đa {@code limit} dòng ĐÃ ĐỌC tạo trước {@code readBefore}
     * hoặc BẤT KỲ dòng nào tạo trước {@code anyBefore}. Trả số dòng đã dọn - gọi lặp tới khi &lt; limit.
     */
    int softDeleteExpired(LocalDateTime readBefore, LocalDateTime anyBefore, int limit);
}
