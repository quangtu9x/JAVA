package com.td.worker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Toàn bộ cấu hình Worker — KHÔNG hard-code credential/database-environment-specific config
 * (đúng docs/promt.txt "Production Requirements / Externalized configuration"). Mọi giá trị
 * mặc định ở đây CHỈ là giá trị PHÁT TRIỂN; giá trị thật được nạp qua biến môi trường
 * (xem application.yml).
 */
@Data
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {

    /** Định danh instance dùng để claim lease; tự sinh UUID nếu không cấu hình. */
    private String instanceId = UUID.randomUUID().toString();

    private Core core = new Core();
    private Db db = new Db();
    private ControlPlane controlPlane = new ControlPlane();
    private Tenant tenant = new Tenant();
    private Http http = new Http();
    private Minio minio = new Minio();
    private Rabbit rabbit = new Rabbit();
    private Kafka kafka = new Kafka();
    private Tasks tasks = new Tasks();
    private FileRegistry fileRegistry = new FileRegistry();

    /** Định danh thực dùng cho {@code claimed_by}/lease — sinh 1 lần cho mỗi tiến trình. */
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private volatile String effectiveInstanceId;

    /**
     * 23/10/2026 (chạy nhiều máy chủ): LUÔN duy nhất — {@code <WORKER_INSTANCE_ID hoặc hostname>-<8 ký tự
     * ngẫu nhiên>}, sinh 1 lần/tiến trình rồi giữ nguyên. Trước đây trả nguyên {@code WORKER_INSTANCE_ID}
     * nên copy cùng 1 giá trị sang 2 máy (vd docker-compose {@code worker-1} khi scale) làm 2 máy cùng
     * "sở hữu" 1 claim/lease GC; còn để trống thì MỖI LẦN GỌI ra 1 UUID khác nhau.
     */
    public String resolveInstanceId() {
        String id = effectiveInstanceId;
        if (id == null) {
            synchronized (this) {
                if (effectiveInstanceId == null) {
                    effectiveInstanceId = buildEffectiveInstanceId();
                }
                id = effectiveInstanceId;
            }
        }
        return id;
    }

    private String buildEffectiveInstanceId() {
        String prefix = instanceId == null ? "" : instanceId.trim();
        if (prefix.isEmpty()) {
            try {
                prefix = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                prefix = "worker";
            }
        }
        if (prefix.length() > 150) {
            prefix = prefix.substring(0, 150);
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Cấu hình lõi (concurrency, timeout, retry, lease, ACK policy). */
    @Data
    public static class Core {
        private int maxInflightUnits = 200;
        private Duration unitTimeout = Duration.ofSeconds(60);
        private Duration messageTimeout = Duration.ofMinutes(10);
        private int retriesPerUnit = 5;
        private Duration retryInitialBackoff = Duration.ofSeconds(5);
        private Duration retryMaxBackoff = Duration.ofMinutes(5);
        private double retryBackoffMultiplier = 2.0;
        private Duration retryPollInterval = Duration.ofSeconds(5);
        private int retryPollBatchSize = 100;
        private int claimLeaseSeconds = 120;
        /** Chu kỳ gia hạn lease unit đang chạy (heartbeat); null/0 = claimLeaseSeconds/3. */
        private Duration leaseRenewInterval;
        /**
         * Trần thời gian 1 unit được giữ lease (kể cả đã interrupt mà handler chưa thoát) — quá trần thì
         * NGỪNG gia hạn để lease hết hạn, máy khác nhận lại (unit treo vĩnh viễn không kẹt mãi).
         */
        private Duration maxProcessingTime = Duration.ofMinutes(15);
        private Duration pendingStaleTimeout = Duration.ofMinutes(5);
        private Duration dependencyRetryInterval = Duration.ofMinutes(5);
        private AckPolicy ackPolicy = AckPolicy.ALL_ACCOUNTED;
        private Duration shutdownGrace = Duration.ofSeconds(30);
        private int schedulerPoolSize = 2;
    }

    /** Database RIÊNG của Worker (checkpoint store) — vd tên db "tdworker". */
    @Data
    public static class Db {
        private String url = "jdbc:postgresql://localhost:5432/tdworker";
        private String username = "postgres";
        private String password = "postgres";
        private String driverClassName = "org.postgresql.Driver";
        /** flyway | script — script dùng cho test (H2) khi không muốn chạy Flyway. */
        private String schemaManagement = "flyway";
        /** batch (mặc định) gửi ngay các unit của một envelope; single ghi từng unit. */
        private String unitInsertMode = "batch";
        /** Số unit tối đa trong một JDBC batch; không tích luỹ giữa các message. */
        private int unitInsertBatchSize = 100;
        private Hikari hikari = new Hikari();
    }

    /** Database control-plane — CHỈ ĐỌC registry tenant (bảng tenants) của td-web. */
    @Data
    public static class ControlPlane {
        private boolean enabled = true;
        private String url = "jdbc:postgresql://localhost:5432/tdwebapi_controlplane";
        private String username = "postgres";
        private String password = "postgres";
        private String driverClassName = "org.postgresql.Driver";
        /** CÙNG key/salt với td-web (TENANT_CREDENTIAL_ENCRYPTION_KEY/SALT) — bắt buộc. */
        private String credentialEncryptionKey = "";
        private String credentialEncryptionSalt = "";
        private Duration refreshInterval = Duration.ofSeconds(30);
        private int maxPoolSize = 5;
    }

    /** Connection pool mỗi tenant (database-per-tenant, tạo lười, giới hạn số pool tối đa). */
    @Data
    public static class Tenant {
        private int maxPools = 50;
        private int poolMinimumIdle = 1;
        private int poolMaximumSize = 5;
        private long poolConnectionTimeoutMs = 30_000;
        private long poolValidationTimeoutMs = 5_000;
        private long poolIdleTimeoutMs = 600_000;
        private long poolMaxLifetimeMs = 1_800_000;
        private boolean poolAutoCommit = false;
    }

    @Data
    public static class Hikari {
        private int minimumIdle = 2;
        private int maximumPoolSize = 10;
        private long connectionTimeoutMs = 30_000;
        private long validationTimeoutMs = 5_000;
        private long idleTimeoutMs = 600_000;
        private long maxLifetimeMs = 1_800_000;
        private boolean autoCommit = true;
    }

    /** HTTP client pool (Apache HttpClient 5). */
    @Data
    public static class Http {
        private boolean enabled = true;
        private int maxConnections = 100;
        private int maxConnectionsPerRoute = 20;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration socketTimeout = Duration.ofSeconds(120);
        private Duration connectionTtl = Duration.ofMinutes(5);
    }

    /** MinIO client (tùy chọn — chỉ khi handler cần kiểm tra objectKey tham chiếu). */
    @Data
    public static class Minio {
        private boolean enabled = false;
        private String url = "http://localhost:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucketName = "td-webapi-files";
    }

    /**
     * Sổ đăng ký tệp đính kèm dùng chung (yêu cầu người dùng 22/10/2026) — database RIÊNG, CÙNG database với
     * {@code app.file-registry} của td-web (td-web sở hữu migration). Xem {@code SharedFileRegistry}/
     * {@code SharedFileGcJob}.
     */
    @Data
    public static class FileRegistry {
        private boolean enabled = true;
        private String url = "jdbc:postgresql://localhost:5432/tdfileregistry";
        private String username = "postgres";
        private String password = "postgres";
        private String driverClassName = "org.postgresql.Driver";
        private int maximumPoolSize = 4;
        /** Job dọn dẹp object MinIO hết tham chiếu (+ quét dữ liệu cũ 1 lần/tenant). */
        private boolean gcEnabled = true;
        private String gcCron = "0 30 2 * * *";
        /** Object hết tham chiếu phải chờ bấy lâu mới bị xóa (yêu cầu người dùng: 7 ngày). */
        private Duration gracePeriod = Duration.ofDays(7);
        private int gcBatchSize = 200;
        private int gcMaxBatchesPerRun = 50;
    }

    /** RabbitMQ topology. */
    @Data
    public static class Rabbit {
        private boolean enabled = false;
        private String exchange = "worker.exchange";
        private String dlxSuffix = ".dlx";
        private String dlqSuffix = ".dlq";
        private String retrySuffix = ".retry";
        private long retryDelayMs = 5_000;
        private int maxRedeliveries = 8;
        private List<QueueSpec> queues = new ArrayList<>();
    }

    /** Kafka consumer. */
    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String groupIdPrefix = "td-worker-";
        private String autoOffsetReset = "earliest";
        private int concurrency = 1;
        private long pollTimeoutMs = 1_000;
        private String dlqTopicSuffix = ".dlq";
        private List<QueueSpec> topics = new ArrayList<>();
    }

    /** 1 queue/topic với taskType mặc định (rỗng = generic "tất cả task"). */
    @Data
    public static class QueueSpec {
        private String name = "worker.tasks";
        private String taskType = "";
        private int concurrency = 4;
        private int maxConcurrency = 8;
        private int prefetch = 5;
        /** Tổng message/giây của queue; 0 nghĩa là không giới hạn. */
        private double rateLimitPerSecond = 0;
    }

    /** Cấu hình task cụ thể (module theo từng loại task, không nằm trong Core). */
    @Data
    public static class Tasks {
        private Vbdi vbdi = new Vbdi();
        private Vbden vbden = new Vbden();
    }

    @Data
    public static class Vbdi {
        private boolean enabled = true;
        private boolean verifyObjectExists = false;
        private String replyTopic = "vbdi.reply";
        private String receivedTable = "worker_vbdi_delivery_received";
        /**
         * Tạo THẬT 1 bản ghi "văn bản đến chờ vào sổ" (bảng {@code documents}, cùng schema
         * td-web/BusinessDocument — Worker KHÔNG tự migrate, chỉ INSERT qua JDBC thuần) cho
         * {@code recipientGroup=contact_unit} (cơ quan/đơn vị KHÁC thuộc tỉnh — bugfix 06/09/2026,
         * trước đó nhầm gắn vào {@code internal_unit}). Tắt cờ này để quay lại
         * hành vi cũ (chỉ ghi dedup, không tạo văn bản) nếu cần rollback vận hành mà không đổi code.
         */
        private boolean createIncomingDocument = true;
    }

    /**
     * Bổ sung 08/09/2026 ("chuyển xử lý cho đơn vị con có tenant riêng" — port
     * {@code DocumentInProcess.java#copyDocumentProcessToDocumentIn()}/{@code DocumentInProcessUnit
     * .java}, Domino form mail-in {@code document_process_unit}). Cờ RIÊNG, độc lập với
     * {@link Vbdi#enabled} — 2 module task khác nhau ({@code com.td.worker.tasks.vbden} khác
     * {@code com.td.worker.tasks.vbdi}).
     */
    @Data
    public static class Vbden {
        private boolean enabled = true;
    }

    public enum AckPolicy {
        /** ACK khi MỌI unit đã được "accounted": DONE / FAILED / RETRY(đã lên lịch) / đang do worker khác claim. */
        ALL_ACCOUNTED,
        /** ACK chỉ khi mọi unit về trạng thái tận cùng DONE/FAILED (chặt hơn, giữ message unacked lâu hơn). */
        ALL_TERMINAL
    }
}
