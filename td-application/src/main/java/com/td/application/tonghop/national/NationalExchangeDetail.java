package com.td.application.tonghop.national;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chi tiết 1 gói trao đổi: thông tin gói + từng nơi nhận + dòng thời gian (event) + từng lần thao tác Trục
 * (attempt) + yêu cầu quản trị đã tạo. Chỉ đọc — xem tdgateway/docs/22 §2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationalExchangeDetail {

    private NationalExchangeItem exchange;
    private String contentSha256;
    private String transportStatus;
    private String recoveryStatus;
    private Boolean errorRetryable;
    private String successDetail;
    private boolean transportAckRequired;
    private OffsetDateTime occurredAt;
    /** {@code dynamic_fields} (đã bỏ khoá kỹ thuật nhạy cảm) — field mở rộng theo quy ước JSONB của JAVA. */
    private Map<String, Object> dynamicFields;

    /** Gói TRẠNG THÁI: văn bản được phản hồi (ResponseFor) + mã trạng thái (gateway V17). */
    private StatusReference statusReference;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusReference {
        private String documentId;
        private String code;
        private String statusCode;
        private String statusLabel;
        private String description;
        private String fromOrganId;
        private String fromOrganName;
        /** Gói văn bản đi được phản hồi ({@code OUT:...}) nếu tìm thấy — liên kết sang chi tiết gói đó. */
        private String relatedExchangeId;
    }

    @Builder.Default
    private List<Recipient> recipients = new ArrayList<>();
    @Builder.Default
    private List<Event> events = new ArrayList<>();
    @Builder.Default
    private List<Attempt> attempts = new ArrayList<>();
    @Builder.Default
    private List<Operation> operations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recipient {
        /** Chiều đến: tenant nhận trong tỉnh; chiều đi: rỗng (cơ quan nhận ngoài tỉnh). */
        private String tenantId;
        private String tenantName;
        private String agencyCode;
        private String agencyName;
        private String organizationIdentifier;
        private String deliveryStatus;
        /** Mã trạng thái eDXML cuối cùng nơi nhận phản hồi (01 Đã đến ... 06 Hoàn thành, 15 Đã lấy lại). */
        private String lastStatusCode;
        private String lastStatusLabel;
        /** Văn bản đến đã tạo trong tenant nhận (chiều đến). */
        private String createdDocumentId;
        private String createdDocumentCode;
        private String createdDocumentTitle;
        private OffsetDateTime acknowledgedAt;
        private String errorDetail;
        private OffsetDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private long eventId;
        private String eventType;
        private String statusBefore;
        private String statusAfter;
        private String tenantId;
        private String agencyCode;
        private String actorType;
        private String actorName;
        private String details;
        private OffsetDateTime occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attempt {
        private long attemptId;
        private String operation;
        private String outcome;
        private Integer attemptNo;
        private String requestedTransportStatus;
        private String resultingTransportStatus;
        private String errorCode;
        private String errorCategory;
        private Boolean errorRetryable;
        private String errorDetail;
        private OffsetDateTime startedAt;
        private OffsetDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Operation {
        private String requestId;
        private String exchangeId;
        private String operationType;
        private String status;
        /** {@code result_json.message} do bộ thực thi gateway ghi. */
        private String resultMessage;
        /** RESEND: gói mới tạo ({@code result_json.newExchangeId}). */
        private String newExchangeId;
        private String reason;
        private String requestedBy;
        private String requestedByName;
        private String errorDetail;
        private OffsetDateTime requestedAt;
        private OffsetDateTime completedAt;
    }
}
