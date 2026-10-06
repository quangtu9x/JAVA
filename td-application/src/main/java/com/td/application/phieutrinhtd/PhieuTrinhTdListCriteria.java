package com.td.application.phieutrinhtd;

import lombok.Builder;
import lombok.Getter;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 06/10/2026 (cutover FE phiếu trình) - tiêu chí lọc danh sách phiếu trình dùng chung cho mọi view
 * (tên view Domino thật của {@code view_phieutrinh.dxl} - xem {@link PhieuTrinhTdViewQuery}).
 * Mọi tiêu chí null/rỗng = không lọc. Sắp xếp luôn mới tạo nhất trước.
 */
@Getter
@Builder
public class PhieuTrinhTdListCriteria {

    /** Cách khớp {@link #member} (username đã resolve) với phiếu trình. */
    public enum MemberRule {
        /** user_creator. */
        CREATOR,
        /** user_lanhdaovanphong (người thông qua). */
        NGUOI_THONG_QUA,
        /** user_lanhdao1 khi status=5, user_lanhdao2 khi status=25 (vwUser_NguoiDuyet_5_25). */
        NGUOI_DUYET_DANG_CHO,
        /**
         * Người tham gia (vwUser_All / vwStoraged): người tạo, người thông qua (đã trình thông qua), người
         * đồng ý / từ chối, lãnh đạo duyệt cấp 1/2 (đã trình), người phối hợp.
         */
        THAM_GIA,
        /** Có trong user_coordinator (phân cách "," hoặc ";") - vwUser_Coordinator. */
        PHOI_HOP,
        /** Phối hợp nhưng CHƯA có trong user_coordinated - vwUser_Coordinator_chua. */
        PHOI_HOP_CHUA,
        /** Phối hợp và ĐÃ có trong user_coordinated - vwUser_Coordinator_da. */
        PHOI_HOP_DA
    }

    /** Tình trạng cho ý kiến của các người phối hợp (vwUser_Creator_Coordinated_Status, cột 2). */
    public enum CoordinatedStatus {
        /** Có người phối hợp (bất kể đã cho ý kiến hay chưa). */
        CO_PHOI_HOP,
        /** Mọi người phối hợp đã cho ý kiến (khoá "1"). */
        DA_DU,
        /** Còn người phối hợp chưa cho ý kiến (khoá "0"). */
        CON_CHUA
    }

    private final String member;
    private final MemberRule memberRule;
    private final List<String> statuses;
    /** true = chỉ đã kết thúc (storaged=1), false = chưa kết thúc, null = không lọc. */
    private final Boolean storaged;
    /** true = chỉ thùng rác (đã xoá mềm); mặc định chỉ bản ghi chưa xoá. */
    private final boolean trash;
    private final CoordinatedStatus coordinatedStatus;
    /** Chỉ các id này (vd phiếu trình gắn vào 1 hồ sơ - bảng document_links). null = không lọc. */
    private final Collection<UUID> ids;
    private final String keyword;
    private final List<String> keywordColumns;
    /** Tìm kiếm nâng cao: field động -> chuỗi con (không phân biệt hoa thường). Tên field phải an toàn. */
    private final java.util.Map<String, String> containsFields;
    /** Khoảng ngày tạo (created_on), bao gồm 2 đầu; null = không lọc. */
    private final java.time.LocalDate createdFrom;
    private final java.time.LocalDate createdTo;
}
