package com.td.infrastructure.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link MessagingProperties} (giống hệt pattern {@code MinIOConfig}/{@code MinIOProperties}).
 * Bean Rabbit/Kafka thật nằm ở {@link RabbitClientConfig}/{@link KafkaClientConfig} — tách riêng để
 * mỗi config chỉ tạo bean khi ĐÚNG broker đang chọn (tránh ép cả 2 client cùng khởi tạo).
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfig {
}
