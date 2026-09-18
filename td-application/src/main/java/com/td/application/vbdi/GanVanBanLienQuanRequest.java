package com.td.application.vbdi;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng "modal Gán văn bản đến và Gán văn bản đi đang lỗi tương tự" — chưa
 * cutover FE nên vẫn gọi Domino): request cho {@code POST /api/v1/vbdi/GanVanBanLienQuan} — DÙNG CHUNG
 * cho cả 2 action {@code ganvanbanden} (chọn văn bản đến) và {@code ganvanbandi} (chọn văn bản đi) của
 * {@code GanVanBanModal.jsx}, xem javadoc {@link GanVanBanLienQuanUseCase}.
 *
 * <p>{@code relatedDocuments}: mảng snapshot hiển thị các văn bản được chọn (id/code/subject/
 * unit_publish/promulgation_date/files/dbpath/database_type — ĐÚNG cấu trúc {@code vanban_lienquan_obj}
 * FE tự dựng, xem {@code GanVanBanModal.jsx#btnActionGanVanBanDen}/{@code btnActionGanVanBanDi}).
 * {@code id} có thể là UUID Postgres HOẶC UNID Domino (32 hex) — modal tìm kiếm liệt kê cả văn bản cũ
 * lẫn mới, KHÔNG ép kiểu UUID (server không cần mở lại văn bản được tham chiếu, xem javadoc use case).
 * {@code database_type} do FE tự gán ("vbden"/"vbdi" tuỳ actionname) — server không diễn giải, chỉ lưu
 * lại nguyên trạng.</p>
 */
@Data
public class GanVanBanLienQuanRequest {

    @NotNull(message = "vbdiId khong duoc de trong")
    private UUID vbdiId;

    @NotEmpty(message = "Chua chon van ban can gan")
    private List<Map<String, Object>> relatedDocuments;
}
