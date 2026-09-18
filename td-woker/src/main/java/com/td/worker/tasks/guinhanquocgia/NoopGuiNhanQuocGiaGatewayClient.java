package com.td.worker.tasks.guinhanquocgia;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation MẶC ĐỊNH của {@link GuiNhanQuocGiaGatewayClient} — bổ sung 09/09/2026.
 * Active khi {@code worker.tasks.guinhanquocgia.enabled=false} (mặc định, an toàn) hoặc khi
 * {@link VpcpGuiNhanQuocGiaGatewayClient} chưa được kích hoạt vì lý do nào khác. KHÔNG gọi SDK VXP,
 * chỉ log + trả kết quả FAILED rõ ràng ({@code NOT_CONFIGURED}) để {@link GuiNhanQuocGiaSendTaskHandler}
 * biết đây là do cấu hình, không phải lỗi vận hành.
 *
 * <p>KHÔNG tự đăng ký bean bằng {@code @Component} — đăng ký qua {@code @Bean} trong
 * {@link GuiNhanQuocGiaGatewayConfig} (xem javadoc ở đó lý do: {@code @ConditionalOnMissingBean}
 * trên {@code @Component} quét bằng component-scan KHÔNG đảm bảo thứ tự xử lý theo tài liệu chính
 * thức Spring Boot).</p>
 */
@Slf4j
public class NoopGuiNhanQuocGiaGatewayClient implements GuiNhanQuocGiaGatewayClient {

    @Override
    public GuiNhanQuocGiaSendOutcome sendEdoc(GuiNhanQuocGiaSendPayload payload) {
        log.warn("guinhanquocgia.send: gatewayClient chưa cấu hình (worker.tasks.guinhanquocgia.enabled=false "
            + "hoặc thiếu endpoint/systemId/secret) — documentId={} KHÔNG được gửi ra Trục liên thông",
            payload == null ? null : payload.getDocumentId());
        return GuiNhanQuocGiaSendOutcome.failed("NOT_CONFIGURED", "guinhanquocgia gateway chưa cấu hình/chưa bật");
    }
}
