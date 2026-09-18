package com.td.worker.tasks.simpki;

import lombok.extern.slf4j.Slf4j;
import vgca.mssp.JvmSsl;

import java.io.File;

/**
 * Thiết lập SSL JVM global cho SIM-PKI CÀNG SỚM CÀNG TỐT — gọi từ {@code WorkerApplication.main()}
 * NGAY TRƯỚC {@code SpringApplication.run(...)}, tức là TRƯỚC KHI Spring tạo BẤT KỲ bean nào (kể cả
 * {@code WorkerHttpClient}).
 *
 * <p><b>Vì sao bắt buộc phải sớm như vậy</b> (bug thật đã phát hiện + sửa — xem
 * docs/18-trien-khai-test-simpki.md mục "Bug đã sửa (2)", và javadoc
 * {@link VgcaMsspSignatureClient}): {@code WorkerHttpClient} (Apache HttpClient5) gọi
 * {@code SSLContexts.createSystemDefault()} NGAY LÚC khởi tạo bean (rất sớm trong vòng đời Spring
 * context, luôn xảy ra bất kể có task nào chạy hay không) — hàm này đọc
 * {@code javax.net.ssl.trustStore} TẠI THỜI ĐIỂM ĐÓ rồi CACHE lại SSLContext dùng cho JVM. Nếu
 * {@code VgcaMsspSignatureClient} chỉ set property này LƯỜI (lần {@code sign()} đầu tiên — luôn xảy
 * ra SAU khi {@code WorkerHttpClient} đã khởi tạo xong) thì ĐÃ QUÁ MUỘN — dẫn tới
 * {@code SSLHandshakeException: PKIX path building failed} khi gọi MSSP dù cấu hình trustStorePath
 * hoàn toàn đúng (đã THỰC NGHIỆM xác nhận: set qua {@code -Djavax.net.ssl.trustStore=...} lúc khởi
 * động JVM → THÀNH CÔNG tới tận {@code UNAUTHORIZED_ACCESS}; set lười trong {@code sign()} → LUÔN
 * lỗi PKIX, không bao giờ chạm được MSSP thật).</p>
 *
 * <p><b>Đọc biến môi trường TRỰC TIẾP</b> ({@code System.getenv}, KHÔNG qua {@code .env}/Spring
 * Environment) — tại thời điểm {@code main()} chạy dòng đầu tiên, Spring context (và cơ chế nạp
 * {@code .env} qua {@code spring.config.import}) CHƯA tồn tại. Do đó CŨNG áp dụng đúng bài học đã
 * ghi ở docs/18-trien-khai-test-simpki.md mục 0: chỉ hoạt động nếu biến môi trường là BIẾN MÔI
 * TRƯỜNG HỆ ĐIỀU HÀNH THẬT (export/set trước khi chạy {@code java -jar} — đúng cách
 * {@code run-simpki.sh}/{@code run-simpki.ps1} đã làm), KHÔNG phải chỉ ghi trong file {@code .env}.</p>
 *
 * <p>An toàn khi thiếu cấu hình: KHÔNG throw, chỉ log WARN rồi bỏ qua (để
 * {@link VgcaMsspSignatureClient#sign} báo lỗi rõ ràng — {@code MISSING_KEYSTORE_CONFIG}/
 * {@code KEYSTORE_FILE_NOT_FOUND} — khi thực sự có yêu cầu ký, thay vì làm Worker khởi động thất
 * bại chỉ vì thiếu cấu hình 1 module tuỳ chọn).</p>
 */
@Slf4j
public final class SimPkiSslBootstrap {

    private SimPkiSslBootstrap() {
    }

    public static void applyIfConfigured() {
        if (!"true".equalsIgnoreCase(env("WORKER_TASKS_SIMPKI_ENABLED", ""))) {
            return;
        }
        String trustStorePath = env("WORKER_TASKS_SIMPKI_TRUSTSTOREPATH", "");
        String keyStorePath = env("WORKER_TASKS_SIMPKI_KEYSTOREPATH", "");
        String keyStorePassword = env("WORKER_TASKS_SIMPKI_KEYSTOREPASSWORD", "");
        if (trustStorePath.isBlank() || keyStorePath.isBlank() || keyStorePassword.isBlank()) {
            log.warn("simpki: WORKER_TASKS_SIMPKI_ENABLED=true nhưng thiếu biến môi trường "
                + "WORKER_TASKS_SIMPKI_TRUSTSTOREPATH/KEYSTOREPATH/KEYSTOREPASSWORD (biến môi trường "
                + "THẬT, không chỉ trong .env — xem docs/18-trien-khai-test-simpki.md mục 0) — bỏ qua "
                + "thiết lập SSL sớm, sẽ báo lỗi rõ ràng khi thực sự có yêu cầu ký.");
            return;
        }
        if (!new File(trustStorePath).isFile() || !new File(keyStorePath).isFile()) {
            log.warn("simpki: không tìm thấy file trustStorePath ({}) hoặc keyStorePath ({}) lúc khởi "
                + "động sớm — bỏ qua thiết lập SSL sớm, sẽ báo lỗi rõ ràng khi thực sự có yêu cầu ký.",
                trustStorePath, keyStorePath);
            return;
        }
        String trustStorePassword = env("WORKER_TASKS_SIMPKI_TRUSTSTOREPASSWORD", "");
        String keyStoreType = env("WORKER_TASKS_SIMPKI_KEYSTORETYPE", "PKCS12");

        log.warn("simpki: thiết lập javax.net.ssl.keyStore/trustStore TOÀN CỤC NGAY LÚC KHỞI ĐỘNG "
            + "(trước khi Spring tạo bất kỳ bean nào, kể cả WorkerHttpClient) — BẮT BUỘC phải sớm, xem "
            + "javadoc SimPkiSslBootstrap/docs/18-trien-khai-test-simpki.md mục 'Bug đã sửa (2)'. Ảnh "
            + "hưởng mọi kết nối HTTPS khác của tiến trình Worker này — khuyến nghị tách instance riêng "
            + "nếu instance này còn xử lý task khác cần client certificate HTTPS khác.");
        try {
            JvmSsl.setSSL(trustStorePath, trustStorePassword, keyStorePath, keyStorePassword, keyStoreType);
            VgcaMsspSignatureClient.markSslConfiguredExternally();
        } catch (Throwable t) {
            log.error("simpki: thiết lập SSL sớm thất bại ({}: {}) — VgcaMsspSignatureClient sẽ tự thử "
                + "lại lúc gọi ký thật (LƯU Ý: thử lại lúc đó có thể quá muộn nếu WorkerHttpClient đã "
                + "khởi tạo xong, xem javadoc lớp này).", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return v == null ? defaultValue : v;
    }
}
