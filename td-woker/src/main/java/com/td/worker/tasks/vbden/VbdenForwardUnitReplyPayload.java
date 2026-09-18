package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Payload của envelope {@code vbden.forward_unit.reply} — publish từ {@link VbdenForwardUnitTaskHandler}. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VbdenForwardUnitReplyPayload {

    private String originDocumentId;
    private String targetIdentifier;
    private String createdDocumentId;
    private String createdTenantId;

    /**
     * Bổ sung 08/09/2026 (yêu cầu người dùng: phản hồi trạng thái "Đã đến" vào quá trình xử lý) —
     * tên hiển thị của đơn vị đích ({@code Organization.name}), để ghi câu "Đã đến đơn vị ..." dễ đọc
     * hơn thay vì chỉ có mã định danh. Có thể null/rỗng (fallback về {@code targetIdentifier}).
     */
    private String targetName;
}
