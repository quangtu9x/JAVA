package com.td.worker.tasks.guinhanquocgia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký bean {@link GuiNhanQuocGiaGatewayClient} — bổ sung 09/09/2026.
 *
 * <p>CỐ Ý dùng {@code @Configuration} + {@code @Bean} (KHÔNG phải {@code @Component} trực tiếp trên
 * {@link VpcpGuiNhanQuocGiaGatewayClient}/{@link NoopGuiNhanQuocGiaGatewayClient}) — theo đúng
 * khuyến cáo chính thức của Spring Boot: {@code @ConditionalOnMissingBean} chỉ đảm bảo thứ tự xử lý
 * ĐÚNG khi dùng trong {@code @Configuration}/{@code @Bean} (hoặc auto-configuration); dùng trực tiếp
 * trên {@code @Component} được component-scan KHÔNG đảm bảo bean kia đã được đăng ký trước khi điều
 * kiện được đánh giá — đã tự xác nhận lỗi này thật (context load fail:
 * "No qualifying bean of type GuiNhanQuocGiaGatewayClient available") khi thử cách đó lúc viết bản
 * scaffold này, nên đổi sang cách này.</p>
 *
 * <p>Đúng 1 trong 2 bean tồn tại tuỳ {@code worker.tasks.guinhanquocgia.enabled}:</p>
 * <ul>
 *   <li>{@code true} → {@link VpcpGuiNhanQuocGiaGatewayClient} (gọi SDK VXP thật).</li>
 *   <li>{@code false} (mặc định) → {@link NoopGuiNhanQuocGiaGatewayClient} (an toàn, không đụng SDK).</li>
 * </ul>
 */
@Configuration
public class GuiNhanQuocGiaGatewayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "worker.tasks.guinhanquocgia", name = "enabled", havingValue = "true")
    public GuiNhanQuocGiaGatewayClient vpcpGuiNhanQuocGiaGatewayClient(GuiNhanQuocGiaProperties props) {
        return new VpcpGuiNhanQuocGiaGatewayClient(props);
    }

    @Bean
    @ConditionalOnMissingBean(GuiNhanQuocGiaGatewayClient.class)
    public GuiNhanQuocGiaGatewayClient noopGuiNhanQuocGiaGatewayClient() {
        return new NoopGuiNhanQuocGiaGatewayClient();
    }
}
