package com.td.application.vbdi;

import com.td.application.documents.VbdiDeliveryFileRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 — tham số ghi 1 dòng "Nhật ký gửi nhận" (bảng vật lý ĐỘC LẬP
 * {@code vbdi_send_log_documents}, xem {@link com.td.domain.vbdi.VbdiSendLogDocument}). Dùng bởi
 * {@link AddVbdiSendLogUseCase} (writer cấp thấp, 1 dòng/1 nơi nhận) — {@link SendVbdiDocumentUseCase}
 * (business action "gửi văn bản", nhiều nơi nhận) gọi lặp lại DTO này cho từng nơi nhận, đúng khuôn
 * {@code AddDraftProcessDocumentUseCase}/{@code XuLyDuThaoUseCase} (writer chung, caller nghiệp vụ
 * lặp gọi).
 */
@Data
public class AddVbdiSendLogRequest {

    @NotNull(message = "parent_id khong duoc de trong")
    private UUID parentId;

    @NotBlank(message = "recipient_group khong duoc de trong")
    private String recipientGroup;

    private String sendType;

    private String recipientIdentifier;

    private String recipientName;

    private String mailin;

    private String status;

    private String content;

    /**
     * Bổ sung 07/09/2026 — tệp đính kèm ĐÃ ĐƯỢC COPY SẴN (1 lần cho cả lượt gửi, KHÔNG copy lại ở
     * đây) bởi {@code SendVbdiDocumentUseCase} qua {@code PrepareVbdiDeliveryFilesUseCase} — writer
     * này (1 dòng/1 nơi nhận) chỉ FORWARD nguyên danh sách này vào {@code documentSnapshot}, không tự
     * tính toán/copy gì thêm (xem javadoc {@link AddVbdiSendLogUseCase#publishDeliveryMessage}).
     */
    private List<VbdiDeliveryFileRef> files;
}
