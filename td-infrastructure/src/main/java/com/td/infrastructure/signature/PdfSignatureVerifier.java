package com.td.infrastructure.signature;

import com.td.application.documents.ChuKySoDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;

import java.io.IOException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): xác thực chữ ký số nhúng trong file PDF (signature
 * dictionary {@code /ByteRange} + {@code /Contents}), đúng cơ chế thật của
 * {@code TDAttachmentSignVerify.java} (Domino thật dùng {@code itextpdf} + {@code bouncycastle});
 * ở đây thay {@code itextpdf} (AGPL) bằng Apache PDFBox (Apache-2.0) để đọc PDF, giữ nguyên
 * BouncyCastle để phân tích/thẩm định chữ ký CMS/PKCS#7 — cùng 1 họ thư viện, cùng cơ chế mật mã.
 *
 * <p><b>Giới hạn cố ý (đúng "Rủi ro" của BL-221)</b>: chỉ kiểm tra chữ ký có khớp toán học với nội
 * dung + khoá công khai trên certificate hay không (toàn vẹn dữ liệu) — KHÔNG kiểm tra certificate
 * người ký có được CA tỉnh/LGSP cấp và còn hiệu lực hay không (cần trust store CA thật — tích hợp
 * bên thứ ba, ngoài phạm vi BL-221).
 */
public final class PdfSignatureVerifier {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String SIGNED_ON_PATTERN = "dd/MM/yyyy HH:mm:ss";

    private PdfSignatureVerifier() {
    }

    /** @return 1 dòng cho mỗi chữ ký số tìm thấy trong file PDF (rỗng nếu không có chữ ký nào). */
    public static List<ChuKySoDto> verify(byte[] pdfBytes) throws IOException {
        List<ChuKySoDto> results = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            for (PDSignature signature : document.getSignatureDictionaries()) {
                results.add(verifySignature(pdfBytes, signature));
            }
        }
        return results;
    }

    // BouncyCastle's Store<X509CertificateHolder>/SignerId API predates Java generics (raw types
    // internally) — unchecked conversion is unavoidable here, not a real type-safety risk.
    @SuppressWarnings("unchecked")
    private static ChuKySoDto verifySignature(byte[] pdfBytes, PDSignature signature) {
        try {
            byte[] signedContent = signature.getSignedContent(pdfBytes);
            byte[] contents = signature.getContents(pdfBytes);

            CMSSignedData signedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), contents);
            SignerInformation signerInfo = signedData.getSignerInfos().getSigners().iterator().next();

            Store<X509CertificateHolder> certificateStore = signedData.getCertificates();
            Collection<X509CertificateHolder> matches = certificateStore.getMatches(signerInfo.getSID());
            X509CertificateHolder certificateHolder = matches.iterator().next();

            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(certificateHolder);
            boolean integrityValid = signerInfo.verify(verifier);

            int[] byteRange = signature.getByteRange();
            boolean coversWholeDocument = byteRange != null && byteRange.length == 4
                && (byteRange[2] + byteRange[3]) == pdfBytes.length;

            String signedOn = signature.getSignDate() != null
                ? new SimpleDateFormat(SIGNED_ON_PATTERN).format(signature.getSignDate().getTime())
                : "";

            return ChuKySoDto.builder()
                .name(extractCommonName(certificateHolder))
                .time(signedOn)
                .integrityValid(integrityValid)
                .coversWholeDocument(coversWholeDocument)
                .build();
        } catch (Exception ex) {
            return ChuKySoDto.builder()
                .name("")
                .time("")
                .integrityValid(false)
                .coversWholeDocument(false)
                .build();
        }
    }

    private static String extractCommonName(X509CertificateHolder certificateHolder) {
        var rdns = certificateHolder.getSubject().getRDNs(BCStyle.CN);
        if (rdns.length == 0) {
            return certificateHolder.getSubject().toString();
        }
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }
}
