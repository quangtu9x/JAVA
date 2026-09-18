package com.td.worker.tasks.simpki;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation MẶC ĐỊNH của {@link SimPkiGatewayClient} — active khi
 * {@code worker.tasks.simpki.enabled=false} (mặc định, an toàn). KHÔNG gọi SDK MSSP, KHÔNG đụng
 * SSL/JVM global (xem {@link VgcaMsspSignatureClient} javadoc về rủi ro SSL global), chỉ log + trả
 * FAILED rõ ràng ({@code NOT_CONFIGURED}) để {@link SimPkiSignTaskHandler} biết đây là do cấu hình,
 * không phải lỗi vận hành.
 *
 * <p>KHÔNG tự đăng ký bean bằng {@code @Component} — đăng ký qua {@code @Bean} trong
 * {@link SimPkiGatewayConfig} (xem javadoc ở đó lý do: {@code @ConditionalOnMissingBean} trên
 * {@code @Component} quét bằng component-scan KHÔNG đảm bảo thứ tự xử lý — bài học đã rút ra khi
 * làm {@code GuiNhanQuocGiaGatewayConfig}, xem docs/16-checklist mục Phần 4).</p>
 */
@Slf4j
public class NoopSimPkiGatewayClient implements SimPkiGatewayClient {

    @Override
    public SimPkiSignOutcome sign(SimPkiSignPayload payload) {
        log.warn("simpki.sign: gatewayClient chưa cấu hình (worker.tasks.simpki.enabled=false hoặc "
            + "thiếu apId/apPassword/keystore) — documentId={}, msisdn={} KHÔNG được ký",
            payload == null ? null : payload.getDocumentId(), payload == null ? null : maskMsisdn(payload.getMsisdn()));
        return SimPkiSignOutcome.failed("NOT_CONFIGURED", "simpki gateway chưa cấu hình/chưa bật");
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 4) {
            return "***";
        }
        return "***" + msisdn.substring(msisdn.length() - 3);
    }
}
