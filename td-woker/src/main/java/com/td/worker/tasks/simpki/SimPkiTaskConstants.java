package com.td.worker.tasks.simpki;

/**
 * Hằng số task "Ký số SIM-PKI" — module MỚI, TÁCH BIỆT HOÀN TOÀN khỏi
 * {@code com.td.worker.tasks.vbdi}/{@code vbden}/{@code guinhanquocgia} hiện có (không sửa gì các
 * module đó). Nguồn nghiệp vụ: {@code docs/TDSimSign/sdk-mobilepki} — SDK ký số từ xa qua SIM ghép
 * chứng thư số (SIM-PKI/MSSP — Mobile Signature Service Provider, chuẩn ETSI TS 102 204) do Cục
 * Chứng thực số và Bảo mật thông tin (Bộ Công an) cung cấp. Khác hẳn mô hình ký số hiện có của
 * {@code td-application} ({@code KySoVbdiUseCase}/{@code KySoVbdenUseCase}: người dùng tự ký cục bộ
 * bằng USB token/HSM rồi upload file PDF đã ký) — SIM-PKI là ký số MÁY CHỦ KHỞI TẠO: server gửi
 * hash cần ký + số điện thoại (MSISDN) tới webservice MSSP, người dùng xác nhận giao dịch ngay trên
 * điện thoại (so khớp "mã xác thực"/Verification Code hiển thị), MSSP trả về chữ ký PKCS#1 dạng
 * đồng bộ (synch).
 *
 * <p>Convention đặt tên {@code <module>.<action>} đúng như {@code vbdi.delivery}/
 * {@code guinhanquocgia.send} đã có.</p>
 */
public final class SimPkiTaskConstants {

    private SimPkiTaskConstants() {
    }

    /**
     * Ký số 1 giá trị hash/DigestInfo qua SIM-PKI (đồng bộ — chờ người dùng xác nhận trên điện
     * thoại trong {@code worker.tasks.simpki.timeout-seconds}). Port tham khảo
     * {@code docs/TDSimSign/sdk-mobilepki/java/sample-code/ExtSignature.java#sign}.
     */
    public static final String TASK_SIGN = "simpki.sign";

    /** taskType envelope phản hồi kết quả ký (publish khi {@code worker.rabbit.enabled=true}). */
    public static final String TASK_SIGN_REPLY_DEFAULT = "simpki.sign_reply";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SIGNED = "SIGNED";
    public static final String STATUS_FAILED = "FAILED";

    /** Bảng dedup/audit RIÊNG của Worker — nằm trong database CỦA WORKER (không phải tenant),
     *  KHÔNG lưu nội dung chữ ký (dữ liệu nghiệp vụ thuộc td-web) — chỉ audit trạng thái/trace id. */
    public static final String SIGN_LOG_TABLE = "worker_simpki_sign_log";

    /** Placeholder mã xác thực (Verification Code) mà MSSP tự thay thế trên điện thoại người
     *  dùng — KHÔNG được thay thế phía server (đúng hành vi thật của SDK, xem VerificationCodeCalculator). */
    public static final String VC_PLACEHOLDER = "%%VC%%";
}
