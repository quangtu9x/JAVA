package com.td.worker.core.task;

import java.util.Map;

/**
 * Kết quả xử lý 1 unit. Failure -> Worker tự quyết định RETRY (backoff) hay FAILED
 * (khi attempts >= maxAttempts).
 */
public final class UnitResult {

    private final boolean success;
    private final boolean waitingDependency;
    private final String error;
    private final Map<String, Object> detail;

    private UnitResult(boolean success, boolean waitingDependency, String error, Map<String, Object> detail) {
        this.success = success;
        this.waitingDependency = waitingDependency;
        this.error = error;
        this.detail = detail;
    }

    public static UnitResult success() {
        return new UnitResult(true, false, null, null);
    }

    /**
     * @param detail payload nghiệp vụ tự do (vd transId/verificationCode của simpki.sign) — chảy
     *               tới {@link com.td.worker.core.flow.UnitOutcome} rồi ra transport (HTTP debug
     *               endpoint), KHÔNG bắt buộc TaskHandler nào phải dùng (mặc định null, các
     *               handler cũ không đổi hành vi).
     */
    public static UnitResult success(Map<String, Object> detail) {
        return new UnitResult(true, false, null, detail);
    }

    public static UnitResult failure(String error) {
        return new UnitResult(false, false, error == null ? "unit failed" : error, null);
    }

    /** Như {@link #failure(String)}, kèm payload chi tiết lỗi (vd errorCode/errorDesc gốc từ MSSP). */
    public static UnitResult failure(String error, Map<String, Object> detail) {
        return new UnitResult(false, false, error == null ? "unit failed" : error, detail);
    }

    /** Dependency chưa sẵn sàng; Worker giữ unit để tự thử lại sau deploy/khôi phục. */
    public static UnitResult waitingDependency(String error) {
        return new UnitResult(false, true, error == null ? "dependency unavailable" : error, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String error() {
        return error;
    }

    public boolean isWaitingDependency() { return waitingDependency; }

    public Map<String, Object> detail() { return detail; }
}
