package com.td.infrastructure.messaging;

import com.td.application.messaging.MessagePublisher;
import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantContext;
import com.td.infrastructure.tenant.TenantRoutingDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Yêu cầu người dùng 04/09/2026 ("hoàn thiện Gửi văn bản... code trước luôn"): publisher outbox
 * GENERIC (không riêng vbdi) — đúng {@code docs/architecture/08 §7.1}/{@code docs/architecture/11
 * §7.1} ("Worker A — Outbox Publisher... chạy ở MỌI tenant"). Ở app chính (Spring Boot 1 tiến
 * trình phục vụ mọi tenant — {@code docs/architecture/06}), mô phỏng "chạy ở mọi tenant" bằng cách
 * LẶP QUA từng tenant đã biết ({@link TenantRoutingDataSource#knownTenantIds()}), tạm đặt
 * {@link TenantContext} (qua {@link TenantContext#runAs}) — bắt buộc vì scheduler chạy trên thread
 * pool RIÊNG của Spring, KHÔNG đi qua {@code TenantFilter} (đúng rủi ro đã ghi ở docs/architecture/
 * 11 §7.2/§9 mục 3).
 *
 * <p>Việc xử lý 1 batch/1 tenant tách riêng ở {@link OutboxTenantDispatcher} (bean KHÁC, được
 * inject qua Spring proxy) — {@code @Transactional} KHÔNG có hiệu lực khi tự gọi method trong CÙNG
 * bean (self-invocation bỏ qua proxy AOP của Spring), nên phải tách bean để
 * {@code @Transactional(REQUIRES_NEW)} ở đó thực sự áp dụng cho mỗi tenant.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcherScheduler {

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final ObjectProvider<MessagePublisher> messagePublisher;
    private final OutboxTenantDispatcher outboxTenantDispatcher;

    @Scheduled(fixedDelayString = "${app.messaging.outbox.poll-interval-ms:5000}")
    public void dispatchAllTenants() {
        MessagePublisher publisher = messagePublisher.getIfAvailable();
        if (publisher == null) {
            // Khong co bean MessagePublisher nao active (vd cau hinh sai broker) - bo qua tick nay,
            // KHONG throw lam scheduler ngung han (se tu thu lai o lan chay ke tiep).
            return;
        }
        for (String tenantId : tenantRoutingDataSource.knownTenantIds()) {
            if (!tenantActiveRegistry.isActive(tenantId)) {
                continue;
            }
            try {
                TenantContext.runAs(tenantId, () -> outboxTenantDispatcher.dispatchTenant(publisher));
            } catch (Exception ex) {
                // 1 tenant loi khong duoc lam hong cac tenant con lai trong cung 1 tick.
                log.error("Outbox dispatch that bai cho tenant '{}' - se thu lai o lan chay ke tiep", tenantId, ex);
            }
        }
    }
}
