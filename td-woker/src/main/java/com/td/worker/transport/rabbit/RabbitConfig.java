package com.td.worker.transport.rabbit;

import com.rabbitmq.client.Channel;
import com.td.worker.config.WorkerProperties;
import com.td.worker.transport.InboundMessageRouter;
import com.td.worker.transport.RoutedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * RabbitMQ topology CHUNG cho mọi task:
 * <ul>
 *   <li>1 topic exchange {@code worker.exchange} — producer publish với routing key
 *       {@code worker.<taskType>}.</li>
 *   <li>Mỗi queue theo cấu hình ({@code worker.rabbit.queues}): queue CHÍNH durable trỏ DLX,
 *       exchange DLX (fanout) + queue DLQ.</li>
 *   <li>Queue generic ({@code taskType=""}) bind {@code worker.*}; queue chuyên biệt bind
 *       {@code worker.<taskType>} — competing consumers: N Worker cùng consume 1 queue.</li>
 * </ul>
 *
 * <p>ACK manual: parse-error -> nack DLQ; flow OK -> ACK theo policy; lỗi hạ tầng tạm thời ->
 * requeue (bounded bởi {@code x-delivery-count}). Retry per-unit KHÔNG qua requeue broker —
 * do {@code RetryPoller} trên DB checkpoint (nguồn sự thật).</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "worker.rabbit", name = "enabled", havingValue = "true")
public class RabbitConfig {

    private final WorkerProperties props;
    private final InboundMessageRouter router;
    private final RabbitTemplate rabbitTemplate;

    @Bean(destroyMethod = "")
    public List<SimpleMessageListenerContainer> workerRabbitContainers(
            ConnectionFactory connectionFactory) {
        validateCapacity();
        declareTopology(connectionFactory);

        List<SimpleMessageListenerContainer> containers = new ArrayList<>();
        for (WorkerProperties.QueueSpec spec : props.getRabbit().getQueues()) {
            containers.add(buildContainer(connectionFactory, spec));
        }
        if (containers.isEmpty()) {
            log.warn("worker.rabbit.enabled=true nhưng worker.rabbit.queues rỗng — không có consumer");
        }
        return containers;
    }

    /**
 * Các containers trả về dạng List không tự lifecycle - cần coordinator SmartLifecycle
 * start/stop đúng thứ tự: phase=10 (sau UnitDispatcher phase 0) đảm bảo dispatcher sẵn
 * sàng trước khi nhận message; khi stop, containers dừng TRƯỚC dispatcher.
 */
@Bean
    public SmartLifecycle rabbitListenerLifecycle(org.springframework.beans.factory.ObjectProvider<List<SimpleMessageListenerContainer>> containers) {
        return new SmartLifecycle() {
            private final java.util.concurrent.atomic.AtomicBoolean running =
                new java.util.concurrent.atomic.AtomicBoolean(false);

            @Override
            public void start() {
                if (running.compareAndSet(false, true)) {
                    List<SimpleMessageListenerContainer> list = containers.getIfAvailable();
                    if (list == null) {
                        list = java.util.Collections.emptyList();
                    }
                    list.forEach(SimpleMessageListenerContainer::start);
                    log.info("Started {} RabbitMQ listener(s)", list.size());
                }
            }

            @Override
            public void stop() {
                if (running.compareAndSet(true, false)) {
                    List<SimpleMessageListenerContainer> list = containers.getIfAvailable();
                    if (list == null) {
                        list = java.util.Collections.emptyList();
                    }
                    list.forEach(SimpleMessageListenerContainer::stop);
                    log.info("Stopped RabbitMQ listener(s)");
                }
            }

            @Override public boolean isRunning() { return running.get(); }
            @Override public boolean isAutoStartup() { return true; }
            /** phase cao hơn UnitDispatcher(0): start sau dispatcher, stop trước dispatcher. */
            @Override public int getPhase() { return 10; }
        };
    }

    private void declareTopology(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setIgnoreDeclarationExceptions(true);
        TopicExchange exchange = new TopicExchange(props.getRabbit().getExchange(), true, false);
        admin.declareExchange(exchange);
        for (WorkerProperties.QueueSpec spec : props.getRabbit().getQueues()) {
            String queueName = spec.getName();
            String dlxName = queueName + props.getRabbit().getDlxSuffix();
            String dlqName = queueName + props.getRabbit().getDlqSuffix();
            String retryExchangeName = queueName + props.getRabbit().getRetrySuffix();
            String retryQueueName = retryExchangeName + ".queue";
            String routingKey = routingKey(spec);

            Queue mainQueue = QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", retryExchangeName)
                .build();
            admin.declareQueue(mainQueue);
            TopicExchange retryExchange = new TopicExchange(retryExchangeName, true, false);
            admin.declareExchange(retryExchange);
            // Chỉ retry queue có TTL. Khi hết TTL, DLX giữ routing key worker.<taskType>
            // gốc để message quay lại đúng main queue.
            Queue retryQueue = QueueBuilder.durable(retryQueueName)
                .withArgument("x-message-ttl", props.getRabbit().getRetryDelayMs())
                .withArgument("x-dead-letter-exchange", props.getRabbit().getExchange())
                .build();
            admin.declareQueue(retryQueue);
            admin.declareBinding(BindingBuilder.bind(retryQueue).to(retryExchange).with(routingKey));
            FanoutExchange dlx = new FanoutExchange(dlxName, true, false);
            admin.declareExchange(dlx);
            Queue dlq = QueueBuilder.durable(dlqName).build();
            admin.declareQueue(dlq);
            admin.declareBinding(BindingBuilder.bind(dlq).to(dlx));
            admin.declareBinding(BindingBuilder.bind(mainQueue).to(exchange).with(routingKey));
            log.info("RabbitMQ topology: main={}, retry={} (delay={}ms), DLQ={}, routing='{}'",
                queueName, retryQueueName, props.getRabbit().getRetryDelayMs(), dlqName, routingKey);
        }
    }

    private SimpleMessageListenerContainer buildContainer(ConnectionFactory connectionFactory,
                                                          WorkerProperties.QueueSpec spec) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(spec.getName());
        container.setConcurrentConsumers(Math.max(1, spec.getConcurrency()));
        container.setMaxConcurrentConsumers(Math.max(spec.getConcurrency(), spec.getMaxConcurrency()));
        container.setPrefetchCount(Math.max(1, spec.getPrefetch()));
        container.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        container.setDefaultRequeueRejected(false);
        container.setMissingQueuesFatal(false);
        MessageRateLimiter limiter = new MessageRateLimiter(spec.getRateLimitPerSecond());
        container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> handle(message, channel, limiter, spec));
        log.info("RabbitMQ consumer: queue={}, concurrency={}-{}, prefetch={}, rateLimitPerSecond={}", spec.getName(),
            spec.getConcurrency(), spec.getMaxConcurrency(), spec.getPrefetch(), spec.getRateLimitPerSecond());
        return container;
    }

    private void handle(Message message, Channel channel, MessageRateLimiter limiter,
                        WorkerProperties.QueueSpec spec) throws IOException {
        limiter.acquire();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        RoutedResult result = router.route(body);
        MessageProperties mp = message.getMessageProperties();
        long tag = mp.getDeliveryTag();
        switch (result.type()) {
            case PARSE_ERROR -> moveToDlq(message, channel, tag, spec, "parse error");
            case FLOW_OK -> {
                if (result.canAck()) {
                    channel.basicAck(tag, false);
                } else {
                    // Chưa đủ điều kiện ACK theo policy: để unacked, broker redeliver khi
                    // ack-timeout; init idempotent + checkpoint DB không trùng xử lý.
                    log.debug("Message {} chưa đủ điều kiện ACK (policy {}) — chờ redelivery",
                        mp.getMessageId(), props.getCore().getAckPolicy());
                }
            }
            case TRANSIENT_ERROR -> {
                if (retryAttempts(message, spec) >= props.getRabbit().getMaxRedeliveries()) {
                    log.error("Message {} retry quá {} lần -> DLQ", mp.getMessageId(),
                        props.getRabbit().getMaxRedeliveries());
                    moveToDlq(message, channel, tag, spec, "transient retries exhausted");
                } else {
                    // Main queue DLX -> retry queue TTL -> main queue. Không requeue nóng.
                    channel.basicNack(tag, false, false);
                }
            }
        }
    }

    private String routingKey(WorkerProperties.QueueSpec spec) {
        return (spec.getTaskType() == null || spec.getTaskType().isBlank())
            ? "worker.*" : "worker." + spec.getTaskType();
    }

    private int retryAttempts(Message message, WorkerProperties.QueueSpec spec) {
        String retryQueue = spec.getName() + props.getRabbit().getRetrySuffix() + ".queue";
        for (java.util.Map<String, ?> death : message.getMessageProperties().getXDeathHeader()) {
            if (retryQueue.equals(death.get("queue")) && death.get("count") instanceof Number count) {
                return count.intValue();
            }
        }
        return 0;
    }

    /** Chỉ ACK nguồn sau khi publisher confirm xác nhận message đã vào DLX/DLQ. */
    private void moveToDlq(Message message, Channel channel, long tag, WorkerProperties.QueueSpec spec,
                           String reason) throws IOException {
        String dlxName = spec.getName() + props.getRabbit().getDlxSuffix();
        MessageProperties source = message.getMessageProperties();
        MessageProperties target = new MessageProperties();
        target.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        target.setContentType(source.getContentType());
        target.setContentEncoding(source.getContentEncoding());
        target.setMessageId(source.getMessageId());
        target.setCorrelationId(source.getCorrelationId());
        source.getHeaders().forEach(target::setHeader);
        target.setHeader("x-worker-dlq-reason", reason);
        CorrelationData correlation = new CorrelationData("dlq-" + UUID.randomUUID());
        rabbitTemplate.send(dlxName, "", new Message(message.getBody(), target), correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IOException("Broker rejected DLQ publish: " + confirm.getReason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting DLQ publisher confirm", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException("DLQ publisher confirm failed", e);
        }
        channel.basicAck(tag, false);
    }

    private void validateCapacity() {
        int totalMaxConsumers = props.getRabbit().getQueues().stream()
            .mapToInt(WorkerProperties.QueueSpec::getMaxConcurrency).sum();
        int checkpointPool = props.getDb().getHikari().getMaximumPoolSize();
        for (WorkerProperties.QueueSpec spec : props.getRabbit().getQueues()) {
            if (spec.getConcurrency() < 1 || spec.getMaxConcurrency() < spec.getConcurrency()) {
                throw new IllegalStateException("Rabbit queue " + spec.getName()
                    + " requires 1 <= concurrency <= max-concurrency");
            }
            if (spec.getPrefetch() < 1 || spec.getPrefetch() > 10) {
                throw new IllegalStateException("Rabbit queue " + spec.getName()
                    + " prefetch must be in range 1..10");
            }
            if (spec.getRateLimitPerSecond() < 0) {
                throw new IllegalStateException("Rabbit queue " + spec.getName()
                    + " rate-limit-per-second must be >= 0");
            }
        }
        if (checkpointPool < totalMaxConsumers) {
            throw new IllegalStateException("worker.db.hikari.maximum-pool-size=" + checkpointPool
                + " is below total Rabbit max-concurrency=" + totalMaxConsumers);
        }
    }

    /** Thread-safe fixed-rate limiter shared by all consumers of one queue. */
    private static final class MessageRateLimiter {
        private final long intervalNanos;
        private long nextPermitNanos;

        private MessageRateLimiter(double permitsPerSecond) {
            this.intervalNanos = permitsPerSecond > 0
                ? Math.max(1L, (long) (1_000_000_000d / permitsPerSecond)) : 0L;
        }

        private synchronized void acquire() throws IOException {
            if (intervalNanos == 0) {
                return;
            }
            long now = System.nanoTime();
            long permitAt = Math.max(now, nextPermitNanos);
            nextPermitNanos = permitAt + intervalNanos;
            long waitNanos = permitAt - now;
            if (waitNanos <= 0) {
                return;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while rate limiting RabbitMQ consumption", e);
            }
        }
    }
}
