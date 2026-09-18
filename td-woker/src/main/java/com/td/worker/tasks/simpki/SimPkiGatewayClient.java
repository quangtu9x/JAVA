package com.td.worker.tasks.simpki;

/**
 * Cổng gọi ra webservice MSSP (SIM-PKI, ETSI TS 102 204) để ký số từ xa.
 *
 * <p>Tách interface riêng để {@link SimPkiSignTaskHandler} KHÔNG phụ thuộc trực tiếp SDK
 * {@code vgca.mssp}/Axis 1.4 — dễ mock khi test, và cho phép đổi implementation mà không sửa
 * handler. 2 implementation (cùng pattern {@code GuiNhanQuocGiaGatewayClient}):</p>
 * <ul>
 *   <li>{@link NoopSimPkiGatewayClient} — mặc định (khi {@code enabled=false}).</li>
 *   <li>{@link VgcaMsspSignatureClient} — implementation THẬT, chỉ được Spring khởi tạo khi
 *       {@code worker.tasks.simpki.enabled=true}.</li>
 * </ul>
 */
public interface SimPkiGatewayClient {

    SimPkiSignOutcome sign(SimPkiSignPayload payload) throws Exception;
}
