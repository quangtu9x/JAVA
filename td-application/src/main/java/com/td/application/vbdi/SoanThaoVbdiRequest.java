package com.td.application.vbdi;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core) — DoD (1): request cho {@code POST /api/v1/vbdi/SoanThao}, cùng khuôn dạng
 * {@code qlvb/add?databasetype=vbdi} (form-data field động) như {@code TiepNhanVbdenRequest} bên
 * vbden — dùng tên field {@code subject} (trích yếu) chung cho cả 2 module.
 */
@Data
public class SoanThaoVbdiRequest {

    private String subject;

    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;

    /**
     * Yêu cầu người dùng 28/08/2026 ("Chọn sổ văn bản"): id sổ văn bản (bảng {@code document_registers}
     * DÙNG CHUNG với văn bản đến, BL-116) đã chọn khi soạn thảo — meta riêng (không phải field
     * động), cùng khuôn {@code TiepNhanVbdenRequest#documentRegisterId}. Rỗng = giữ hành vi cũ
     * (không đánh số theo sổ, {@code document_number} lấy từ field động thô client gửi hoặc BL-104
     * numbering chung).
     */
    private UUID documentRegisterId;
}
