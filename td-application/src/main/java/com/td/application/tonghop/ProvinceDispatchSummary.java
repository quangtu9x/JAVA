package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Thẻ KPI + 2 bảng xếp hạng của màn giám sát: đơn vị PHÁT HÀNH còn nhiều nơi nhận chưa phản hồi, và
 * đơn vị NHẬN chậm tiếp nhận (bản Domino chỉ có chiều thứ nhất).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDispatchSummary {

    private long documents;
    private long documentsPending;
    private long recipients;
    private long waiting;
    private long arrived;
    private long processing;
    private long done;
    private long refused;
    private long revoked;
    private long lateArrival;
    private long lateAccept;
    /** Thời gian trung bình từ lúc gửi tới lúc "Đã tiếp nhận" (giờ), null nếu chưa có mẫu. */
    private Double avgAcceptHours;
    private List<UnitRank> topSenders;
    private List<UnitRank> topSlowReceivers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnitRank {
        private String identifier;
        private String name;
        private long total;
        private long pending;
        private long late;
        private Double avgAcceptHours;
    }
}
