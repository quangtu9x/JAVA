package com.td.application.documents;

import lombok.Data;

import java.util.UUID;

/**
 * Tham số cho {@link PrepareVbdiDeliveryFilesUseCase} — xem javadoc interface đó.
 */
@Data
public class PrepareVbdiDeliveryFilesRequest {

    /** Id văn bản đi (nguồn) cần copy tệp đính kèm sang vị trí lưu trữ dùng chung cho lượt gửi. */
    private UUID documentId;

    /** Mã định danh đơn vị gửi ({@code unit_publish_identifier}) - dùng làm segment đường dẫn. */
    private String senderIdentifier;
}
