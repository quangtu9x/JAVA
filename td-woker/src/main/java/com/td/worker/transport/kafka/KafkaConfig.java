package com.td.worker.transport.kafka;

import com.td.worker.config.WorkerProperties;
import com.td.worker.transport.InboundMessageRouter;
import com.td.worker.transport.RoutedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer CHUNG cho mọi task:
 * <ul>
 *   <li>Worker instance dùng CÙNG {@code group.id} (groupIdPrefix + topic) → Kafka tự load
 *       balance qua consumer group (competing consumers, partition/rebalance safe).</li>
 *   <li>Offset commit theo {@code AckMode.RECORD} — an toàn vì checkpoint PostgreSQL là nguồn
 *       sự thật: redelivery sau rebalance/crash chỉ gây xử lý lại nhưng unit DONE không bao
 *       giờ chạy lại (idempotency persistent).</li>
 *   <li>Parse-error → publish DLQ (topic + dlq-suffix) rồi trả về (auto-ack).</li>
 *   <li>Lỗi hạ tầng tạm thời → ném exception → {@code DefaultErrorHandler} retry backoff rồi DLQ.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "worker.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

    private final WorkerProperties props;
    private final InboundMessageRouter router;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public List<KafkaMessageListenerContainer<String, String>> workerKafkaContainers(
            KafkaTemplate<String, String> kafkaTemplate) {
        List<KafkaMessageListenerContainer<String, String>> containers = new ArrayList<>();
        for (WorkerProperties.QueueSpec spec : props.getKafka().getTopics()) {
            DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerConfigs(spec));

            ContainerProperties cp = new ContainerProperties(spec.getName());
            cp.setAckMode(ContainerProperties.AckMode.RECORD);
            cp.setPollTimeout(props.getKafka().getPollTimeoutMs());
            cp.setMessageListener((MessageListener<Object, Object>) record ->
                handle(castRecord(record), kafkaTemplate, spec));

            KafkaMessageListenerContainer<String, String> container =
                new KafkaMessageListenerContainer<>(consumerFactory, cp);
            container.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));
            container.setAutoStartup(true);
            containers.add(container);
            log.info("Kafka consumer: topic={}, group={}, autoOffsetReset={}",
                spec.getName(), groupId(spec), props.getKafka().getAutoOffsetReset());
        }
        if (containers.isEmpty()) {
            log.warn("worker.kafka.enabled=true nhưng worker.kafka.topics rỗng");
        }
        return containers;
    }

    /** Coordinator lifecycle cho các container Kafka (List không tự start). */
    @Bean
    public SmartLifecycle kafkaListenerLifecycle(org.springframework.beans.factory.ObjectProvider<List<KafkaMessageListenerContainer<String, String>>> containers) {
        return new SmartLifecycle() {
            private final java.util.concurrent.atomic.AtomicBoolean running =
                new java.util.concurrent.atomic.AtomicBoolean(false);

            @Override
            public void start() {
                if (running.compareAndSet(false, true)) {
                    List<KafkaMessageListenerContainer<String, String>> list = containers.getIfAvailable();
                    if (list == null) {
                        list = java.util.Collections.emptyList();
                    }
                    list.forEach(KafkaMessageListenerContainer::start);
                    log.info("Started {} Kafka listener container(s)", list.size());
                }
            }

            @Override
            public void stop() {
                if (running.compareAndSet(true, false)) {
                    List<KafkaMessageListenerContainer<String, String>> list = containers.getIfAvailable();
                    if (list == null) {
                        list = java.util.Collections.emptyList();
                    }
                    list.forEach(KafkaMessageListenerContainer::stop);
                    log.info("Stopped Kafka listener(s)");
                }
            }

            @Override public boolean isRunning() { return running.get(); }
            @Override public boolean isAutoStartup() { return true; }
            @Override public int getPhase() { return 10; }
        };
    }

    private String groupId(WorkerProperties.QueueSpec spec) {
        return props.getKafka().getGroupIdPrefix() + spec.getName();
    }

    /** Cast record (deserializer thực tế là String/String) sang kiểu String<String>. */
    private ConsumerRecord<String, String> castRecord(ConsumerRecord<Object, Object> r) {
        return new ConsumerRecord<>(r.topic(), r.partition(), r.offset(),
            (String) r.key(), (String) r.value());
    }

    private Map<String, Object> consumerConfigs(WorkerProperties.QueueSpec spec) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId(spec));
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getKafka().getAutoOffsetReset());
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return cfg;
    }

    private DefaultErrorHandler buildErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new org.apache.kafka.common.TopicPartition(
                record.topic() + props.getKafka().getDlqTopicSuffix(), record.partition()));
        FixedBackOff backOff = new FixedBackOff(1_000L, 5);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(com.td.worker.core.envelope.EnvelopeValidationException.class);
        return handler;
    }

    private void handle(ConsumerRecord<String, String> record, KafkaTemplate<String, String> kafkaTemplate,
                        WorkerProperties.QueueSpec spec) {
        RoutedResult result = router.route(record.value());
        switch (result.type()) {
            case PARSE_ERROR -> {
                String dlq = record.topic() + props.getKafka().getDlqTopicSuffix();
                log.error("Kafka record @ {}-{} envelope lỗi -> DLQ {}", record.topic(),
                    record.offset(), dlq);
                kafkaTemplate.send(dlq, record.key(), record.value());
            }
            case FLOW_OK -> {
                // AckMode.RECORD: container tự commit offset sau khi listener trả về.
                // Unit RETRY/PENDING được RetryPoller trên DB xử lý - checkpoint là nguồn sự thật.
            }
            case TRANSIENT_ERROR -> throw new RuntimeException(
                "Lỗi hạ tầng tạm thời khi xử lý Kafka record @ " + record.topic() + "-" + record.offset()
                    + ": " + result.detail());
        }
    }
}