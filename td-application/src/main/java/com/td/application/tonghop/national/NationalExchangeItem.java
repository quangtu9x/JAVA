package com.td.application.tonghop.national;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 1 dòng danh sách gói trao đổi qua Trục quốc gia (đọc {@code vw_worker_national_exchange_search} của TD Gateway).
 * Không trả đường dẫn tệp/metadata kỹ thuật — tdgateway/docs/23 "Contract triển khai API/UI".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationalExchangeItem {

    private String exchangeId;
    private String direction;
    private String messageType;
    private String operationType;
    /** Trạng thái kỹ thuật gốc của gateway. */
    private String status;
    /** Nhóm trạng thái hiển thị — {@link NationalExchangeStates}. */
    private String state;
    private String tenantId;
    private String tenantName;
    private String businessDocumentId;
    private String edxmlDocumentId;
    private String transportDocumentId;
    private String centerDocumentId;
    private String senderAgencyCode;
    private String senderAgencyName;
    private String recipientCodes;
    /** Tên nơi nhận theo cùng thứ tự {@link #recipientCodes} (danh bạ cơ quan Trục; thiếu tên thì giữ mã), ";"-nối. */
    private String recipientNames;
    private int recipientCount;
    private int acknowledgedCount;
    private int attemptCount;
    private boolean manualReplay;
    private boolean hasError;
    private String errorCode;
    private String errorCategory;
    private String errorDetail;
    private String lastStage;
    private String lastEventType;
    private Long contentLength;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;

    /** Số/ký hiệu + trích yếu văn bản đi trong tenant gửi (tra kho tổng hợp province_documents), có thể null. */
    private String documentCode;
    private String documentTitle;
}
