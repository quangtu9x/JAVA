package com.td.worker.core.flow;

import com.td.worker.core.checkpoint.UnitStatus;

import java.util.List;
import java.util.Map;

/**
 * Kết quả chạy 1 message qua MessageFlowRunner — gồm số liệu thống kê và quyết định
 * {@code canAck} theo ACK policy (docs/promt.txt: "Chỉ ACK message theo chính sách hoàn tất
 * được thiết kế; không ACK đơn giản chỉ vì một phần unit đã xử lý thành công").
 *
 * @param unitsTotal    tổng unit
 * @param statusCounts  trạng thái units của message sau khi chờ xử lý xong lượt hiện tại
 * @param canAck        được phép ACK theo policy
 * @param timedOutUnits số unit đã timeout trong lượt này (đã fail-in-place để retry)
 * @param skippedUnits  số unit không claim được (đang do worker khác xử lý/làm xong)
 * @param unitOutcomes  chi tiết từng unit đã CHỜ XONG lượt này (xem {@link UnitOutcome}) — KHÔNG
 *                      gồm unit backpressure (chưa dispatch) hay unit worker khác đang giữ.
 */
public record FlowResult(
        int unitsTotal,
        Map<UnitStatus, Long> statusCounts,
        boolean canAck,
        int timedOutUnits,
        int skippedUnits,
        List<UnitOutcome> unitOutcomes) {

    public static FlowResult completed(int unitsTotal, Map<UnitStatus, Long> statusCounts, boolean canAck) {
        return new FlowResult(unitsTotal, statusCounts, canAck, 0, 0, List.of());
    }
}