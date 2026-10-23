package com.td.infrastructure.tonghop;

import com.td.application.tonghop.ProvinceDocumentStates;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dựng trạng thái HIỆN TẠI của từng nơi nhận từ nhật ký gửi nhận append-only
 * ({@code vbdi_send_log_documents}): bảng gốc chỉ có chuỗi sự kiện (gửi, gửi lại, phản hồi "Đã đến"/"Đã tiếp
 * nhận"/..., lấy lại) — Domino cũ cũng không có hàng trạng thái theo nơi nhận, phải lọc view RichText.
 *
 * <p>Quy tắc: sự kiện họ GỬI đưa nơi nhận về {@code SENT} (gửi lại = chu kỳ mới); phản hồi chỉ NÂNG trạng thái
 * (phản hồi đến lệch thứ tự không làm lùi); từ chối/trả lại và lấy lại ghi đè. Nhóm {@code coordinator} (cá
 * nhân xem để biết trong đơn vị) không thuộc phạm vi giám sát liên đơn vị nên bỏ qua.</p>
 */
final class ProvinceDispatchAggregator {

    static final Set<String> SEND_TYPES = Set.of("senddocument", "resenddocument", "guibosung", "guilaitoanbo");
    static final Set<String> REVOKE_TYPES = Set.of("thuhoivanban", "laylaivanban", "thuhoitoanbo");
    static final String REPLY_TYPE = "phan_hoi_trang_thai";
    static final Set<String> MONITORED_GROUPS = Set.of("internal_unit", "contact_unit", "external_unit");

    private static final Map<String, Integer> RANK = Map.of(
        ProvinceDocumentStates.DISPATCH_SENT, 10,
        ProvinceDocumentStates.DISPATCH_ARRIVED, 20,
        ProvinceDocumentStates.DISPATCH_ACCEPTED, 30,
        ProvinceDocumentStates.DISPATCH_ASSIGNED, 40,
        ProvinceDocumentStates.DISPATCH_PROCESSING, 50,
        ProvinceDocumentStates.DISPATCH_DONE, 60);

    static final Map<String, String> LABELS = Map.of(
        ProvinceDocumentStates.DISPATCH_SENT, "Đã gửi - chưa đến",
        ProvinceDocumentStates.DISPATCH_ARRIVED, "Đã đến - chưa tiếp nhận",
        ProvinceDocumentStates.DISPATCH_ACCEPTED, "Đã tiếp nhận",
        ProvinceDocumentStates.DISPATCH_ASSIGNED, "Phân công xử lý",
        ProvinceDocumentStates.DISPATCH_PROCESSING, "Đang xử lý",
        ProvinceDocumentStates.DISPATCH_DONE, "Hoàn thành",
        ProvinceDocumentStates.DISPATCH_REFUSED, "Từ chối/Trả lại",
        ProvinceDocumentStates.DISPATCH_REVOKED, "Đã lấy lại");

    private ProvinceDispatchAggregator() {
    }

    @Data
    static class LogEntry {
        private UUID id;
        private UUID vbdiId;
        private String sendType;
        private String recipientGroup;
        private String recipientIdentifier;
        private String recipientName;
        private String status;
        private String content;
        private String userCreator;
        private OffsetDateTime createdOn;
    }

    @Data
    static class Dispatch {
        private UUID vbdiId;
        private String recipientKey;
        private String recipientGroup;
        private String recipientIdentifier;
        private String recipientName;
        private String state;
        private int sendCount;
        private OffsetDateTime firstSentOn;
        private OffsetDateTime lastSentOn;
        private OffsetDateTime arrivedOn;
        private OffsetDateTime acceptedOn;
        private OffsetDateTime completedOn;
        private OffsetDateTime lastEventOn;

        String stateLabel() {
            return LABELS.getOrDefault(state, state);
        }
    }

    static String recipientKey(String group, String identifier, String name) {
        String id = identifier != null && !identifier.isBlank() ? identifier.trim()
            : name == null ? "" : ProvinceText.normalize(name);
        return ProvinceText.truncate(group + "|" + id, 600);
    }

    static boolean isMonitored(LogEntry e) {
        return e.getRecipientGroup() != null && MONITORED_GROUPS.contains(e.getRecipientGroup());
    }

    /** Toàn bộ sự kiện của CÁC văn bản cần tính (mọi thứ tự) → danh sách nơi nhận. */
    static List<Dispatch> aggregate(List<LogEntry> entries) {
        List<LogEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LogEntry::getCreatedOn, Comparator.nullsFirst(Comparator.naturalOrder())));
        Map<String, Dispatch> byKey = new LinkedHashMap<>();
        for (LogEntry e : sorted) {
            if (!isMonitored(e)) {
                continue;
            }
            String key = e.getVbdiId() + "#" + recipientKey(e.getRecipientGroup(), e.getRecipientIdentifier(), e.getRecipientName());
            Dispatch d = byKey.computeIfAbsent(key, k -> newDispatch(e));
            if (d.getRecipientName() == null && e.getRecipientName() != null) {
                d.setRecipientName(e.getRecipientName());
            }
            apply(d, e);
        }
        // Chỉ có phản hồi mà không thấy sự kiện gửi (dữ liệu cũ) vẫn giữ — state đã được nâng theo phản hồi.
        return byKey.values().stream().filter(d -> d.getState() != null).toList();
    }

    private static Dispatch newDispatch(LogEntry e) {
        Dispatch d = new Dispatch();
        d.setVbdiId(e.getVbdiId());
        d.setRecipientGroup(e.getRecipientGroup());
        d.setRecipientIdentifier(e.getRecipientIdentifier() == null || e.getRecipientIdentifier().isBlank()
            ? null : e.getRecipientIdentifier().trim());
        d.setRecipientName(e.getRecipientName());
        d.setRecipientKey(recipientKey(e.getRecipientGroup(), e.getRecipientIdentifier(), e.getRecipientName()));
        return d;
    }

    private static void apply(Dispatch d, LogEntry e) {
        OffsetDateTime at = e.getCreatedOn();
        String type = e.getSendType() == null ? "" : e.getSendType();
        if (at != null && (d.getLastEventOn() == null || at.isAfter(d.getLastEventOn()))) {
            d.setLastEventOn(at);
        }
        if (SEND_TYPES.contains(type)) {
            d.setState(ProvinceDocumentStates.DISPATCH_SENT);
            d.setSendCount(d.getSendCount() + 1);
            if (d.getFirstSentOn() == null) {
                d.setFirstSentOn(at);
            }
            d.setLastSentOn(at);
            // Chu kỳ gửi mới: mốc phản hồi tính lại từ lần gửi này.
            d.setArrivedOn(null);
            d.setAcceptedOn(null);
            d.setCompletedOn(null);
            return;
        }
        if (REVOKE_TYPES.contains(type)) {
            d.setState(ProvinceDocumentStates.DISPATCH_REVOKED);
            return;
        }
        if (!REPLY_TYPE.equals(type)) {
            return;
        }
        String reply = replyState(e.getStatus());
        if (reply == null) {
            return;
        }
        if (ProvinceDocumentStates.DISPATCH_REFUSED.equals(reply) || ProvinceDocumentStates.DISPATCH_REVOKED.equals(reply)) {
            d.setState(reply);
            return;
        }
        if (ProvinceDocumentStates.DISPATCH_REVOKED.equals(d.getState())) {
            return;
        }
        int current = d.getState() == null ? 0 : RANK.getOrDefault(d.getState(), 0);
        if (RANK.get(reply) > current) {
            d.setState(reply);
        }
        if (d.getArrivedOn() == null) {
            d.setArrivedOn(at);
        }
        if (RANK.get(reply) >= RANK.get(ProvinceDocumentStates.DISPATCH_ACCEPTED) && d.getAcceptedOn() == null) {
            d.setAcceptedOn(at);
        }
        if (ProvinceDocumentStates.DISPATCH_DONE.equals(reply)) {
            d.setCompletedOn(at);
        }
    }

    /** Nhãn phản hồi (VbdiTaskConstants.LABEL_*) → trạng thái. */
    static String replyState(String label) {
        String s = ProvinceText.normalize(label);
        if (s.isEmpty()) {
            return null;
        }
        if (s.contains("tu choi") || s.contains("tra lai")) {
            return ProvinceDocumentStates.DISPATCH_REFUSED;
        }
        if (s.contains("lay lai") || s.contains("thu hoi")) {
            return ProvinceDocumentStates.DISPATCH_REVOKED;
        }
        if (s.contains("hoan thanh")) {
            return ProvinceDocumentStates.DISPATCH_DONE;
        }
        if (s.contains("dang xu ly")) {
            return ProvinceDocumentStates.DISPATCH_PROCESSING;
        }
        if (s.contains("phan cong")) {
            return ProvinceDocumentStates.DISPATCH_ASSIGNED;
        }
        if (s.contains("tiep nhan")) {
            return ProvinceDocumentStates.DISPATCH_ACCEPTED;
        }
        if (s.contains("da den")) {
            return ProvinceDocumentStates.DISPATCH_ARRIVED;
        }
        // Nơi nhận ngoài tỉnh (TD Gateway báo qua vbdi.reply): Trục quốc gia đã nhận gói = mốc "đến" quan sát được
        // cho tới khi có gói trạng thái từ đơn vị nhận. "Gửi lỗi" giữ nguyên trạng thái chờ → hiện ở cảnh báo chậm đến.
        if (s.contains("gui len truc")) {
            return ProvinceDocumentStates.DISPATCH_ARRIVED;
        }
        return null;
    }
}
