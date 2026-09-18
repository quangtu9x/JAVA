package com.td.application.vbdi;

import lombok.Builder;
import lombok.Data;

/**
 * Payload envelope {@code vbdi.reply} — đơn vị NHẬN → đơn vị GỬI, publish từ
 * {@link PublishVbdiReplyStatusUseCase} khi văn thư "vào sổ"/"phân xử lý" 1 văn bản đến được tạo
 * qua giao (có {@code send_log_id} trong {@code dynamic_fields} — xem
 * {@code VbdenIncomingDocumentWriter}, td-woker). PHẢI khớp field-by-field
 * {@code com.td.worker.tasks.vbdi.VbdiReplyPayload} (đọc trực tiếp mã nguồn, KHÔNG suy đoán) —
 * KHÔNG share class/jar với {@code td-woker} (2 module độc lập, chỉ khớp NHAU về hình dạng JSON).
 */
@Data
@Builder
public class VbdiReplyMessagePayload {

    /** = id dòng {@code vbdi_send_log_documents} gốc phía đơn vị gửi (khoá đối chiếu). */
    private String sendLogId;

    private String documentId;

    /** 01-06 theo {@code StatusCode.java} (Domino) — xem {@code VbdiTaskConstants} (td-woker). */
    private String statusCode;

    private String statusLabel;

    private String statusDetails;

    private String recipientIdentifier;
    private String recipientName;
    private String recipientGroup;

    /** Tên hiển thị người/hệ thống thực hiện hành động phía đơn vị nhận. */
    private String actorDisplayName;
}
