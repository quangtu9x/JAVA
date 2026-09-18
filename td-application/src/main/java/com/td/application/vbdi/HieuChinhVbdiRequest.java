package com.td.application.vbdi;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port luôn" nhánh "Hiệu chỉnh"): request cho
 * {@code POST /api/v1/vbdi/HieuChinh} — tương đương Ý NGHĨA (không byte-for-byte) với
 * {@code qlvb/add?id=<id>&databasetype=vbdi} (Domino, {@code ChiTietModal.jsx#onFinish} nhánh id có
 * sẵn) — cùng khuôn form-data field động như {@link SoanThaoVbdiRequest}, CHỈ khác: mọi field ở đây
 * là PARTIAL UPDATE (merge lên dynamic_fields hiện có, KHÔNG replace toàn bộ như lúc soạn thảo mới)
 * — FE chỉ gửi field THỰC SỰ đã đổi (đúng cách {@code ChiTietModal.jsx#onFinish} tự tính diff qua
 * {@code getChangedFieldDetails} trước khi gọi API).
 */
@Data
public class HieuChinhVbdiRequest {

    private UUID id;

    /** Trích yếu mới (map vào cột tĩnh {@code title}) — null/rỗng = giữ nguyên. */
    private String subject;

    /**
     * Mã trạng thái Domino thô ("0"/"2") hoặc chữ nội bộ — null/rỗng = GIỮ NGUYÊN trạng thái hiện
     * tại (khác {@link SoanThaoVbdiRequest#getStatus()}, nơi rỗng mặc định DRAFT — ở đây rỗng nghĩa
     * là "không đổi", xem {@link VbdiLegacyStatusCodes#toInternalOrNull(String)}).
     */
    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
