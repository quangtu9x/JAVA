package com.td.application.phieutrinhtd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Request cho {@code POST /api/v1/phieutrinhtd/ChuyenTrangThai} —
 * {@link com.td.application.phieutrinhtd.ChuyenTrangThaiPhieuTrinhTdUseCase} (tương đương
 * {@code ChiTietPage.jsx#handleTrinh}, xem
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 2.1/6.2). Dùng chung cho
 * mọi lần chuyển {@code status} (trình thông qua "2", trình duyệt cấp 1 "5"/cấp 2 "25", không thông
 * qua "3", đã thông qua "4", không duyệt "6"/"26", đã duyệt "7"/"27", thu hồi "0"...) — {@code
 * targetStatus} truyền tường minh, KHÔNG hardcode danh sách hẹp vì 6 trạng thái "kết quả" + "Trả lại"
 * chưa xác nhận được logic chi tiết riêng (xem tài liệu 20 mục 2.1, rủi ro #2).
 */
@Data
public class ChuyenTrangThaiPhieuTrinhTdRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    @NotBlank(message = "targetStatus khong duoc de trong")
    private String targetStatus;

    /** Ý kiến tại bước chuyển (tuỳ chọn) — ghi vào log {@code quatrinh} + field {@code ykientrinh<N>}. */
    private String opinion;

    /**
     * 06/10/2026 (cutover FE): field bổ sung ghi lên phiếu trình cùng lần chuyển - các field nghiệp vụ FE
     * vẫn tự tính như bản Domino (người thông qua chọn ở bước "Trình thông qua", receivers_agree/refuse,
     * ý kiến/ngày theo từng người {@code trinh<N>_<user>_date}, người nhận khi thu hồi...). Gộp TRƯỚC khi
     * xác định người nhận. Không ghi đè được status/form/người tạo (server-authoritative).
     */
    private Map<String, String> fields = new LinkedHashMap<>();

    /** Người nhận ghi vào dòng quá trình - để trống thì server tự lấy theo trạng thái đích. */
    private String receiver;
    private String userReceiver;
    private String receiverObj;
}
