package com.td.application.hosoluutru;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;

import java.util.List;
import java.util.UUID;

/**
 * Port cho bảng vật lý ĐỘC LẬP {@code hosoluutru_van_ban_documents} (văn bản gắn vào 1 hồ sơ lưu trữ,
 * xem javadoc {@link HoSoLuuTruVanBanDocument}).
 */
public interface HoSoLuuTruVanBanDocumentRepository extends IRepository<HoSoLuuTruVanBanDocument> {

    /** Toàn bộ văn bản con CHƯA XOÁ của 1 hồ sơ lưu trữ ({@code dynamic_fields->>'hoso_id' = hosoId}). */
    List<HoSoLuuTruVanBanDocument> findByHosoId(UUID hosoId);

    /** Văn bản con ĐÃ XOÁ MỀM của 1 hồ sơ lưu trữ — dùng khi khôi phục hồ sơ cha (đệ quy). */
    List<HoSoLuuTruVanBanDocument> findDeletedByHosoId(UUID hosoId);
}
