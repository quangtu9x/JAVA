package com.td.worker.tasks.guinhanquocgia;

/**
 * Cổng gọi ra Trục liên thông văn bản quốc gia (VPCP/VXP) — bổ sung 09/09/2026.
 *
 * <p>Tách interface riêng để {@link GuiNhanQuocGiaSendTaskHandler} KHÔNG phụ thuộc trực tiếp SDK
 * VXP ({@code SDKVXP-2.0.0.jar}) — dễ mock khi test, và cho phép đổi implementation mà không sửa
 * handler. 2 implementation:</p>
 * <ul>
 *   <li>{@link NoopGuiNhanQuocGiaGatewayClient} — mặc định (khi {@code enabled=false} hoặc chưa có
 *       implementation nào khác được kích hoạt).</li>
 *   <li>{@link VpcpGuiNhanQuocGiaGatewayClient} — implementation THẬT, chỉ được Spring khởi tạo khi
 *       {@code worker.tasks.guinhanquocgia.enabled=true}.</li>
 * </ul>
 */
public interface GuiNhanQuocGiaGatewayClient {

    GuiNhanQuocGiaSendOutcome sendEdoc(GuiNhanQuocGiaSendPayload payload) throws Exception;
}
