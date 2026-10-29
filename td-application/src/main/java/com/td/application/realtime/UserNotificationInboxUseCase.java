package com.td.application.realtime;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.notifications.UserNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hộp thư thông báo (chuông) của NGƯỜI ĐANG ĐĂNG NHẬP — không nhận username từ client. Phạm vi = username
 * + mã định danh đơn vị nếu là văn thư (thông báo gửi room {@code coquan_<mã>}, cùng điều kiện FE join room).
 *
 * <p>Item trả về dùng ĐÚNG khoá payload socket ({@code databasetype}, {@code action}, {@code docid},
 * {@code code}, {@code creator}...) + {@code notification_id}/{@code created_on}/{@code read} để FE dùng 1
 * bộ hiển thị cho cả thông báo realtime lẫn thông báo kéo lại.</p>
 */
@Service
@RequiredArgsConstructor
public class UserNotificationInboxUseCase {

    static final int MAX_PAGE_SIZE = 200;
    private static final int SUMMARY_LATEST = 5;
    // Văn thư (cơ quan/đơn vị/phòng) VB đến + VB đi - người nhận thông báo đơn vị (chuyển phát hành,
    // văn bản tới đơn vị chưa có người đại diện...).
    private static final Set<String> UNIT_INBOX_ROLES = Set.of(
        "QCAPNHATVBDEN", "QCAPNHATVBDENDV", "QCAPNHATVBDENPB",
        "QCAPNHATVBDI", "QCAPNHATVBDIDV", "QCAPNHATVBDIPB");

    private final UserNotificationRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    // Mã đơn vị của văn thư (dựng hồ sơ tổ chức = duyệt cây) - cache 5 phút/username: FE gọi hộp thư khá thường
    // (kết nối lại, đánh dấu đọc...).
    private final java.util.Map<String, UnitKey> unitKeyCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record UnitKey(String identifier, long expiresAt) {
    }

    public Result<Map<String, Object>> list(int page, int size, boolean unreadOnly, String databaseType) {
        Set<String> keys = recipientKeys();
        if (keys.isEmpty()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        int safeSize = Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE));
        Page<UserNotification> result = repository.findForRecipients(keys, unreadOnly, blankToNull(databaseType),
            PageRequest.of(Math.max(0, page), safeSize));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent().stream().map(this::toItem).toList());
        body.put("total", result.getTotalElements());
        body.put("unreadCount", repository.countUnread(keys));
        return Result.success(body);
    }

    /** Số chưa đọc + nhóm theo phân hệ/hành động + vài thông báo mới nhất - dùng cho toast tổng hợp khi đăng nhập. */
    public Result<Map<String, Object>> summary() {
        Set<String> keys = recipientKeys();
        if (keys.isEmpty()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        long unread = 0;
        for (Object[] row : repository.countUnreadGrouped(keys)) {
            long count = row[2] == null ? 0 : ((Number) row[2]).longValue();
            unread += count;
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("databasetype", row[0]);
            group.put("action", row[1]);
            group.put("count", count);
            group.put("latest_on", row[3] == null ? null : row[3].toString());
            groups.add(group);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unreadCount", unread);
        body.put("groups", groups);
        body.put("latest", repository.findLatestUnread(keys, SUMMARY_LATEST).stream().map(this::toItem).toList());
        return Result.success(body);
    }

    public Result<Integer> markRead(UUID id) {
        Set<String> keys = recipientKeys();
        if (keys.isEmpty() || id == null) {
            return Result.failure("Thiếu thông tin thông báo");
        }
        return Result.success(repository.markRead(id, keys, currentUsername(), LocalDateTime.now()));
    }

    public Result<Integer> markAllRead(String databaseType) {
        Set<String> keys = recipientKeys();
        if (keys.isEmpty()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        return Result.success(repository.markAllRead(keys, blankToNull(databaseType), currentUsername(),
            LocalDateTime.now()));
    }

    /** Mở văn bản (từ thông báo hoặc từ danh sách) -> mọi thông báo chưa đọc của văn bản đó coi như đã đọc. */
    public Result<Integer> markReadByDocument(String documentId) {
        Set<String> keys = recipientKeys();
        if (keys.isEmpty() || documentId == null || documentId.isBlank()) {
            return Result.failure("Thiếu văn bản");
        }
        return Result.success(repository.markReadByDocument(keys, documentId.trim(), currentUsername(),
            LocalDateTime.now()));
    }

    public Result<Integer> hide(UUID id) {
        Set<String> keys = recipientKeys();
        if (keys.isEmpty() || id == null) {
            return Result.failure("Thiếu thông tin thông báo");
        }
        return Result.success(repository.hide(id, keys, LocalDateTime.now()));
    }

    Set<String> recipientKeys() {
        String username = currentUsername();
        Set<String> keys = new LinkedHashSet<>();
        if (username.isBlank()) {
            return keys;
        }
        keys.add(username.toLowerCase(Locale.ROOT));
        boolean unitClerk = currentUserProvider.currentRoles().stream().anyMatch(UNIT_INBOX_ROLES::contains);
        if (unitClerk) {
            String identifier = unitIdentifier(username);
            if (!identifier.isBlank()) {
                keys.add(identifier.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    private String unitIdentifier(String username) {
        long now = System.currentTimeMillis();
        UnitKey cached = unitKeyCache.get(username);
        if (cached != null && cached.expiresAt() > now) {
            return cached.identifier();
        }
        String identifier = "";
        try {
            identifier = resolveIdentifierByLevelUseCase.resolveForCurrentUser().map(String::trim).orElse("");
        } catch (RuntimeException ignored) {
            // Không xác định được đơn vị -> chỉ hộp thư cá nhân.
        }
        if (unitKeyCache.size() > 10_000) {
            unitKeyCache.clear();
        }
        unitKeyCache.put(username, new UnitKey(identifier, now + 5 * 60 * 1000L));
        return identifier;
    }

    private Map<String, Object> toItem(UserNotification n) {
        Map<String, Object> item = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(n.getDynamicFieldsJson()));
        item.put("notification_id", n.getId().toString());
        item.put("databasetype", n.getDatabaseType());
        item.put("action", n.getAction());
        item.put("docid", n.getDocumentId());
        item.put("recipient", n.getRecipient());
        item.put("recipient_type", n.getRecipientType());
        item.put("created_on", n.getCreatedOn() == null ? null : n.getCreatedOn().toString());
        item.put("read", n.isRead());
        item.put("read_on", n.getReadOn() == null ? null : n.getReadOn().toString());
        item.put("read_by", n.getReadBy());
        return item;
    }

    private String currentUsername() {
        return currentUserProvider.currentUsername().orElse("").trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
