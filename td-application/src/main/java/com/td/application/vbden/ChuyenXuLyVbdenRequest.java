package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-211 (VBDen — Xử lý & Chuyển tiếp): request cho {@code POST /api/v1/vbden/ChuyenXuLy} — tương
 * thích API legacy {@code qlvb/vbden/ChuyenXuLy}. Domino thật dùng MỘT action cho cả phân công xử
 * lý, ghi ý kiến và chuyển tiếp, phân biệt bởi tham số {@code action} (vd. {@code phanxuly},
 * {@code chuyenxuly}, {@code phanphoi}, {@code chuyenphoihop}, {@code chuyenxemdebiet}) — giữ
 * nguyên đúng thiết kế này thay vì tách endpoint (đối chiếu {@code ChuyenXuLy.java:129-165}).
 *
 * <p>Các field {@code userCoordinate}/{@code unitCoordinate}/{@code userToRead}/{@code unitToRead}/
 * {@code userTrack}/{@code userManage} là tuỳ chọn, chỉ dùng khi {@code action} tương ứng
 * (phối hợp/xem để biết/theo dõi/phụ trách) — không ảnh hưởng caller cũ chỉ gửi
 * {@code userProcess}/{@code unitProcess}.</p>
 */
@Data
public class ChuyenXuLyVbdenRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    @NotBlank(message = "action khong duoc de trong")
    private String action;

    /** Người được giao/chuyển tiếp (chủ trì) — chuỗi phân tách bởi {@code ;}, đúng quy ước TEXT_LIST. */
    private String userProcess;

    /** Đơn vị xử lý (chủ trì) — chuỗi phân tách bởi {@code ;} (tuỳ chọn). */
    private String unitProcess;

    /** Người phối hợp — dùng khi {@code action=chuyenphoihop} (Domino: {@code updatePhoihop}). */
    private String userCoordinate;

    /** Đơn vị phối hợp — tuỳ chọn, đi kèm {@code userCoordinate}. */
    private String unitCoordinate;

    /** Người xem để biết — dùng khi {@code action=chuyenxemdebiet} (Domino: {@code updateDebiet}). */
    private String userToRead;

    /** Đơn vị xem để biết — tuỳ chọn, đi kèm {@code userToRead}. */
    private String unitToRead;

    /** Người theo dõi xử lý (Domino: {@code updateTheodoi}) — chỉ thêm, không loại người thực hiện. */
    private String userTrack;

    /** Người phụ trách (Domino: {@code updatePhutrach}) — chỉ thêm, không loại người thực hiện. */
    private String userManage;

    /** Ý kiến xử lý — tương đương field {@code opinion} trên bản ghi quá trình xử lý (vbden_res). */
    private String opinion;

    /**
     * Hạn xử lý mới (bổ sung 22/08/2026, theo báo lỗi FE "Chuyển văn bản" mất field này khi cutover
     * khỏi {@code qlvb/vbden?action=...}) — chuỗi thô {@code dd/MM/yyyy}, cùng định dạng
     * {@code received_date}/{@code promulgation_date} đã xác nhận (D.16, tài liệu phân tích 01) —
     * KHÔNG parse/validate ở tầng request, lưu nguyên văn giống {@code TiepNhanVbdenUseCase}/
     * {@code LuuVanBanDenUseCase}. Để trống nếu không đổi hạn xử lý ở lần chuyển xử lý này.
     */
    private String deadlineDate;

    /**
     * 3 field JSON "rich object" (bổ sung 22/08/2026, cùng lý do trên) — snapshot đầy đủ
     * {tên, username, chức vụ, đơn vị...} của người/đơn vị được chọn, dùng để hiển thị lại đúng
     * (tab "Thông tin phân phối" — {@code SubViewPhanVanBan.jsx}/{@code DonViNguoiNhanXuLy.jsx}).
     * KHÔNG bắt buộc — nếu không gửi, {@code process_obj}/{@code coordinate_obj}/{@code to_read_obj}
     * hiện có trên văn bản (nếu có, từ lần "vào sổ"/"hiệu chỉnh" trước) giữ nguyên, KHÔNG bị xoá.
     * Chuỗi JSON thô (FE tự {@code JSON.stringify(...)}) — lưu nguyên dạng {@code FieldType.JSON}
     * (parse rồi lưu object, khớp quy ước field kết thúc hậu tố {@code _obj} — xem
     * {@code FieldTypeInferrer}).
     */
    private String userProcessObj;

    private String userCoordinateObj;

    private String userToReadObj;

    /**
     * Snapshot "chỉ đạo" (bổ sung 02/09/2026, theo báo lỗi người dùng: tab "Quá trình xử lý" không
     * hiện dòng "-Chỉ đạo:"). Khác {@code userProcessObj}/{@code userCoordinateObj}/
     * {@code userToReadObj} (list nhiều người) — "chỉ đạo"/"Lãnh đạo phân công" là field SCALAR đơn
     * ({@code assigner}/{@code user_assigner}/{@code assigner_position} trên văn bản cha, xem
     * {@code SubViewPhanVanBan.jsx}) — FE tự bọc thành JSON array 1 phần tử
     * {@code [{name, username, position}]} đúng hình dạng mà {@code QuatrinhxulyVBden.jsx} đã sẵn đọc
     * qua field {@code user_assigner_obj}. Tuỳ chọn — không có nghĩa văn bản không có chỉ đạo, chỉ là
     * lần chuyển xử lý này không kèm theo.
     */
    private String userAssignerObj;

    /**
     * Tên hiển thị của người thực hiện (bổ sung 02/09/2026, theo báo lỗi người dùng: tab "Quá trình
     * xử lý" — {@code QuatrinhxulyVBden.jsx} — không hiện được "Người thực hiện" vì component đọc
     * field {@code creator}, còn use case chỉ ghi {@code nguoi_thuc_hien} (username thô, dùng cho
     * tra cứu — {@code LichSuXuLyUseCase} — không đổi)). Tuỳ chọn, CHỈ để hiển thị — không phải
     * trust-critical: field {@code nguoi_thuc_hien} vẫn luôn lấy username từ token phía server, y
     * hệt trước đây, không đọc lại field này. Nếu bỏ trống, use case fallback về
     * {@code nguoi_thuc_hien} (mirror {@code ListVbdiEditHistoryUseCase#toLegacyFields}).
     */
    private String creatorName;

    /** Chức vụ hiển thị của người thực hiện (đi kèm {@code creatorName}) — cùng lý do, tuỳ chọn. */
    private String position;
}
