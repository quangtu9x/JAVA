package com.td.worker.tasks.guinhanquocgia;

/**
 * Kết quả gọi cổng gửi văn bản ra Trục liên thông quốc gia — bổ sung 09/09/2026.
 * Tách khỏi {@code UnitResult} (Worker Core) vì đây là kết quả NGHIỆP VỤ của riêng module này
 * (mang thêm {@code centerDocId}/{@code errorCode} của Trung tâm) — {@link GuiNhanQuocGiaSendTaskHandler}
 * là nơi DUY NHẤT chuyển đổi nó sang {@code UnitResult}.
 */
public record GuiNhanQuocGiaSendOutcome(boolean success, String centerDocId, String errorCode, String errorDesc) {

    public static GuiNhanQuocGiaSendOutcome ok(String centerDocId) {
        return new GuiNhanQuocGiaSendOutcome(true, centerDocId, null, null);
    }

    public static GuiNhanQuocGiaSendOutcome failed(String errorCode, String errorDesc) {
        return new GuiNhanQuocGiaSendOutcome(false, null, errorCode, errorDesc);
    }
}
