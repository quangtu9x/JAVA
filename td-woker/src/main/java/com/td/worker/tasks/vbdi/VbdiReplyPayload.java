package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Payload của envelope {@code vbdi.reply} — đơn vị NHẬN → đơn vị GỬI (port
 * {@code ResponseStatus.java}, 6 mã trạng thái 01-06 của Domino). Unit chạy trong tenant
 * của ĐƠN VỊ GỬI (bên nhận phản hồi).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VbdiReplyPayload {

    /** Khoá đối chiếu thẳng 1 dòng vbdi_send_log_documents phía đơn vị gửi. */
    private String sendLogId;

    private String documentId;

    /** 01-06 theo StatusCode.java (Đã đến/Từ chối/Đã tiếp nhận/Phân công/Đang xử lý/Hoàn thành). */
    private String statusCode;

    /** Nhãn hiển thị (status_description). */
    private String statusLabel;

    /** Mô tả lỗi/chi tiết trạng thái (status_details). */
    private String statusDetails;

    /** Đơn vị/cá nhân phản hồi. */
    private String recipientIdentifier;
    private String recipientName;

    /** Tenant đơn vị gửi (trùng tenant của unit). */
    private String senderTenantId;

    /**
     * Bổ sung 07/09/2026 (hoàn thiện broker phản hồi trạng thái "vào sổ"/"phân xử lý" — xem
     * javadoc {@link VbdiReplyTaskHandler}): 2 field bổ sung để ghi được 1 dòng MỚI vào
     * {@code vbdi_send_log_documents} (Nhật ký gửi nhận) phía đơn vị gửi — trước đó reply chỉ ghi
     * vào bảng tự quản {@code worker_vbdi_reply_log} của Worker, KHÔNG BAO GIỜ phản ánh vào bảng
     * nghiệp vụ FE thật đang đọc.
     */
    private String recipientGroup;

    /** Tên hiển thị người/hệ thống thực hiện hành động phía đơn vị nhận (cột {@code user_creator}). */
    private String actorDisplayName;
}