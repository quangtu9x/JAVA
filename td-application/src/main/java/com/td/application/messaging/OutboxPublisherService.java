package com.td.application.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.domain.messaging.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu người dùng 04/09/2026 ("viết class chung... để sau có thể tái dùng, không cần code riêng
 * từng chức năng"): API DUY NHẤT business code cần gọi để publish message ra RabbitMQ/Kafka —
 * KHÔNG bao giờ gọi thẳng {@link MessagePublisher} từ use case nghiệp vụ.
 *
 * <p><b>Outbox pattern</b> (đúng {@code docs/architecture/08 §3.1}, cụ thể hoá vbdi ở
 * {@code docs/architecture/11 §6.1}): {@link #enqueue} CHỈ ghi 1 dòng {@code outbox_events}
 * {@code PENDING} — KHÔNG tự publish, KHÔNG tự mở transaction riêng (chạy TRONG transaction của
 * use case gọi nó, vd {@code AddVbdiSendLogUseCase}) — nếu transaction đó rollback, dòng outbox
 * cũng rollback theo, không có publish "ảo". {@code OutboxDispatcherScheduler}
 * ({@code td-infrastructure}, chạy định kỳ NGOÀI transaction request) mới thực sự publish.</p>
 *
 * <p>Tính năng mới muốn publish message chỉ cần: build {@link OutboundMessage} → gọi
 * {@link #enqueue} — xem {@code JAVA/docs/MESSAGING_OUTBOX_GUIDE.md}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public UUID enqueue(String aggregateType, UUID aggregateId, OutboundMessage message) {
        String payloadJson = toJson(message.units());
        var entity = new OutboxEvent(aggregateType, aggregateId, message.taskType(),
            message.correlationId(), sourceName(), payloadJson);
        var saved = outboxEventRepository.save(entity);
        log.info("Outbox enqueued: aggregateType={}, aggregateId={}, taskType={}, outboxId={}",
            aggregateType, aggregateId, message.taskType(), saved.getId());
        return saved.getId();
    }

    private String sourceName() {
        return "td-web";
    }

    private String toJson(List<MessageUnitSpec> units) {
        try {
            return objectMapper.writeValueAsString(units);
        } catch (Exception ex) {
            // Khong duoc de 1 loi serialize lam hong toan bo request nghiep vu dang chay (vd "Gui
            // van ban") - ghi log ro rang, luu payload rong (Scheduler se skip do thieu unit khi
            // doc lai, khong publish gi ca) thay vi nem exception pha vo transaction goi.
            log.error("Khong the serialize outbox payload - se luu payload rong (khong publish)", ex);
            return "[]";
        }
    }
}
