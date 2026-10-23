package com.td.application.tonghop.national;

import java.util.Set;

/**
 * Nhóm trạng thái hiển thị của 1 gói trao đổi qua Trục quốc gia (bảng {@code worker_national_exchange} của TD
 * Gateway). Trạng thái kỹ thuật gốc ({@code status}) vẫn trả kèm để hỗ trợ điều tra; FE chỉ lọc/tô màu theo nhóm.
 *
 * <p>Ánh xạ (khớp {@code GuiNhanQuocGiaSendLogStore}/{@code GuiNhanQuocGiaReceiveLogStore} của tdgateway):</p>
 * <ul>
 *   <li>{@link #DONE}: chiều đi {@code SENT} (Trục đã nhận gói); chiều đến {@code RECEIVED} (mọi đơn vị đã nhận
 *       + đã báo {@code done} cho Trục).</li>
 *   <li>{@link #BUILT_ONLY}: chế độ thử {@code GATEWAY_VPCP_BUILD_ONLY} — đã dựng eDXML nhưng KHÔNG gửi Trục.</li>
 *   <li>{@link #ERROR}: {@code FAILED} hoặc {@code FAILED_RETRYABLE} (gateway còn tự thử lại).</li>
 *   <li>{@link #QUARANTINED}: gói đến bị cách ly (sai cấu trúc/không định tuyến được).</li>
 *   <li>{@link #IN_PROGRESS}: còn lại ({@code PENDING}, {@code DOWNLOADED}, {@code AWAITING_ACK},
 *       {@code DONE_PENDING}...).</li>
 * </ul>
 */
public final class NationalExchangeStates {

    public static final String DONE = "DONE";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String BUILT_ONLY = "BUILT_ONLY";
    public static final String ERROR = "ERROR";
    public static final String QUARANTINED = "QUARANTINED";

    public static final Set<String> ALL = Set.of(DONE, IN_PROGRESS, BUILT_ONLY, ERROR, QUARANTINED);

    public static final String DIRECTION_INBOUND = "INBOUND";
    public static final String DIRECTION_OUTBOUND = "OUTBOUND";
    public static final Set<String> DIRECTIONS = Set.of(DIRECTION_INBOUND, DIRECTION_OUTBOUND);

    public static final Set<String> MESSAGE_TYPES = Set.of("edoc", "status");

    private NationalExchangeStates() {
    }
}
