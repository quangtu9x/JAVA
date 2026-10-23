package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Tổng hợp chung" toàn tỉnh. Tên field số liệu đơn vị GIỮ đúng tên của blob Redis Domino cũ
 * ({@code vbden}, {@code vbden_chuaxl}, {@code vbgiay}...) để bảng FE không phải đổi cột.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceStatisticsResult {

    private List<UnitStatistic> units;
    private UnitStatistic totals;
    private List<MonthlyPoint> monthly;
    /** Lần đồng bộ thành công CŨ NHẤT trong các tenant — số liệu chắc chắn mới tới thời điểm này. */
    private OffsetDateTime syncedUntil;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnitStatistic {
        private String identifier;
        private String name;
        private long vbden;
        private long vbden_chuaxl;
        private long vbden_daxl;
        private long vbden_quahan;
        private long vbgiay;
        private long vbden_noibo;
        private long vbden_truc;
        private long vbdi;
        private long vbdi_kyso;
        private long vbdi_noibo;
        private long vbdi_truc;
        private long vbdi_vbgiay;
        private OffsetDateTime lastActivity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPoint {
        /** {@code yyyy-MM}. */
        private String month;
        private long vbden;
        private long vbdi;
    }
}
