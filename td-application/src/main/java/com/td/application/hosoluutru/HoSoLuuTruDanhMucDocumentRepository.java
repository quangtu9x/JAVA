package com.td.application.hosoluutru;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.hosoluutru.HoSoLuuTruDanhMucDocument;

import java.util.List;

/**
 * Port cho bảng vật lý ĐỘC LẬP {@code hosoluutru_danh_muc_documents} (3 danh mục dùng chung
 * {@code kyhieuhoso}/{@code chatluong}/{@code loaihinhtailieu}, xem javadoc
 * {@link HoSoLuuTruDanhMucDocument}).
 */
public interface HoSoLuuTruDanhMucDocumentRepository extends IRepository<HoSoLuuTruDanhMucDocument> {

    /** Toàn bộ bản ghi của 1 loại danh mục ({@code document_type = nameform}), chưa xoá mềm. */
    List<HoSoLuuTruDanhMucDocument> findByDocumentType(String documentType);
}
