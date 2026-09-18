package com.td.worker.tasks.simpki;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký bean {@link SimPkiGatewayClient}.
 *
 * <p>CỐ Ý dùng {@code @Configuration} + {@code @Bean} (KHÔNG phải {@code @Component} trực tiếp trên
 * {@link VgcaMsspSignatureClient}/{@link NoopSimPkiGatewayClient}) — theo đúng khuyến cáo chính thức
 * Spring Boot: {@code @ConditionalOnMissingBean} chỉ đảm bảo thứ tự xử lý ĐÚNG khi dùng trong
 * {@code @Configuration}/{@code @Bean}; dùng trực tiếp trên {@code @Component} được component-scan
 * KHÔNG đảm bảo bean kia đã đăng ký trước khi điều kiện được đánh giá — bài học đã rút ra thật khi
 * làm {@code GuiNhanQuocGiaGatewayConfig} (context load fail lần đầu, xem docs/16-checklist Phần 4),
 * áp dụng lại NGAY TỪ ĐẦU cho module này để không lặp lại lỗi.</p>
 *
 * <p>Đúng 1 trong 2 bean tồn tại tuỳ {@code worker.tasks.simpki.enabled}:</p>
 * <ul>
 *   <li>{@code true} → {@link VgcaMsspSignatureClient} (gọi webservice MSSP thật, thiết lập SSL JVM
 *       global — xem cảnh báo javadoc lớp đó).</li>
 *   <li>{@code false} (mặc định) → {@link NoopSimPkiGatewayClient} (an toàn, không đụng SDK/SSL).</li>
 * </ul>
 */
@Configuration
public class SimPkiGatewayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "worker.tasks.simpki", name = "enabled", havingValue = "true")
    public SimPkiGatewayClient vgcaMsspSignatureClient(SimPkiProperties props) {
        return new VgcaMsspSignatureClient(props);
    }

    @Bean
    @ConditionalOnMissingBean(SimPkiGatewayClient.class)
    public SimPkiGatewayClient noopSimPkiGatewayClient() {
        return new NoopSimPkiGatewayClient();
    }
}
