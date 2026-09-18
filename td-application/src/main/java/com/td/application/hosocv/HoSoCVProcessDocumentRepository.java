package com.td.application.hosocv;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.hosocv.HoSoCVProcessDocument;

import java.util.List;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — port cho bảng vật lý ĐỘC LẬP {@code hosocv_process_documents}
 * (xem javadoc {@link HoSoCVProcessDocument}).
 */
public interface HoSoCVProcessDocumentRepository extends IRepository<HoSoCVProcessDocument> {

    /**
     * Toàn bộ vết xử lý của 1 hồ sơ ({@code dynamic_fields->>'hosocv_id' = hosocvId}), sắp theo
     * thời gian tạo tăng dần (đúng thứ tự lịch sử) — dùng cho {@code GetHoSoChiTietUseCase} nhóm
     * theo {@code process_type} (trinhduyet/duyet/chuyenluutru/phoihop/gopy).
     */
    List<HoSoCVProcessDocument> findByHoSoCVId(UUID hosocvId);
}
