package com.td.application.hosoluutru;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.hosoluutru.HoSoLuuTruProcessDocument;

import java.util.List;
import java.util.UUID;

/**
 * Port cho bảng vật lý ĐỘC LẬP {@code hosoluutru_process_documents} (vết xử lý NỘI BỘ hosoluutru,
 * hiện chỉ dùng cho LTLS — xem javadoc {@link HoSoLuuTruProcessDocument}).
 */
public interface HoSoLuuTruProcessDocumentRepository extends IRepository<HoSoLuuTruProcessDocument> {

    /** Toàn bộ vết xử lý LTLS của 1 hồ sơ ({@code dynamic_fields->>'hoso_id' = hosoId}), tăng dần theo thời gian. */
    List<HoSoLuuTruProcessDocument> findByHosoId(UUID hosoId);
}
