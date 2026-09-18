package com.td.application.duthao;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng "Văn bản kèm theo -> tương tự khi mở modal chọn văn bản và đồng ý
 * cũng chưa port"): request cho {@code POST /api/v1/duthao/GanVanBanLienQuan} — DÙNG CHUNG cho cả 2
 * action {@code ganvanbanden} (chọn văn bản đến) và {@code ganvanbandi} (chọn văn bản đi) của
 * {@code DuThaoActionModal.jsx} — cùng khuôn request với
 * {@link com.td.application.vbdi.GanVanBanLienQuanRequest} (đã port cho văn bản đi), chỉ khác văn
 * bản CHA là dự thảo ({@link #draftId}) thay vì văn bản đi.
 *
 * <p>{@code relatedDocuments}: mảng snapshot hiển thị các văn bản được chọn (id/code/subject/
 * unit_publish/promulgation_date/files/dbpath/database_type). {@code id} có thể là UUID Postgres HOẶC
 * UNID Domino — server không cần mở lại văn bản được tham chiếu (xem javadoc
 * {@link GanVanBanLienQuanDuThaoUseCase}).</p>
 */
@Data
public class GanVanBanLienQuanDuThaoRequest {

    @NotNull(message = "draftId khong duoc de trong")
    private UUID draftId;

    @NotEmpty(message = "Chua chon van ban can gan")
    private List<Map<String, Object>> relatedDocuments;
}
