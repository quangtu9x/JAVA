package com.td.application.hosoluutru;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port cho bảng vật lý ĐỘC LẬP {@code hosoluutru_phieu_yeu_cau_documents} (phiếu yêu cầu mượn/trả tài
 * liệu lưu trữ, xem javadoc {@link HoSoLuuTruPhieuYeuCauDocument}).
 */
public interface HoSoLuuTruPhieuYeuCauDocumentRepository extends IRepository<HoSoLuuTruPhieuYeuCauDocument> {

    /** Toàn bộ phiếu yêu cầu gắn với 1 hồ sơ lưu trữ ({@code dynamic_fields->>'hoso_id' = hosoId}). */
    List<HoSoLuuTruPhieuYeuCauDocument> findByHosoId(UUID hosoId);

    /**
     * Danh sách phiếu yêu cầu (`phieuyeucauN_by_identifier`/`_dangmuon`/`_quahan`/`_datra`) — cùng ngữ
     * nghĩa tổ hợp {@code HoSoLuuTruDocumentRepository#findByCriteria}: {@code matchAnyFieldNames}
     * dùng cho khoá cá nhân ({@code identifier} khi kho xem theo đơn vị, hoặc {@code user_creator} khi
     * người mượn xem của chính mình), {@code fieldValueIn} lọc {@code status}/{@code status1}.
     */
    Page<HoSoLuuTruPhieuYeuCauDocument> findByCriteria(
            List<String> matchAnyFieldNames, String matchValue,
            Map<String, List<String>> fieldValueIn,
            Pageable pageable);
}
