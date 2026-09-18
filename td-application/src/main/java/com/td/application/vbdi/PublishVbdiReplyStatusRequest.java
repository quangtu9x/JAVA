package com.td.application.vbdi;

import lombok.Data;

import java.util.Map;

/**
 * Tham số cho {@link PublishVbdiReplyStatusUseCase} — xem javadoc lớp đó.
 */
@Data
public class PublishVbdiReplyStatusRequest {

    /**
     * {@code dynamic_fields} ĐÃ PARSE SẴN của văn bản đến (caller thường đã có sẵn map này trong
     * cùng giao dịch — vd {@code ChuyenXuLyVbdenUseCase}/{@code LuuVanBanDenUseCase} — nên KHÔNG
     * bắt đọc lại DB ở đây).
     */
    private Map<String, Object> dynamicFields;

    /** 01-06 theo {@code StatusCode.java} (Domino). */
    private String statusCode;

    private String statusLabel;

    private String statusDetails;

    /** Tên hiển thị người thực hiện hành động (văn thư/lãnh đạo) — rỗng thì Worker tự mặc định. */
    private String actorDisplayName;
}
