package com.td.worker.infrastructure.minio;

import com.td.worker.config.WorkerProperties;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MinIO client — envelope chỉ mang {@code objectKey}/{@code sha256} ("message nhẹ",
 * docs/architecture/08 §3.2); Worker chỉ kiểm tra tệp tồn tại khi handler yêu cầu.
 * Worker KHÔNG tải file về máy (chỉ FE qua td-web mới tải — docs/architecture/11 §13.2).
 */
@Slf4j
@Component
public class WorkerMinioClient {

    private final WorkerProperties props;
    private final MinioClient client;

    public WorkerMinioClient(WorkerProperties props) {
        this.props = props;
        if (props.getMinio().isEnabled()) {
            WorkerProperties.Minio m = props.getMinio();
            this.client = MinioClient.builder()
                .endpoint(m.getUrl())
                .credentials(m.getAccessKey(), m.getSecretKey())
                .build();
        } else {
            this.client = null;
        }
    }

    public boolean isEnabled() {
        return props.getMinio().isEnabled() && client != null;
    }

    /** Kiểm tra object tồn tại trong bucket cấu hình. */
    public boolean objectExists(String objectName) {
        if (!isEnabled() || objectName == null || objectName.isBlank()) {
            return false;
        }
        try {
            client.statObject(StatObjectArgs.builder()
                .bucket(props.getMinio().getBucketName())
                .object(objectName)
                .build());
            return true;
        } catch (Exception e) {
            log.debug("MinIO object '{}' không tồn tại: {}", objectName, e.getMessage());
            return false;
        }
    }

    public String bucketName() {
        return props.getMinio().getBucketName();
    }
}