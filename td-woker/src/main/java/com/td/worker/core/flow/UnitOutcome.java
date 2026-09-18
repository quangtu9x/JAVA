package com.td.worker.core.flow;

import java.util.Map;

/**
 * Kết quả CHI TIẾT của 1 unit sau khi flow chờ xong lượt dispatch hiện tại — bổ sung BÊN CẠNH
 * {@link FlowResult#statusCounts()} (vốn chỉ đếm tổng hợp theo trạng thái, không biết unit nào
 * trả về gì). Cho phép transport (vd HTTP debug endpoint,
 * {@code com.td.worker.transport.http.DebugTriggerController}) trả nguyên payload nghiệp vụ do
 * TaskHandler sinh ra (vd transId/verificationCode/errorCode của {@code simpki.sign}) mà KHÔNG
 * cần Worker Core biết gì về module cụ thể — {@code detail} là {@code Map} tự do do
 * {@link com.td.worker.core.task.UnitResult#detail()} truyền lên, TaskHandler nào không set thì
 * {@code detail} là {@code null} (không đổi hành vi handler cũ).
 *
 * @param unitId  id unit (khớp {@code MessageUnit#unitId()})
 * @param success kết quả cuối cùng lượt này (KHÔNG tính waitingDependency là success)
 * @param error   thông điệp lỗi ngắn (null nếu success) — giống {@code UnitResult#error()}
 * @param detail  payload tự do TaskHandler trả về, hoặc null nếu unit timeout/lỗi hạ tầng trước
 *                khi handler kịp chạy xong
 */
public record UnitOutcome(String unitId, boolean success, String error, Map<String, Object> detail) {
}
