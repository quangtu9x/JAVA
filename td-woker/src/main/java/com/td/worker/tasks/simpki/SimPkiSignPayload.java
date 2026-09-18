package com.td.worker.tasks.simpki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Payload task {@code simpki.sign}.
 *
 * <p>Người gửi (td-web/td-application, hoặc bất kỳ producer nào publish envelope
 * {@code taskType=simpki.sign}) chịu trách nhiệm TÍNH SẴN {@code hashBase64} (vd SHA-256 của nội
 * dung file cần ký, đóng gói DigestInfo nếu chuẩn ký yêu cầu — xem
 * {@code docs/TDSimSign/sdk-mobilepki/java/samplemssp}) — Worker KHÔNG tự đọc file/tính hash (giữ
 * đúng nguyên tắc "message nhẹ", docs/architecture/08 §3.2).</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimPkiSignPayload {

    /** Id văn bản/tệp nội bộ (td-web) — dùng đối chiếu/audit, KHÔNG gửi cho MSSP. */
    private String documentId;

    /** Id tệp đính kèm cần gắn chữ ký (tuỳ chọn) — dùng khi cần đối chiếu file_metadata phía td-web. */
    private String fileId;

    /** Số điện thoại thuê bao SIM-PKI của người ký (định dạng quốc tế không dấu "+", vd "84972495193"). */
    private String msisdn;

    /**
     * Giá trị cần ký, mã hoá base64 — theo sample SDK đây thường là DigestInfo (hash đã đóng gói
     * ASN.1 DigestInfo, KHÔNG phải hash trần) tuỳ theo yêu cầu định dạng ký; Worker CHUYỂN NGUYÊN
     * mảng byte này cho MSSP, không diễn giải/biến đổi.
     */
    private String hashBase64;

    /**
     * Nội dung hiển thị xác nhận trên điện thoại — nếu bỏ trống, dùng
     * {@code worker.tasks.simpki.display-message-template} (phải chứa {@code %%VC%%} nếu muốn hiện
     * mã xác thực).
     */
    private String displayMessage;
}
