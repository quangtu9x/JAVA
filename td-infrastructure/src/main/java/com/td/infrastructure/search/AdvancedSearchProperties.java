package com.td.infrastructure.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 17/10/2026 — tìm kiếm nâng cao đọc Elasticsearch (docs/analysis/27). Địa chỉ ES dùng chung
 * {@code spring.elasticsearch.uris}. Index do td-woker tạo và đồng bộ ({@code worker.search-sync}) — 2 bên phải
 * cùng {@link #indexPrefix}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.search.elasticsearch")
public class AdvancedSearchProperties {

    /** Tắt = mọi tìm kiếm chạy SQL như trước. */
    private boolean enabled = false;
    /** PHẢI trùng WORKER_SEARCH_SYNC_INDEX_PREFIX của td-woker. */
    private String indexPrefix = "hscv";
    /** Module đọc ES (vbden, vbdi, duthao, hosocv, phieutrinhtd); module khác chạy SQL. */
    private List<String> modules = new ArrayList<>(List.of("vbden", "vbdi", "duthao", "hosocv", "phieutrinhtd"));
    /** Kiểm tra "index đã sẵn sàng" (alias có + worker nạp xong lần đầu) được nhớ trong khoảng này. */
    private Duration readinessCacheTtl = Duration.ofSeconds(30);
    /**
     * Từ khoá tìm cả trong NỘI DUNG TỆP đính kèm (trường files_text do td-woker trích bằng Tika (chưa OCR) —
     * docs/architecture/14-trich-noi-dung-tep.md). Tắt = chỉ tìm trên các cột của văn bản.
     */
    private boolean includeFileContent = true;

    public boolean isModuleEnabled(String module) {
        return enabled && module != null && modules != null
            && modules.stream().anyMatch(m -> m != null && m.trim().equalsIgnoreCase(module));
    }
}
