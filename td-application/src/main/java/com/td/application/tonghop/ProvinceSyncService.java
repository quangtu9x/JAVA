package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Đồng bộ dữ liệu từ DB từng tenant về kho tổng hợp toàn tỉnh. Cài đặt ở td-infrastructure
 * ({@code ProvinceIndexSyncService}) vì phải lặp qua mọi tenant.
 */
public interface ProvinceSyncService {

    /**
     * Chạy đồng bộ ngay (đồng bộ, chặn tới khi xong). {@code full=true} quét lại toàn bộ thay vì từ watermark —
     * cần khi worker sửa dữ liệu bằng JDBC thuần không cập nhật {@code last_modified_on}.
     */
    SyncStatus syncNow(boolean full);

    SyncStatus status();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SyncStatus {
        private boolean running;
        private List<TenantSyncStatus> tenants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class TenantSyncStatus {
        private String tenantId;
        private OffsetDateTime lastRunOn;
        private OffsetDateTime lastSuccessOn;
        private OffsetDateTime lastFullSyncOn;
        private String lastError;
        private Long lastDurationMs;
        private Integer lastChangedRows;
        private long documents;
    }
}
