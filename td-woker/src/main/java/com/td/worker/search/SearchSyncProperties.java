package com.td.worker.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đồng bộ Elasticsearch từ hàng đợi {@code search_index_queue} của từng tenant (xem
 * {@code docs/21-dong-bo-elasticsearch.md} và {@code docs/architecture/13-dong-bo-elasticsearch.md} (thư mục docs gốc HSCV)).
 * Mọi giá trị nạp qua biến môi trường {@code WORKER_SEARCH_SYNC_*} / {@code ELASTICSEARCH_*}
 * (application.yml) — không hardcode địa chỉ, tài khoản, tên index.
 */
@Data
@ConfigurationProperties(prefix = "worker.search-sync")
public class SearchSyncProperties {

    /** Tắt hẳn bộ đồng bộ (hàng đợi vẫn được trigger ghi, bật lại sẽ tự bù). */
    private boolean enabled = true;

    /** Địa chỉ node ES, vd http://10.10.10.116:9200 — để trống = không chạy (log cảnh báo 1 lần). */
    private List<String> uris = new ArrayList<>();
    private String username = "";
    private String password = "";
    /** API key (base64 "id:key") — ưu tiên hơn username/password nếu có. */
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(60);

    /**
     * Tiền tố index — cluster ES DÙNG CHUNG với hệ khác, mọi index của hệ này phải có tiền tố riêng.
     * Alias: {prefix}-{tenant}-{entity}; index vật lý: {alias}-v{mappingVersion}.
     */
    private String indexPrefix = "hscv";
    /** Tăng khi đổi mapping/analyzer → worker tạo index mới, chuyển alias, nạp lại toàn bộ. */
    private int mappingVersion = 1;
    private int numberOfShards = 1;
    /** 0 với cluster 1 node (tránh index "yellow"); >=1 khi cluster nhiều node. */
    private int numberOfReplicas = 0;
    private int totalFieldsLimit = 2000;
    private String refreshInterval = "1s";

    /**
     * Đánh thức theo sự kiện: worker LISTEN kênh {@link #notifyChannel} ở DB từng tenant, trigger (migration
     * V1.0.73) {@code pg_notify} khi COMMIT → xử lý ngay tenant đó. Tắt = chỉ còn quét dự phòng.
     */
    private boolean notifyEnabled = true;
    /** PHẢI khớp kênh trong hàm trigger search_index_enqueue() (migration V1.0.73). */
    private String notifyChannel = "search_index";
    /**
     * Quét DỰ PHÒNG mọi tenant — bù thông báo bị lỡ (worker khởi động lại, mất kết nối LISTEN) + đối soát +
     * metric. Không còn là đường chính nên để thưa.
     */
    private Duration fallbackSweepInterval = Duration.ofMinutes(2);
    /** Số tenant xử lý ĐỒNG THỜI (mỗi tenant tối đa 1 luồng) — tenant nạp lớn không chặn tenant khác. */
    private int maxParallelTenants = 4;
    /** Kết nối LISTEN rảnh quá lâu thì gửi SELECT 1 để phát hiện kết nối chết (firewall cắt, DB khởi động lại). */
    private Duration listenerPingInterval = Duration.ofSeconds(60);
    private Duration listenerReconnectDelay = Duration.ofSeconds(10);
    /** ES không kết nối được → thử lại tenant sau khoảng này. */
    private Duration unavailableRetryDelay = Duration.ofSeconds(15);
    /** Số dòng hàng đợi lấy mỗi lô (1 lệnh bulk). */
    private int batchSize = 500;
    /** Số lô tối đa / lượt / tenant — hết lượt mà còn việc thì tenant xếp lại cuối hàng (nhường tenant khác). */
    private int maxBatchesPerTenantPerCycle = 20;
    private Duration retryInitialBackoff = Duration.ofSeconds(10);
    private Duration retryMaxBackoff = Duration.ofMinutes(30);

    /** Tạo index + nạp toàn bộ bảng khi alias chưa tồn tại (lần đầu triển khai, hoặc ES bị dựng lại). */
    private boolean bootstrapOnMissingIndex = true;
    /** Đối soát số lượng DB ↔ ES; 0 = tắt. */
    private Duration reconcileInterval = Duration.ofHours(6);
    /** Xoá index vật lý phiên bản cũ sau khi chuyển alias sang mappingVersion mới. */
    private boolean deleteOldIndices = false;

    /** Độ dài tối đa trường search_text (gộp mọi giá trị chữ). */
    private int searchTextMaxChars = 32_000;
    /**
     * Khoá dynamic_fields dùng tìm TỪ KHOÁ (thành trường {@code kw.<khoá>} có bỏ dấu) — hợp các cột bảng kết quả của
     * trang tìm kiếm nâng cao 5 module (FE TDExport/columns.jsx). td-web chỉ tìm từ khoá trên các khoá có ở đây; khoá
     * khác → dùng {@code search_text}. Đổi danh sách → tăng mapping-version để nạp lại.
     */
    private List<String> keywordFields = new ArrayList<>(List.of(
        "code", "code_full", "subject", "title", "name", "signer", "draftor", "creator", "unit_in_charge",
        "unit_publish", "noinhanchutri", "process_handler", "approved_by", "first_topic", "second_topic", "category",
        "description", "lanhdaovanphong", "lanhdao1", "lanhdao2", "status", "promulgation_date", "received_date",
        "deadline_date", "create_date", "draft_end_date", "document_type"));
    /** Cột KHÔNG đưa lên ES (vd cột nhị phân/lớn), áp cho mọi bảng. */
    private List<String> excludedColumns = new ArrayList<>();
    /** Chỉ đồng bộ các tenant này; rỗng = mọi tenant active. */
    private List<String> tenants = new ArrayList<>();

    /**
     * entity_type (khớp tham số trigger ở migration V1.0.72) → tên bảng. Thêm bảng = thêm migration tạo
     * trigger + khai ở đây (WORKER_SEARCH_SYNC_ENTITIES_<TYPE>=<bảng>).
     */
    private Map<String, String> entities = defaultEntities();

    private static Map<String, String> defaultEntities() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("documents", "documents");
        map.put("vbdi", "vbdi_documents");
        map.put("duthao", "duthao_documents");
        map.put("hosocv", "hosocv_documents");
        map.put("phieutrinhtd", "phieutrinhtd_documents");
        map.put("vbcddh", "vbcddh_documents");
        map.put("hosoluutru", "hosoluutru_documents");
        return map;
    }

    /** Trích nội dung tệp đính kèm (Tika, KHÔNG OCR) — docs/architecture/14-trich-noi-dung-tep.md. */
    private FileExtract fileExtract = new FileExtract();

    @Data
    public static class FileExtract {
        /** Tắt = không trích (hàng đợi 'file' vẫn ghi, bật sau sẽ tự bù). Cần MinIO bật (WORKER_MINIO_*). */
        private boolean enabled = false;
        /** Thời gian tối đa trích 1 tệp. */
        private Duration fileTimeout = Duration.ofMinutes(5);
        /** Tệp lớn hơn → SKIPPED. */
        private long maxFileBytes = 50L * 1024 * 1024;
        /** Số ký tự tối đa LƯU vào file_content_extracts (phần dư → truncated=true). */
        private int maxStoredChars = 2_000_000;
        /** Số ký tự tối đa nội dung tệp đưa vào trường files_text của 1 văn bản trên Elasticsearch. */
        private int maxIndexedChars = 300_000;
        /** Tiền tố content-type bỏ qua (ảnh — chưa OCR, video, âm thanh, nén...). */
        private List<String> skipContentTypes = new ArrayList<>(List.of("image/", "video/", "audio/", "application/zip",
            "application/x-rar", "application/x-7z", "application/octet-stream"));
        /** Số tệp lấy mỗi lượt / tenant. */
        private int batchSize = 3;
        /** Số tenant trích ĐỒNG THỜI (làn riêng, không chiếm luồng đồng bộ văn bản). */
        private int maxParallelTenants = 2;
        /** "Thuê" tệp trong khoảng này khi trích (không giữ transaction lúc trích); worker chết → hết hạn → thử lại. */
        private Duration lease = Duration.ofMinutes(15);
        /** Số lần thử tối đa trước khi ghi FAILED. */
        private int maxAttempts = 5;
    }

    public boolean isConfigured() {
        return enabled && uris != null && uris.stream().anyMatch(u -> u != null && !u.isBlank());
    }
}
