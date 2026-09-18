package com.td.application.workcalendars;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port lichct (08/09/2026) — tương thích form-data {@code LuuDanhMuc?databasetype=lichct} (nhánh
 * {@code form=lich}): field tĩnh ({@code thu2/type/identifier/ghichu}) tách khỏi field động (lưới ô
 * lịch) — cùng cách {@code TaoDuThaoRequest}/{@code buildTaoDuThaoRequest} tách field tĩnh khỏi field
 * động trong {@code DuThaoController} (xem {@code LegacyDanhMucController#buildSaveWorkCalendarRequest}).
 */
@Data
public class SaveWorkCalendarRequest {

    /** Có giá trị → cập nhật; null → tạo mới. */
    private UUID id;

    /** Domino {@code type}: canhan|phong|donvi|coquan|lanhdaotinh. */
    private String scope;

    /** Domino {@code thu2}, {@code dd/MM/yyyy}. */
    private String weekStart;

    /** Domino {@code identifier} (phong/donvi/coquan/lanhdaotinh). */
    private String identifier;

    /** Domino {@code ghichu}. */
    private String note;

    /** Field động (lưới ô lịch), CHƯA lọc theo regex hợp lệ — xem SaveWorkCalendarUseCase. */
    private Map<String, String> weekFields = new LinkedHashMap<>();
}
