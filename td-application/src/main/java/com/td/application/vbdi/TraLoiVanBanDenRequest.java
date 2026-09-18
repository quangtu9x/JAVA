package com.td.application.vbdi;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 09/09/2026 (yêu cầu người dùng: modal "Trả lời văn bản đến" tại chi tiết văn bản đi — port
 * {@code GanVanBanModal.jsx#btnActionTraLoiVanBanDen}, Domino {@code qlvb/add} +
 * {@code qlvb/vbden/CapNhatVanBanDen?action=ganduthaotraloi}): liên kết văn bản đi
 * {@link #vbdiId} với 1+ văn bản đến được chọn ({@link #documentIn}).
 *
 * <p>{@link #documentIn}: mỗi phần tử là snapshot HIỂN THỊ của 1 văn bản đến (khớp NGUYÊN VĂN cấu
 * trúc {@code documentin_obj} FE tự dựng ở {@code GanVanBanModal.jsx} dòng 274-283 — {@code id, code,
 * subject, unit_publish, promulgation_date, files, dbpath}) — CHẤP NHẬN client-supplied (display-only,
 * KHÔNG security-sensitive — cùng tiền lệ {@link AddVbdiUserActionLogRequest}), CHỈ {@code id} là
 * trust-critical (dùng để tra {@code documentRepository.findById}, không tin field khác của map này
 * cho logic nghiệp vụ). Field lạ trong map KHÔNG bị lọc — lưu nguyên vào {@code documentin_obj} (đúng
 * hành vi Domino: field nào FE gửi cũng được lưu, không whitelist).</p>
 */
@Data
public class TraLoiVanBanDenRequest {

    @NotNull(message = "vbdiId khong duoc de trong")
    private UUID vbdiId;

    @NotEmpty(message = "Chua chon van ban den can tra loi")
    private List<Map<String, Object>> documentIn;

    /** Tên hiển thị người thực hiện — tuỳ chọn, display-only (xem javadoc {@code ChuyenXuLyVbdenRequest#creatorName}). */
    private String creatorName;

    /** Chức vụ hiển thị — tuỳ chọn, đi kèm {@link #creatorName}. */
    private String position;
}
