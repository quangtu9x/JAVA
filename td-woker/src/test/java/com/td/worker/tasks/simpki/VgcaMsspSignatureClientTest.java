package com.td.worker.tasks.simpki;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link VgcaMsspSignatureClient} — tập trung vào các guard-clause XÁC ĐỊNH (deterministic,
 * KHÔNG phụ thuộc mạng/keystore thật/MSSP thật), chạy được không cần Docker/mạng, giống tinh thần
 * {@code VpcpGuiNhanQuocGiaGatewayClientTest#missingEdxmlReturnsFailedOutcomeWithoutTouchingSdk}.
 *
 * <p>KHÔNG test đường gọi SOAP thật thành công (cần AP_ID/AP_PASSWORD/keystore/truststore THẬT do
 * Cục CTS-BMTT cấp + kết nối mạng tới MSSP — phụ thuộc bên ngoài, ngoài phạm vi test tự động, xem
 * docs/17-checklist-tich-hop-kyso-simpki.md mục rủi ro). {@link #callNeverThrowsEvenWithInvalidKeystore()}
 * chỉ khẳng định KHÔNG BAO GIỜ ném exception ra ngoài dù cấu hình sai/thiếu — bất kể
 * {@code ensureSslConfigured()} đã chạy ở lần gọi trước đó trong CÙNG JVM hay chưa (guard
 * {@code AtomicBoolean} TOÀN CỤC theo thiết kế, xem javadoc lớp), cả 2 nhánh đều phải trả về outcome
 * FAILED thay vì throw.</p>
 */
class VgcaMsspSignatureClientTest {

    @TempDir
    Path tempDir;

    private SimPkiSignPayload validPayload() {
        SimPkiSignPayload p = new SimPkiSignPayload();
        p.setDocumentId("doc-1");
        p.setMsisdn("84972495193");
        p.setHashBase64(Base64.getEncoder().encodeToString("noi dung mau".getBytes()));
        return p;
    }

    private SimPkiProperties baseProps() {
        SimPkiProperties props = new SimPkiProperties();
        props.setEnabled(true);
        return props;
    }

    @Test
    void missingApCredentialReturnsFailedOutcomeWithoutTouchingSdk() throws Exception {
        VgcaMsspSignatureClient client = new VgcaMsspSignatureClient(baseProps());

        SimPkiSignOutcome outcome = client.sign(validPayload());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("MISSING_AP_CREDENTIAL");
    }

    @Test
    void missingKeystoreConfigReturnsFailedOutcome() throws Exception {
        SimPkiProperties props = baseProps();
        props.setApId("http://ap.mobile-id.vn/test");
        props.setApPassword("secret");
        VgcaMsspSignatureClient client = new VgcaMsspSignatureClient(props);

        SimPkiSignOutcome outcome = client.sign(validPayload());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("MISSING_KEYSTORE_CONFIG");
    }

    @Test
    void keystoreFileNotFoundReturnsFailedOutcome() throws Exception {
        SimPkiProperties props = baseProps();
        props.setApId("http://ap.mobile-id.vn/test");
        props.setApPassword("secret");
        props.setTrustStorePath(tempDir.resolve("khong-ton-tai-truststore.jks").toString());
        props.setKeyStorePath(tempDir.resolve("khong-ton-tai-keystore.p12").toString());
        props.setKeyStorePassword("secret");
        VgcaMsspSignatureClient client = new VgcaMsspSignatureClient(props);

        SimPkiSignOutcome outcome = client.sign(validPayload());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("KEYSTORE_FILE_NOT_FOUND");
    }

    @Test
    void invalidPayloadReturnsFailedOutcomeWithoutTouchingSdk() throws Exception {
        VgcaMsspSignatureClient client = new VgcaMsspSignatureClient(fullyConfiguredProps());

        SimPkiSignOutcome outcome = client.sign(new SimPkiSignPayload());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("INVALID_PAYLOAD");
    }

    @Test
    void invalidHashBase64ReturnsFailedOutcome() throws Exception {
        VgcaMsspSignatureClient client = new VgcaMsspSignatureClient(fullyConfiguredProps());
        SimPkiSignPayload payload = new SimPkiSignPayload();
        payload.setMsisdn("84972495193");
        payload.setHashBase64("khong-phai-base64-hop-le!!!");

        SimPkiSignOutcome outcome = client.sign(payload);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("INVALID_HASH_BASE64");
    }

    @Test
    void callNeverThrowsEvenWithInvalidKeystore() {
        VgcaMsspSignatureClient client = new VgcaMsspSignatureClient(fullyConfiguredProps());

        // Điều quan trọng cần khẳng định: sign() KHÔNG BAO GIỜ ném exception/Error ra ngoài, dù
        // keystore không hợp lệ (JvmSsl.setSSL ném IllegalArgumentException khi validate) hay endpoint
        // không có thật (lỗi mạng/DNS) — luôn trả về 1 SimPkiSignOutcome (FAILED trong môi trường test
        // này, bất kể ensureSslConfigured() đã "tiêu" guard toàn cục ở lần gọi trước hay chưa).
        SimPkiSignOutcome outcome;
        try {
            outcome = client.sign(validPayload());
        } catch (Exception e) {
            throw new AssertionError("sign() không được phép ném exception ra ngoài", e);
        }

        assertThat(outcome).isNotNull();
        assertThat(outcome.success()).isFalse();
    }

    private SimPkiProperties fullyConfiguredProps() {
        try {
            SimPkiProperties props = baseProps();
            props.setApId("http://ap.mobile-id.vn/test");
            props.setApPassword("secret");
            props.setSignatureUrl("https://khong-co-that.example.invalid/soap/services/MSS_SignaturePort");
            Path trustStore = tempDir.resolve("dummy-truststore.jks");
            Path keyStore = tempDir.resolve("dummy-keystore.p12");
            Files.write(trustStore, "not-a-real-keystore".getBytes());
            Files.write(keyStore, "not-a-real-keystore".getBytes());
            props.setTrustStorePath(trustStore.toString());
            props.setTrustStorePassword("changeit");
            props.setKeyStorePath(keyStore.toString());
            props.setKeyStorePassword("changeit");
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
