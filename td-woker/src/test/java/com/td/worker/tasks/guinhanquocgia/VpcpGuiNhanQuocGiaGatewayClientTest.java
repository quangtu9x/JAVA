package com.td.worker.tasks.guinhanquocgia;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link VpcpGuiNhanQuocGiaGatewayClient} — bổ sung 09/09/2026.
 *
 * <p>{@link #missingEdxmlReturnsFailedOutcomeWithoutTouchingSdk()} an toàn tuyệt đối (không chạm
 * lớp nào của SDK VXP). {@link #realSdkCallNeverThrowsEvenWithoutTransitiveJars()} CỐ Ý gọi thẳng
 * vào SDK thật (để thực nghiệm xác nhận hành vi khi thiếu gson/jdom2/httpclient4/guava — xem javadoc
 * lớp {@link VpcpGuiNhanQuocGiaGatewayClient}) — khẳng định {@code sendEdoc} KHÔNG BAO GIỜ ném
 * exception ra ngoài (dù thiếu dependency transitive), chỉ trả outcome FAILED.</p>
 */
class VpcpGuiNhanQuocGiaGatewayClientTest {

    private GuiNhanQuocGiaSendPayload validPayload() {
        GuiNhanQuocGiaSendPayload p = new GuiNhanQuocGiaSendPayload();
        p.setDocumentId("doc-1");
        p.setSenderAgencyCode("777.77.04.D77");
        p.setRecipientAgencyCode("99.99.99.G21");
        p.setDocType(GuiNhanQuocGiaTaskConstants.DOC_TYPE_NEW);
        p.setEdxmlBase64(Base64.getEncoder().encodeToString("noi dung mau".getBytes()));
        return p;
    }

    @Test
    void missingEdxmlReturnsFailedOutcomeWithoutTouchingSdk() throws Exception {
        GuiNhanQuocGiaProperties props = new GuiNhanQuocGiaProperties();
        props.setEnabled(true);
        VpcpGuiNhanQuocGiaGatewayClient client = new VpcpGuiNhanQuocGiaGatewayClient(props);

        GuiNhanQuocGiaSendPayload payload = new GuiNhanQuocGiaSendPayload();
        payload.setDocumentId("doc-1");
        payload.setRecipientAgencyCode("99.99.99.G21");
        // edxmlBase64 CỐ Ý để trống.

        GuiNhanQuocGiaSendOutcome outcome = client.sendEdoc(payload);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("MISSING_EDXML");
    }

    @Test
    void realSdkCallNeverThrowsEvenWithoutTransitiveJars() {
        GuiNhanQuocGiaProperties props = new GuiNhanQuocGiaProperties();
        props.setEnabled(true);
        props.setEndpoint("https://khong-co-that.example.invalid");
        props.setSystemId("test-system");
        props.setSecret("test-secret");
        props.setStorePathDir(System.getProperty("java.io.tmpdir"));
        VpcpGuiNhanQuocGiaGatewayClient client = new VpcpGuiNhanQuocGiaGatewayClient(props);

        // Điều quan trọng cần khẳng định: sendEdoc KHÔNG BAO GIỜ ném exception/Error ra ngoài, dù
        // thiếu dependency transitive (LinkageError) hay endpoint không có thật (lỗi mạng) — luôn
        // trả về 1 GuiNhanQuocGiaSendOutcome (thường FAILED trong môi trường test này).
        GuiNhanQuocGiaSendOutcome outcome;
        try {
            outcome = client.sendEdoc(validPayload());
        } catch (Exception e) {
            throw new AssertionError("sendEdoc không được phép ném exception ra ngoài", e);
        }

        assertThat(outcome).isNotNull();
        assertThat(outcome.success()).isFalse();
    }
}
