package com.td.application.tonghop.national;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Thẻ số liệu màn Trục quốc gia — cùng bộ lọc danh sách trừ nhóm trạng thái. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationalExchangeSummary {

    private long total;
    private long outbound;
    private long inbound;
    private long done;
    private long inProgress;
    private long builtOnly;
    private long error;
    private long quarantined;
    /** Gói đến còn đơn vị nội tỉnh chưa nhận (recipient chưa RECEIVED). */
    private long inboundAwaitingRecipients;
    /** Gói cần hỗ trợ (lỗi, cách ly, treo/ACK quá {@code staleHours}) — xem {@code NationalExchangeSearchRequest#needsSupport}. */
    private long needsSupport;
    private int staleHours;
    private long totalBytes;

    /** Gói theo ngày (theo {@code updated_at}) — để vẽ xu hướng. */
    @Builder.Default
    private List<Day> days = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day {
        private LocalDate day;
        private long outbound;
        private long inbound;
        private long failed;
    }
}
