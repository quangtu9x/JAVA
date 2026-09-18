package com.td.worker.tasks.simpki;

import com.tomicalab.mobileid.datatype.AP_Info;
import com.tomicalab.mobileid.datatype.AdditionalServices;
import com.tomicalab.mobileid.datatype.DTBS;
import com.tomicalab.mobileid.datatype.DataToBeDisplayed;
import com.tomicalab.mobileid.datatype.DataToBeSigned;
import com.tomicalab.mobileid.datatype.Description;
import com.tomicalab.mobileid.datatype.MSSP_ID;
import com.tomicalab.mobileid.datatype.MSSP_Info;
import com.tomicalab.mobileid.datatype.MSS_Format;
import com.tomicalab.mobileid.datatype.MobileUser;
import com.tomicalab.mobileid.datatype.Service;
import com.tomicalab.mobileid.datatype.SignatureProfile;
import lombok.extern.slf4j.Slf4j;
import org.apache.axis.client.Stub;
import org.apache.axis.types.NCName;
import org.apache.axis.types.PositiveInteger;
import org.apache.axis.types.URI;
import org.etsi.uri.TS102204.v1_1_2.MSS_SignatureReqType;
import org.etsi.uri.TS102204.v1_1_2.MSS_SignatureRespType;
import org.etsi.uri.TS102204.v1_1_2.MessageAbstractType;
import org.etsi.uri.TS102204.v1_1_2.MessagingModeType;
import vgca.mssp.JvmSsl;
import vgca.mssp.VerificationCodeCalculator;
import vn.mobile_id.ae.TS102204.etsi204.MSS_SignatureBindingStub;
import vn.mobile_id.ae.TS102204.etsi204.MSS_SignatureServiceLocator;

import java.io.File;
import java.math.BigInteger;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation THẬT gọi webservice MSSP (SIM-PKI, ETSI TS 102 204) — ký số từ xa qua SIM ghép
 * chứng thư số.
 *
 * <p><b>Vì sao KHÔNG dùng thẳng {@code vgca.mssp.ExtSignature}</b> (lớp tiện ích do vendor cung cấp
 * sẵn, xem {@code docs/TDSimSign/sdk-mobilepki/java/sample-code/ExtSignature.java}): lớp đó CHỈ cho
 * phép cấu hình 3 giá trị qua setter công khai ({@code setEPMSSP}/{@code setAPID}/{@code setAPPWD})
 * — {@code MSSP_ID}, {@code signatureProfile}, {@code MSS_Format}, và đặc biệt {@code TimeOut}
 * (HARD-CODE {@code "300"} ngay trong mã nguồn vendor, không có setter) đều KHÔNG thể cấu hình từ
 * bên ngoài. Vi phạm yêu cầu "mọi tham số phải cấu hình được, không hard-code" của module này. Class
 * này viết lại đúng luồng SOAP mà {@code ExtSignature.sign()} thực hiện (transcribe 1:1 từ mã nguồn
 * THAM KHẢO đã đọc trực tiếp, không suy đoán giao thức) nhưng dùng thẳng các lớp Axis/ETSI stub cấp
 * thấp (cùng nằm trong {@code laverca-mobileid_v2.jar}/{@code laverca-mobileid-datatype.jar} — đã
 * khai báo dependency ở {@code pom.xml}) để MỌI giá trị (endpoint, MSSP_ID, signatureProfile,
 * MSS_Format, timeout, AP_ID/AP_PASSWORD, apTransId prefix) đều đọc từ {@link SimPkiProperties}.
 * Vẫn tái dùng nguyên {@code vgca.mssp.VerificationCodeCalculator} (thuật toán tính mã xác thực,
 * không có gì để cấu hình) và {@code vgca.mssp.JvmSsl} (tiện ích set SSL JVM global, xem cảnh báo
 * dưới) từ đúng jar vendor, không viết lại.</p>
 *
 * <p><b>⚠️ CẢNH BÁO KIẾN TRÚC QUAN TRỌNG — SSL JVM GLOBAL</b>: SDK Axis 1.4 (từ năm 2006, KHÔNG hỗ
 * trợ cấu hình {@code SSLContext} theo từng lời gọi) bắt buộc thiết lập chứng thư Client
 * Authentication (mutual TLS) bằng cách set {@code System.setProperty("javax.net.ssl.keyStore"...)}
 * — đây là cấu hình TOÀN CỤC CỦA CẢ JVM (xem javadoc {@code JvmSsl.setSSL} trong sample-code gốc:
 * "This is STATIC, and uses global JVM settings. This can conflict with settings done by some other
 * library function"). {@link com.td.worker.infrastructure.http.WorkerHttpClient} (Apache HttpClient
 * 5, dùng bởi các task handler khác) xây SSLContext qua {@code SSLContexts.createSystemDefault()} —
 * CŨNG đọc CÙNG system property này, nên MỌI kết nối HTTPS khác của tiến trình Worker (không chỉ gọi
 * MSSP) SẼ bị ảnh hưởng bởi keystore/truststore cấu hình ở đây kể từ lần ký đầu tiên sau khi bật
 * {@code enabled=true}. Đây là giới hạn CỦA CHÍNH SDK vendor, không phải lỗi thiết kế ở đây —
 * khuyến nghị: nếu Worker instance này CÒN chạy task handler khác gọi HTTPS ra ngoài với client
 * certificate khác (hoặc không cần client cert), triển khai {@code simpki.sign} trên 1 WORKER
 * INSTANCE RIÊNG (đúng cơ chế multi-instance đã có, xem docs/05-multi-instance.md — mỗi instance có
 * thể lắng nghe taskType khác nhau qua {@code worker.rabbit.queues[].task-type}/
 * {@code worker.kafka.topics[].task-type}), tránh set global SSL ảnh hưởng handler khác trong CÙNG
 * tiến trình. Xem thêm docs/17-checklist-tich-hop-kyso-simpki.md mục rủi ro.</p>
 *
 * <p>CHỈ được Spring khởi tạo khi {@code worker.tasks.simpki.enabled=true} (mặc định {@code false},
 * an toàn) — xem {@link SimPkiGatewayConfig}. {@link NoopSimPkiGatewayClient} là bean mặc định khi
 * tắt — KHÔNG đụng SSL/JVM global.</p>
 *
 * <p><b>⚠️ Bổ sung — bug thật đã phát hiện + sửa (docs/18-trien-khai-test-simpki.md mục "Bug đã sửa
 * (2)")</b>: ban đầu {@link #ensureSslConfigured()} chỉ chạy LƯỜI (lần {@link #sign} ĐẦU TIÊN) —
 * ĐÃ THỰC NGHIỆM XÁC NHẬN đây là QUÁ MUỘN: {@code WorkerHttpClient} (Apache HttpClient5, luôn được
 * Spring khởi tạo lúc context refresh, TRƯỚC BẤT KỲ task nào chạy) gọi
 * {@code SSLContexts.createSystemDefault()} ngay lúc khởi tạo bean — đọc
 * {@code javax.net.ssl.trustStore} TẠI THỜI ĐIỂM ĐÓ (chưa được set) rồi CACHE lại SSLContext dùng
 * truststore JDK mặc định (không có CA của MSSP). Khi {@link #sign} chạy lần đầu và set property
 * này, CHỈ ảnh hưởng các `SSLContext` tạo SAU đó — nhưng thực tế `AxisFault`/JSSE bên trong Axis 1.4
 * VẪN ném `SSLHandshakeException: PKIX path building failed` dù property đã set đúng, cho thấy hành
 * vi cache SSLContext toàn JVM này ảnh hưởng cả đường gọi Axis. Đã sửa: {@code WorkerApplication.main()}
 * gọi {@code SimPkiSslBootstrap.applyIfConfigured()} (đọc biến môi trường THẬT trực tiếp qua
 * {@code System.getenv}, KHÔNG qua Spring Environment vì context chưa tồn tại) NGAY TRƯỚC
 * {@code SpringApplication.run(...)} — chắc chắn sớm hơn MỌI bean, kể cả {@code WorkerHttpClient}.
 * Đã THỰC NGHIỆM xác nhận: set qua {@code -Djavax.net.ssl.trustStore=...} (tương đương thời điểm)
 * → gọi MSSP thật thành công tới tận {@code UNAUTHORIZED_ACCESS} (không còn lỗi PKIX). Phương thức
 * {@link #ensureSslConfigured()} ở đây GIỮ LẠI làm lưới an toàn thứ 2 (phòng khi bootstrap sớm không
 * chạy được vì lý do nào đó, vd thiếu file lúc khởi động rồi mới xuất hiện) — không phải đường chính.</p>
 */
@Slf4j
public class VgcaMsspSignatureClient implements SimPkiGatewayClient {

    static final AtomicBoolean SSL_CONFIGURED = new AtomicBoolean(false);

    /** Đánh dấu SSL đã được thiết lập BỞI {@code SimPkiSslBootstrap} (lúc {@code main()}, trước khi
     *  Spring tạo bean nào) — {@link #ensureSslConfigured()} sẽ bỏ qua, không set lại lần nữa. */
    static void markSslConfiguredExternally() {
        SSL_CONFIGURED.set(true);
    }

    /** 2 URI mô tả dịch vụ phụ trợ — HẰNG SỐ GIAO THỨC ETSI/MSSP cố định (không phải cấu hình môi
     *  trường/triển khai), giữ nguyên như tài liệu chuẩn + sample SDK. */
    private static final String CERTIFICATE_URL = "http://mobile-id.vn/MSSP/v1.0.0#signingCertificate";
    private static final String VALIDATION_URL = "http://mobile-id.vn/MSSP/v1.0.0#signatureValidation";

    private final SimPkiProperties props;

    public VgcaMsspSignatureClient(SimPkiProperties props) {
        this.props = props;
    }

    @Override
    public SimPkiSignOutcome sign(SimPkiSignPayload payload) {
        if (payload == null || isBlank(payload.getMsisdn()) || isBlank(payload.getHashBase64())) {
            return SimPkiSignOutcome.failed("INVALID_PAYLOAD", "payload thiếu msisdn/hashBase64");
        }
        if (isBlank(props.getApId()) || isBlank(props.getApPassword())) {
            return SimPkiSignOutcome.failed("MISSING_AP_CREDENTIAL",
                "worker.tasks.simpki.ap-id/ap-password chưa cấu hình (do Cục CTS-BMTT cấp)");
        }
        if (isBlank(props.getTrustStorePath()) || isBlank(props.getKeyStorePath()) || isBlank(props.getKeyStorePassword())) {
            return SimPkiSignOutcome.failed("MISSING_KEYSTORE_CONFIG",
                "worker.tasks.simpki.trust-store-path/key-store-path/key-store-password chưa cấu hình đầy đủ");
        }
        if (!new File(props.getTrustStorePath()).isFile() || !new File(props.getKeyStorePath()).isFile()) {
            return SimPkiSignOutcome.failed("KEYSTORE_FILE_NOT_FOUND",
                "Không tìm thấy file trust-store-path/key-store-path trên đĩa (kiểm tra đường dẫn cấu hình)");
        }

        byte[] hashData;
        try {
            hashData = Base64.getDecoder().decode(payload.getHashBase64());
        } catch (IllegalArgumentException e) {
            return SimPkiSignOutcome.failed("INVALID_HASH_BASE64", e.getMessage());
        }

        String verificationCode = VerificationCodeCalculator.calculate(hashData);
        String displayMessage = isBlank(payload.getDisplayMessage())
            ? props.getDisplayMessageTemplate() : payload.getDisplayMessage();

        try {
            ensureSslConfigured();
            return callSoap(hashData, payload.getMsisdn(), displayMessage, verificationCode);
        } catch (Throwable sdkError) {
            // Bọc Throwable (không chỉ Exception): thiếu dependency transitive Axis/Castor có thể
            // ném LinkageError/NoClassDefFoundError — KHÔNG được làm sập Worker (cùng nguyên tắc đã
            // áp dụng ở VpcpGuiNhanQuocGiaGatewayClient).
            log.error("simpki.sign: lỗi gọi webservice MSSP (msisdn={}, kiểm tra endpoint/AP credential/"
                + "keystore/dependency Axis — xem docs/17-checklist mục rủi ro): {}",
                maskMsisdn(payload.getMsisdn()), sdkError.toString(), sdkError);
            return SimPkiSignOutcome.failed("SDK_ERROR", String.valueOf(sdkError.getMessage()));
        }
    }

    /**
     * Thiết lập SSL JVM global (mutual TLS Client Authentication bắt buộc bởi MSSP) — CHỈ 1 LẦN cho
     * cả JVM (idempotent, guard bằng {@link AtomicBoolean}), vì đây là cấu hình TOÀN CỤC (xem cảnh
     * báo javadoc lớp). Không cấu hình lại mỗi lần ký (JvmSsl.setSSL rẻ nhưng vô nghĩa nếu gọi lại
     * với cùng giá trị — giữ 1 lần cho rõ ràng log/ý định).
     */
    private void ensureSslConfigured() {
        if (SSL_CONFIGURED.compareAndSet(false, true)) {
            log.warn("simpki.sign: thiết lập javax.net.ssl.keyStore/trustStore TOÀN CỤC cho tiến trình "
                + "Worker (yêu cầu bắt buộc của SDK MSSP Axis 1.4) — ẢNH HƯỞNG mọi kết nối HTTPS khác "
                + "của Worker instance này (WorkerHttpClient...). Nếu Worker instance này còn xử lý "
                + "task khác cần client certificate HTTPS khác, xem docs/17-checklist mục rủi ro "
                + "(khuyến nghị tách instance riêng).");
            JvmSsl.setSSL(props.getTrustStorePath(), props.getTrustStorePassword(),
                props.getKeyStorePath(), props.getKeyStorePassword(), props.getKeyStoreType());
        }
    }

    private SimPkiSignOutcome callSoap(byte[] hashData, String msisdn, String displayMessage,
            String verificationCode) throws Exception {
        MSS_SignatureServiceLocator locator = new MSS_SignatureServiceLocator();
        URL signatureUrl = new URL(props.getSignatureUrl());

        String apTransId = buildApTransId();
        DTBS dtbs = new DTBS(hashData, DTBS.ENCODING_BASE64, DTBS.MIME_STREAM);

        MSS_SignatureReqType sigReq = new MSS_SignatureReqType();
        fillMatStuff(sigReq, apTransId);

        MobileUser mobileUser = new MobileUser();
        mobileUser.setMSISDN(msisdn);
        sigReq.setMobileUser(mobileUser);

        sigReq.setDataToBeSigned(dtbs.toDataToBeSigned());

        if (displayMessage != null && !displayMessage.isBlank()) {
            DataToBeDisplayed dataToBeDisplayed = new DataToBeDisplayed();
            dataToBeDisplayed.set_value(displayMessage);
            sigReq.setDataToBeDisplayed(dataToBeDisplayed);
        }

        SignatureProfile signatureProfile = new SignatureProfile();
        signatureProfile.setMssURI(new URI(props.getSignatureProfile()));
        sigReq.setSignatureProfile(signatureProfile);

        MSS_Format mssFormat = new MSS_Format();
        mssFormat.setMssURI(new URI(props.getMssFormat()));
        sigReq.setMSS_Format(mssFormat);

        sigReq.setMessagingMode(MessagingModeType.synch);
        sigReq.setTimeOut(new PositiveInteger(String.valueOf(Math.max(1, props.getTimeoutSeconds()))));

        AdditionalServices services = new AdditionalServices();
        Description certDescription = new Description();
        certDescription.setMssURI(new URI(CERTIFICATE_URL));
        Service certService = new Service();
        certService.setDescription(certDescription);
        services.addService(certService);

        Description validationDescription = new Description();
        validationDescription.setMssURI(new URI(VALIDATION_URL));
        Service validationService = new Service();
        validationService.setDescription(validationDescription);
        services.addService(validationService);
        sigReq.setAdditionalServices(services.getService());

        Stub port = (MSS_SignatureBindingStub) locator.getMSS_SignaturePort(signatureUrl);
        long timeoutMs = sigReq.getTimeOut().longValue() * 1000L + 1000L;
        port.setTimeout((int) timeoutMs);
        if (port._getCall() == null) {
            port._createCall();
        }

        MSS_SignatureRespType sigResp = ((MSS_SignatureBindingStub) port).MSS_Signature(sigReq);

        long statusCode = sigResp.getStatus().getStatusCode().getValue().longValue();
        String transId = String.valueOf(sigResp.getMSSP_TransID());
        byte[] signature = sigResp.getMSS_Signature() == null ? null : sigResp.getMSS_Signature().getBase64Signature();

        if (signature == null || signature.length == 0) {
            return SimPkiSignOutcome.failed("EMPTY_SIGNATURE",
                "MSSP trả về statusCode=" + statusCode + " nhưng không có chữ ký (giao dịch có thể bị "
                    + "từ chối/hết hạn trên điện thoại người dùng)");
        }
        return SimPkiSignOutcome.ok(Base64.getEncoder().encodeToString(signature), transId, verificationCode, statusCode);
    }

    /**
     * apTransId đổi mỗi 15 phút (cùng thuật toán {@code ExtSignature.sign()} gốc — comment vendor
     * gốc: "de 15 phut thay doi 1 lan de cache PIN") — đây là THUẬT TOÁN giao thức, không phải giá
     * trị môi trường, chỉ cho phép cấu hình phần PREFIX ({@code worker.tasks.simpki.apTransIdPrefix}
     * không tồn tại — giữ literal "VGCA" đúng mẫu vendor vì đây là quy ước của chính hệ thống VGCA,
     * không phải thứ mỗi môi trường triển khai cần đổi khác nhau).
     */
    private String buildApTransId() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
        Calendar now = Calendar.getInstance();
        int quarterHour = now.get(Calendar.MINUTE) / 15;
        return "VGCA" + dateFormat.format(now.getTime()) + quarterHour;
    }

    private void fillMatStuff(MessageAbstractType mat, String apTransId) throws URI.MalformedURIException {
        mat.setMajorVersion(BigInteger.valueOf(1L));
        mat.setMinorVersion(BigInteger.valueOf(1L));

        AP_Info apInfo = new AP_Info();
        apInfo.setAP_ID(new URI(props.getApId()));
        apInfo.setAP_PWD(props.getApPassword());
        apInfo.setAP_TransID(new NCName(apTransId));
        apInfo.setInstant(Calendar.getInstance());
        mat.setAP_Info(apInfo);

        MSSP_Info msspInfo = new MSSP_Info();
        MSSP_ID msspId = new MSSP_ID();
        msspId.setURI(new URI(props.getMsspId()));
        msspInfo.setMSSP_ID(msspId);
        mat.setMSSP_Info(msspInfo);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 4) {
            return "***";
        }
        return "***" + msisdn.substring(msisdn.length() - 3);
    }
}
