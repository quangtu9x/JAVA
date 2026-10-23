package com.td.application.search;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 17/10/2026 — port tìm kiếm nâng cao trên index tìm kiếm (cài đặt Elasticsearch ở td-infrastructure). Xem
 * docs/analysis/27 và docs/architecture/13.
 *
 * <p>Trả {@link Optional#empty()} khi KHÔNG được dùng index (tắt bằng cấu hình, module không bật, index chưa có,
 * worker chưa nạp xong lần đầu, hoặc lỗi) — người gọi PHẢI chạy truy vấn SQL như cũ. Không bao giờ ném lỗi.</p>
 */
public interface AdvancedSearchIndex {

    /**
     * @param module   tên module (vbden, vbdi, duthao, hosocv, phieutrinhtd) — để bật/tắt theo module
     * @param entity   entity_type của index (documents, vbdi, duthao, hosocv, phieutrinhtd)
     * @param pageIndex trang, bắt đầu từ 0
     */
    Optional<Hits> search(String module, String entity, AdvancedSearchQuery query, int pageIndex, int pageSize);

    /** Id theo đúng thứ tự sắp xếp + tổng số khớp. */
    record Hits(List<UUID> ids, long total) {
    }

    /** Không dùng index (cho test và khi không có cài đặt). */
    AdvancedSearchIndex DISABLED = (module, entity, query, pageIndex, pageSize) -> Optional.empty();
}
