package com.td.application.vbden;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Port của {@code TDDocumentUlties.tinhSoNgayConLai}/{@code xacDinhTrangThai} (Domino thật —
 * {@code BE_THANHHOA/Code/Java/vn/com/tandan/xsp/common/TDDocumentUlties.java:2188-2223}) — nền
 * tảng xác định trạng thái xử lý ({@code status} "0"-"4") theo hạn xử lý ({@code deadline_date}).
 * Dùng bởi {@link ChuyenXuLyVbdenUseCase} (BL-211, recompute status khi phân công chủ trì lần đầu,
 * đúng {@code ChuyenXuLy.java:240-259}) và sẽ tái sử dụng cho các view "quá hạn/đến hạn/sắp tới
 * hạn" (N11-N14, slice sau).
 *
 * <p><b>Lưu ý</b>: biên "sắp tới hạn" ({@code <= 3} ngày) và "đến hạn" ({@code == 0} ngày) chỉ suy
 * ra được từ call-site ({@code VaoSoNhieu.java:298-308} và method backup {@code tao_so_van_ban_bk()}
 * không còn dùng), KHÔNG đọc được {@code $Formula} gốc của View Domino (đã compiled, không còn
 * dạng text trong repo) — cần xác nhận lại với chủ dữ liệu trước khi dùng làm ranh giới chính thức
 * cho N13.</p>
 */
@Service
public class VbdenStatusService {

    /** Mới, chưa phân công — Domino stamp mặc định khi tạo văn bản (vd. {@code GuiXinYKien.tao_vbden}). */
    public static final String STATUS_MOI = "0";
    /** Quá hạn, còn người đang xử lý (deadline_date đã qua, user_process/user_processing khác rỗng). */
    public static final String STATUS_QUA_HAN_DANG_XU_LY = "1";
    /** Còn hạn, còn người đang xử lý. */
    public static final String STATUS_CON_HAN_DANG_XU_LY = "2";
    /** Quá hạn nhưng đã xử lý xong (user_process/user_processing rỗng, user_processed khác rỗng). */
    public static final String STATUS_QUA_HAN_DA_XU_LY = "3";
    /** Đã xử lý xong, đúng hạn. */
    public static final String STATUS_DUNG_HAN_DA_XU_LY = "4";
    /**
     * 09/09/2026 (port view Domino {@code vwDocumentIn_Internet_Refuse} — route FE
     * {@code /vbden/xuly/tuchoidenquamang}): Domino gốc chỉ set status này khi văn bản KHÔNG gửi
     * liên thông nội bộ, hoặc đơn vị xử lý là đơn vị CUỐI CÙNG còn lại trong danh sách nhận (khái
     * niệm không có tương đương trực tiếp trong kiến trúc Java — mỗi tenant nhận 1 dòng
     * {@code documents} riêng, xem javadoc {@link TuChoiTiepNhanVbdenUseCase}) — theo quyết định
     * người dùng (09/09/2026), ĐƠN GIẢN HOÁ: LUÔN set status này ngay khi "Từ chối tiếp nhận"
     * ({@code type != "tralai"}), bỏ qua nhánh "đơn vị cuối cùng".
     */
    public static final String STATUS_TU_CHOI_TIEP_NHAN = "5";

    /**
     * Domino: {@code @Date(deadline_date) - @Date(@Today)}, đơn vị ngày (âm nếu đã quá hạn).
     *
     * @param deadlineDate hạn xử lý; {@code null} nếu văn bản chưa có hạn (không tính được, trả về {@code null})
     * @param today        ngày tham chiếu ("hôm nay"); truyền {@code null} để dùng {@link LocalDate#now()}
     */
    public Integer computeRemainingDays(LocalDate deadlineDate, LocalDate today) {
        if (deadlineDate == null) {
            return null;
        }
        LocalDate reference = today != null ? today : LocalDate.now();
        return (int) ChronoUnit.DAYS.between(reference, deadlineDate);
    }

    /**
     * Domino: {@code TDDocumentUlties.xacDinhTrangThai(int soNgayConLai, user_process, user_processing, user_processed)}.
     *
     * @param remainingDays     kết quả {@link #computeRemainingDays}
     * @param hasPendingAssignee {@code user_process} hoặc {@code user_processing} còn khác rỗng (chưa xử lý xong)
     * @param hasProcessed       {@code user_processed} khác rỗng (đã có người xử lý xong)
     */
    public String determineStatus(int remainingDays, boolean hasPendingAssignee, boolean hasProcessed) {
        boolean completed = !hasPendingAssignee && hasProcessed;
        if (remainingDays < 0) {
            return completed ? STATUS_QUA_HAN_DA_XU_LY : STATUS_QUA_HAN_DANG_XU_LY;
        }
        return completed ? STATUS_DUNG_HAN_DA_XU_LY : STATUS_CON_HAN_DANG_XU_LY;
    }
}
