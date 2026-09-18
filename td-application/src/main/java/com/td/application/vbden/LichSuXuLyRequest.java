package com.td.application.vbden;

import lombok.Data;

/**
 * N09 (01/09/2026 — "Lịch sử xử lý" cá nhân, xem javadoc {@link LichSuXuLyUseCase}): request cho
 * {@code POST /api/v1/vbden/LichSuXuLy} — tương thích API legacy {@code qlvb/vbden/LichSuXuLy?
 * databasetype=vbden_res} ({@code viewname:"QTXL_by_username"} luôn cố định, KHÔNG cần FE gửi —
 * server tự khoá theo username đăng nhập, xem {@code LichSuXuLy.java} thật:
 * {@code entries.getAllEntriesByKey(connection.user_token.username, true)}).
 */
@Data
public class LichSuXuLyRequest {

    private int pageNumber = 1;

    private int pageSize = 20;
}
