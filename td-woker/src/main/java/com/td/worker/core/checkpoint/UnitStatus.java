package com.td.worker.core.checkpoint;

/** Trạng thái persistent của 1 unit (docs/promt.txt mục "Message gồm nhiều Unit"). */
public enum UnitStatus {
    /** Chưa được claim. */
    PENDING,
    /** Đang xử lý bởi 1 worker (claim có lease; hết lease -> worker khác claim lại). */
    PROCESSING,
    /** Xử lý thành công — không bao giờ xử lý lại. */
    DONE,
    /** Lỗi sau khi retry vượt maxAttempts — cần vận hành/danh sách DLQ. */
    FAILED,
    /** Lỗi tạm thời — đã lên lịch retry (next_retry_at) theo exponential backoff. */
    RETRY,
    /** Chờ handler/dependency được triển khai hoặc khôi phục; tự kiểm tra lại, không tiêu maxAttempts. */
    WAITING_DEPENDENCY
}
