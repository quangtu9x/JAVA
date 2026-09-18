package com.td.application.vbden;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-210 (VBDen Core): request cho {@code POST /api/v1/vbden/TiepNhan} — tương thích API legacy
 * {@code qlvb/add?databasetype=vbden} (multipart/form-data), nhưng dùng đúng tên field Domino
 * {@code subject} (trích yếu) thay vì {@code title} chung của {@code qlvb/add}.
 */
@Data
public class TiepNhanVbdenRequest {

    private String subject;

    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;

    // BL-210/211 (bổ sung 19/08/2026): "sổ văn bản" (BL-116, DocumentRegister) cần vào sổ — khi có
    // giá trị, TiepNhanVbdenUseCase phát/ghi nhận document_number SCOPE THEO SỔ NÀY (mirror
    // LuuVanBanDen.java tang_so_cho_so_van_ban) thay vì để trống/dùng counter phẳng
    // DocumentNumberingService (BL-104) — đúng phát hiện đã ghi ở docs/migration-backlog/
    // 05-review-p0-p1-bl210-vs-domino.md mục 4 #1. Để trống (null) giữ nguyên hành vi cũ (không đổi
    // caller hiện có nào chưa truyền field này).
    private UUID documentRegisterId;
}
