package com.td.application.realtime;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Một thông báo realtime gửi qua server socket.io (port {@code TDInit.sendSocketNotSave} Domino).
 *
 * <p>Khoá payload giữ ĐÚNG như Domino vì FE đọc trực tiếp ({@code notificationUtils.ts}:
 * {@code databasetype}/{@code action}/{@code code}/{@code creator}/{@code position}/{@code opinion}/
 * {@code deadline}/{@code message}/{@code docid}). {@code recipients} là username hoặc mã định danh
 * đơn vị (văn thư cơ quan nhận theo room {@code coquan_<mã>}) — chuỗi kiểu Domino {@code ";a;b;"}
 * được tách sẵn ở {@link #to(String...)}.</p>
 */
public final class RealtimeNotification {

    /**
     * Mã định danh đơn vị (vd {@code H05}, {@code H56.07.01}) - người nhận khớp mẫu là HỘP THƯ ĐƠN VỊ (room
     * {@code coquan_<mã>}, văn thư join). Domino ({@code TDInit.isMaTinhUser}) so tiền tố cứng
     * {@code H05;K05;A05} nên bỏ sót mã tỉnh khác.
     */
    private static final Pattern UNIT_IDENTIFIER = Pattern.compile("[A-Z][0-9]{2}(\\.[0-9A-Za-z]+)*");

    private final Set<String> recipients = new LinkedHashSet<>();
    private final Map<String, UUID> notificationIds = new HashMap<>();
    private String createdOn;
    private final String databaseType;
    private final String action;
    private final String docId;
    private final String code;
    private String creator;
    private String position;
    private String opinion;
    private String deadline;
    private String message;

    private RealtimeNotification(String databaseType, String action, String docId, String code) {
        this.databaseType = databaseType;
        this.action = action;
        this.docId = docId;
        this.code = code;
    }

    public static RealtimeNotification of(String databaseType, String action, UUID docId, String code) {
        return new RealtimeNotification(databaseType, action, docId == null ? null : docId.toString(), code);
    }

    public static RealtimeNotification of(String databaseType, String action, String docId, String code) {
        return new RealtimeNotification(databaseType, action, docId, code);
    }

    /** Thêm người nhận; mỗi chuỗi có thể chứa nhiều giá trị ngăn bởi {@code ;} hoặc {@code ,}. */
    public RealtimeNotification to(String... userLists) {
        if (userLists != null) {
            for (String list : userLists) {
                addSplit(list);
            }
        }
        return this;
    }

    public RealtimeNotification to(Collection<String> users) {
        if (users != null) {
            users.forEach(this::addSplit);
        }
        return this;
    }

    /** Như {@link #to(String...)} nhưng nhận giá trị field động thô: chuỗi {@code ;}, List, hoặc null. */
    public RealtimeNotification toValues(Object... values) {
        if (values != null) {
            for (Object value : values) {
                if (value instanceof Collection<?> collection) {
                    collection.forEach(item -> addSplit(item == null ? null : String.valueOf(item)));
                } else if (value != null) {
                    addSplit(String.valueOf(value));
                }
            }
        }
        return this;
    }

    /** Bỏ 1 người khỏi danh sách nhận (vd Domino {@code replace(username, "")} để không tự báo chính mình). */
    public RealtimeNotification exclude(String user) {
        if (user != null) {
            recipients.removeIf(r -> r.equalsIgnoreCase(user.trim()));
        }
        return this;
    }

    public RealtimeNotification creator(String creator) {
        this.creator = creator;
        return this;
    }

    public RealtimeNotification position(String position) {
        this.position = position;
        return this;
    }

    public RealtimeNotification opinion(String opinion) {
        this.opinion = opinion;
        return this;
    }

    public RealtimeNotification deadline(String deadline) {
        this.deadline = deadline;
        return this;
    }

    public RealtimeNotification message(String message) {
        this.message = message;
        return this;
    }

    private void addSplit(String list) {
        if (list == null) {
            return;
        }
        for (String part : list.split("[;,]")) {
            String user = part.trim();
            if (!user.isEmpty()) {
                recipients.add(user);
            }
        }
    }

    public Set<String> getRecipients() {
        return Collections.unmodifiableSet(recipients);
    }

    public static boolean isUnitIdentifier(String recipient) {
        return recipient != null && UNIT_IDENTIFIER.matcher(recipient).matches();
    }

    /** Id dòng {@code user_notifications} của người nhận (null nếu lưu thất bại) - gửi kèm socket để FE đánh dấu đã đọc. */
    public UUID getNotificationId(String recipient) {
        return notificationIds.get(recipient);
    }

    void putNotificationId(String recipient, UUID id) {
        notificationIds.put(recipient, id);
    }

    public String getCreatedOn() {
        return createdOn;
    }

    void createdOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getAction() {
        return action;
    }

    public String getDocId() {
        return docId;
    }

    public String getCode() {
        return code;
    }

    public String getCreator() {
        return creator;
    }

    public String getPosition() {
        return position;
    }

    public String getOpinion() {
        return opinion;
    }

    public String getDeadline() {
        return deadline;
    }

    public String getMessage() {
        return message;
    }
}
