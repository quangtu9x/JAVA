package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N11 (01/09/2026 — "Thu hồi" văn bản đến, xem javadoc {@link ThuHoiVanBanUseCase}): request cho
 * {@code POST /api/v1/vbden/ThuHoiVanBan} — tương thích API legacy {@code qlvb/vbden/ThuHoiVanBan?
 * databasetype=vbden&action=thuhoi&parentid=...} ({@code ThuHoiVanBan.java} thật, nhánh
 * {@code thuHoiToanBo()} — xem lý do chọn nhánh này trong javadoc use case).
 */
@Data
public class ThuHoiVanBanRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Lý do thu hồi — FE ({@code ItemsActiveList.jsx#thuhoivanban}) BẮT BUỘC nhập trước khi gửi. */
    private String opinion;

    /**
     * Bổ sung 18/09/2026 (cutover {@code ModalThuHoiVanBan.jsx} — màn chi tiết văn bản đến, xem
     * javadoc {@link ThuHoiVanBanUseCase} mục "Nhánh chọn lọc"): {@code "all"} (mặc định khi
     * null/rỗng, callsite {@code ItemsActiveList.jsx} không gửi field này) = nhánh {@code
     * thuHoiToanBo()} cũ, giữ NGUYÊN hành vi. {@code "select"} = nhánh {@code thuHoi()} (Domino
     * gốc) — thu hồi ĐÚNG những người/đơn vị trong 4 field {@code *Obj} dưới đây (FE tự lọc theo lựa
     * chọn người dùng trong bảng, dựa trên dữ liệu {@code GetNguoiThuHoi} trả về).
     */
    private String type;

    /**
     * 11/10/2026: true = KHÔNG thu hồi gì, chỉ gỡ người thao tác khỏi user_delivered/user_track nếu không
     * còn ai do họ chuyển đang xử lý/đã xử lý (dọn dấu "đã chuyển" sót từ trước bản sửa 09/10) — FE gọi
     * khi GetNguoiThuHoi rỗng. Không ghi vết quá trình xử lý.
     */
    private boolean cleanupOnly;

    /** Nhánh {@code "select"}: JSON mảng {@code {name,username,identifier}} — chủ trì cần thu hồi. */
    private String userProcessObj;

    /** Nhánh {@code "select"}: JSON mảng {@code {name,username,identifier}} — phối hợp cần thu hồi. */
    private String userCoordinateObj;

    /** Nhánh {@code "select"}: JSON mảng {@code {name,username,identifier}} — xem để biết cần thu hồi. */
    private String userToReadObj;

    /** Nhánh {@code "select"}: JSON mảng {@code {name,username,identifier}} — theo dõi cần thu hồi. */
    private String userTrackObj;
}
