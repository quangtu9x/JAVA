package com.td.application.tonghop;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ lọc "Giám sát gửi văn bản đi tập trung" (route FE {@code /tonghop/theodoivanbandi}).
 */
@Data
public class ProvinceDispatchSearchRequest {

    private String keyword;

    /** Theo ngày ban hành. */
    private String fromDate;

    private String toDate;

    /** Đơn vị phát hành (gồm đơn vị con). */
    private List<String> senderIdentifiers = new ArrayList<>();

    /** Có ít nhất 1 lượt gửi tới đơn vị này (gồm đơn vị con). */
    private List<String> recipientIdentifiers = new ArrayList<>();

    /** {@code internal_unit} | {@code contact_unit} | {@code external_unit}. */
    private List<String> recipientGroups = new ArrayList<>();

    /**
     * {@code CHUA_HOAN_TAT} (còn nơi nhận chưa đến/chưa tiếp nhận), {@code HOAN_TAT},
     * {@code CO_TU_CHOI}, {@code CHAM_DEN}, {@code CHAM_TIEP_NHAN}. Rỗng = tất cả.
     */
    private String situation;

    /** Quá số phút này mà chưa "Đã đến" = chậm đến (lỗi truyền nhận). */
    private Integer lateArrivalMinutes;

    /** Quá số giờ này mà chưa "Đã tiếp nhận" (vào sổ) = chậm tiếp nhận. */
    private Integer lateAcceptHours;

    private int pageNumber = 1;

    private int pageSize = 20;

    private boolean export;

    /**
     * Phạm vi tenant do SERVER đặt ({@link ProvinceScopePolicy}) — client không gửi được ({@code @JsonIgnore}) và use
     * case luôn ghi đè. Null = toàn tỉnh. Có trong {@code toString} nên khoá cache theo phạm vi.
     */
    @JsonIgnore
    private String scopeTenantId;
}
