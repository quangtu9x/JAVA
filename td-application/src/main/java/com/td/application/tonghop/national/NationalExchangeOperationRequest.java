package com.td.application.tonghop.national;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Yêu cầu thao tác quản trị trên 1 gói (docs/22 §4). Người yêu cầu lấy từ token — không nhận từ client. */
@Data
public class NationalExchangeOperationRequest {

    /** {@code RESEND} | {@code RECEIVE_AGAIN} | {@code REPLAY_INTERNAL} | {@code REPROCESS_STATUS}. */
    private String operationType;

    /** Lý do bắt buộc (audit). */
    private String reason;

    /** REPLAY_INTERNAL: tenant nhận cần phát lại (rỗng = mọi đơn vị trong phạm vi). Phải thuộc danh sách To. */
    private List<String> tenantIds = new ArrayList<>();

    /** REPLAY_INTERNAL: giới hạn thêm theo mã đơn vị nhận (tuỳ chọn). */
    private List<String> agencyCodes = new ArrayList<>();
}
