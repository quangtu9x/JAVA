package com.td.worker;

import com.td.worker.config.WorkerProperties;
import com.td.worker.tasks.simpki.SimPkiSslBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Khởi động Worker Platform — tiến trình ĐỘC LẬP hoàn toàn khỏi td-web (Spring Boot web API).
 *
 * <p>Worker là ứng dụng Spring Boot riêng, chạy trên máy riêng được (xem
 * docs/architecture/11 §13 của project tham khảo): chỉ có endpoint actuator
 * ({@code /actuator/health}, {@code /actuator/prometheus}) trên cổng riêng — KHÔNG có
 * controller nghiệp vụ nào. (Bổ sung: 1 endpoint DEBUG/TEST tùy chọn, mặc định TẮT — xem
 * {@link com.td.worker.transport.http.DebugTriggerController} — để test độc lập không cần dựng
 * broker thật, KHÔNG phải controller nghiệp vụ, không bật ở production.)</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerApplication {

    public static void main(String[] args) {
        // NGOẠI LỆ có chủ đích với nguyên tắc "Worker Core không biết nghiệp vụ module": SSL JVM
        // global của SIM-PKI PHẢI được set TRƯỚC KHI Spring tạo bất kỳ bean nào (kể cả
        // WorkerHttpClient) — không cơ chế Spring bean lifecycle nào (kể cả @PostConstruct/
        // @DependsOn) đảm bảo "chạy trước MỌI bean khác" đáng tin cậy bằng việc gọi trước
        // SpringApplication.run(). Xem javadoc SimPkiSslBootstrap (bug thật đã phát hiện + sửa,
        // docs/18-trien-khai-test-simpki.md mục "Bug đã sửa (2)") — no-op an toàn nếu
        // worker.tasks.simpki không được bật qua biến môi trường thật.
        SimPkiSslBootstrap.applyIfConfigured();
        SpringApplication.run(WorkerApplication.class, args);
    }
}