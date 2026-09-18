package com.td.worker.tasks.guinhanquocgia;

import com.vpcp.services.KnobstickServiceImp;
import com.vpcp.services.VnptProperties;
import com.vpcp.services.model.SendEdocResult;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Implementation THẬT gọi SDK VXP ({@code SDKVXP-2.0.0.jar}, package {@code com.vpcp.services}) —
 * bổ sung 09/09/2026.
 *
 * <p>THAM KHẢO (KHÔNG copy nguyên) từ
 * {@code docs/td-guinhanquocgia/src/main/java/com/vpcp/example/ServiceTest.java} case 7
 * ("gui van ban") — viết lại theo Spring DI (constructor injection, không {@code static main}, không
 * hard-code dữ liệu tổ chức/endpoint), và SỬA lỗi cú pháp JSON hard-code đã phát hiện ở bản demo gốc
 * (dòng ~64 dùng {@code StringBuffer} nối tay sai định dạng JSON) — dựng JSON bằng escape đúng thay
 * vì nối chuỗi thô.</p>
 *
 * <p>Chữ ký phương thức SDK đã xác minh trực tiếp bằng {@code javap} trên
 * {@code SDKVXP-2.0.0.jar}/{@code EdXML102-0.0.3.jar} (không đoán):
 * {@code VnptProperties(String endpoint, String systemId, String secret, String storePathDir,
 * int maxConnection, int retry)}, {@code KnobstickServiceImp(VnptProperties)},
 * {@code KnobstickServiceImp.sendEdoc(String jsonHeader, String edxmlFilePath)} trả về
 * {@code SendEdocResult} ({@code getStatus()}/{@code getErrorCode()}/{@code getErrorDesc()}/
 * {@code getDocID()}).</p>
 *
 * <p>CHỈ được Spring khởi tạo khi {@code worker.tasks.guinhanquocgia.enabled=true} (mặc định
 * {@code false}, an toàn) — xem {@link GuiNhanQuocGiaGatewayConfig} (đăng ký qua {@code @Bean}, KHÔNG
 * phải {@code @Component} trên chính class này — lý do ở javadoc {@link GuiNhanQuocGiaGatewayConfig}).
 * {@link NoopGuiNhanQuocGiaGatewayClient} là bean mặc định khi tắt.</p>
 *
 * <p><b>Rủi ro/việc bỏ ngỏ đã biết trước</b> (xem {@code docs/16-checklist-tich-hop-gui-nhan-quoc-gia.md}
 * mục rủi ro): {@code SDKVXP-2.0.0.jar}/{@code EdXML102-0.0.3.jar} dùng NỘI BỘ Apache HttpClient
 * 4.5.6/Gson 2.2/jdom2/Guava 14.0.1/Guice/xercesImpl — các jar này CỐ Ý CHƯA được thêm vào
 * {@code pom.xml} của td-woker ở bản scaffold này (rủi ro xung đột phiên bản Guava với
 * Testcontainers — xem pom.xml). Vì vậy việc TẠO instance {@link KnobstickServiceImp} hoặc gọi
 * {@code sendEdoc} RẤT CÓ THỂ ném {@link LinkageError} (thường {@code NoClassDefFoundError}) cho tới
 * khi bổ sung đủ các jar đó. Bắt riêng {@link Throwable} (không chỉ {@link Exception}) ở đây để
 * KHÔNG làm sập bean/Worker khi ai đó bật {@code enabled=true} trước khi hoàn tất bước đó — trả về
 * kết quả FAILED rõ ràng thay vì crash.</p>
 */
@Slf4j
public class VpcpGuiNhanQuocGiaGatewayClient implements GuiNhanQuocGiaGatewayClient {

    private final GuiNhanQuocGiaProperties props;

    public VpcpGuiNhanQuocGiaGatewayClient(GuiNhanQuocGiaProperties props) {
        this.props = props;
    }

    @Override
    public GuiNhanQuocGiaSendOutcome sendEdoc(GuiNhanQuocGiaSendPayload payload) {
        if (payload == null || payload.getEdxmlBase64() == null || payload.getEdxmlBase64().isBlank()) {
            return GuiNhanQuocGiaSendOutcome.failed("MISSING_EDXML",
                "payload thiếu edxmlBase64 (chưa build eDXML từ dữ liệu nghiệp vụ — xem checklist)");
        }
        File tmpFile;
        try {
            tmpFile = File.createTempFile("guinhanquocgia-", ".edxml");
        } catch (Exception e) {
            return GuiNhanQuocGiaSendOutcome.failed("TEMP_FILE_ERROR", e.getMessage());
        }
        try {
            Files.write(tmpFile.toPath(), Base64.getDecoder().decode(payload.getEdxmlBase64()));
            return callSdk(payload, tmpFile);
        } catch (Throwable sdkError) {
            log.error("guinhanquocgia.send: lỗi gọi SDK VXP (kiểm tra dependency transitive gson/"
                + "jdom2/httpclient4/guava/xerces — xem docs/16-checklist mục pom.xml) — documentId={}",
                payload.getDocumentId(), sdkError);
            return GuiNhanQuocGiaSendOutcome.failed("SDK_ERROR", String.valueOf(sdkError.getMessage()));
        } finally {
            try {
                Files.deleteIfExists(tmpFile.toPath());
            } catch (Exception ignore) {
                // best-effort dọn file tạm, không ảnh hưởng kết quả gửi.
            }
        }
    }

    private GuiNhanQuocGiaSendOutcome callSdk(GuiNhanQuocGiaSendPayload payload, File edxmlFile) throws Exception {
        VnptProperties vnptProperties = new VnptProperties(
            props.getEndpoint(), props.getSystemId(), props.getSecret(),
            props.getStorePathDir(), props.getMaxConnection(), props.getMaxErrorRetry());
        KnobstickServiceImp knobstickService = new KnobstickServiceImp(vnptProperties);
        String jsonHeader = buildSendJsonHeader(payload);
        SendEdocResult result = knobstickService.sendEdoc(jsonHeader, edxmlFile.getAbsolutePath());
        if (result == null) {
            return GuiNhanQuocGiaSendOutcome.failed("NULL_RESULT", "SDK VXP trả về null");
        }
        if ("OK".equalsIgnoreCase(result.getStatus())) {
            return GuiNhanQuocGiaSendOutcome.ok(result.getDocID());
        }
        return GuiNhanQuocGiaSendOutcome.failed(result.getErrorCode(), result.getErrorDesc());
    }

    /**
     * Dựng jsonHeader cho {@code sendEdoc} — SỬA lỗi cú pháp JSON của demo gốc (escape đúng thay vì
     * nối chuỗi thô, xem javadoc lớp).
     */
    private String buildSendJsonHeader(GuiNhanQuocGiaSendPayload payload) {
        return "{"
            + "\"from\":\"" + escape(payload.getSenderAgencyCode()) + "\","
            + "\"to\":\"" + escape(payload.getRecipientAgencyCode()) + "\","
            + "\"servicetype\":\"eDoc\","
            + "\"messagetype\":\"edoc\""
            + "}";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
