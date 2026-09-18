package com.td.infrastructure.signature;

import com.td.application.documents.ChuKySoDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): kiểm tra thật (không mock mật mã) toàn bộ vòng
 * ký→xác thực: tự tạo 1 PDF, tự ký bằng chứng chỉ tự cấp (self-signed, sinh ngay trong test bằng
 * BouncyCastle — KHÔNG cần CA thật), rồi chạy {@link PdfSignatureVerifier} lên chính file đó để
 * xác nhận cơ chế toàn vẹn dữ liệu hoạt động đúng (khớp = hợp lệ, sửa nội dung sau khi ký = phát
 * hiện được, dù không xác thực chuỗi tin cậy CA — đúng giới hạn đã ghi trong {@link ChuKySoDto}).
 */
class PdfSignatureVerifierTest {

    @Test
    void verify_withGenuinelySignedPdf_shouldReportValidIntegrityAndSignerName() throws Exception {
        byte[] signedPdf = createSelfSignedPdf("NGUYEN VAN A");

        List<ChuKySoDto> results = PdfSignatureVerifier.verify(signedPdf);

        assertThat(results).hasSize(1);
        ChuKySoDto signature = results.get(0);
        assertThat(signature.isIntegrityValid()).isTrue();
        assertThat(signature.getName()).isEqualTo("NGUYEN VAN A");
        assertThat(signature.getTime()).isNotBlank();
    }

    @Test
    void verify_withoutAnySignature_shouldReturnEmptyList() throws Exception {
        byte[] plainPdf = createUnsignedPdf();

        List<ChuKySoDto> results = PdfSignatureVerifier.verify(plainPdf);

        assertThat(results).isEmpty();
    }

    @Test
    void verify_whenSignedContentTamperedAfterSigning_shouldReportInvalidIntegrity() throws Exception {
        byte[] signedPdf = createSelfSignedPdf("NGUYEN VAN A");
        byte[] tampered = tamperByteRangeContent(signedPdf);

        List<ChuKySoDto> results = PdfSignatureVerifier.verify(tampered);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isIntegrityValid()).isFalse();
    }

    private byte[] createUnsignedPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createSelfSignedPdf(String signerCommonName) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        X509Certificate certificate = selfSignedCertificate(signerCommonName, keyPair);

        byte[] baselinePdf = createUnsignedPdf();

        try (PDDocument document = PDDocument.load(baselinePdf);
             ByteArrayOutputStream signedOut = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(signerCommonName);
            signature.setSignDate(Calendar.getInstance());

            SignatureInterface signatureInterface = content -> {
                try {
                    return signDetachedCms(content, keyPair.getPrivate(), certificate);
                } catch (Exception ex) {
                    throw new java.io.IOException("Loi ky CMS trong test: " + ex.getMessage(), ex);
                }
            };

            document.addSignature(signature, signatureInterface);
            document.saveIncremental(signedOut);
            return signedOut.toByteArray();
        }
    }

    private byte[] signDetachedCms(InputStream content, PrivateKey privateKey, X509Certificate certificate) throws Exception {
        byte[] contentBytes = content.readAllBytes();

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().build())
            .build(contentSigner, certificate));
        generator.addCertificates(new JcaCertStore(List.of(certificate)));

        var signedData = generator.generate(new CMSProcessableByteArray(contentBytes), false);
        return signedData.getEncoded();
    }

    private X509Certificate selfSignedCertificate(String commonName, KeyPair keyPair) throws Exception {
        X500Name subject = new X500Name("CN=" + commonName + ", O=BL-221 Test");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder certificateHolder = certificateBuilder.build(signer);
        return new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certificateHolder);
    }

    // Sua 1 byte trong pham vi noi dung da ky (byte range) de mo phong "tai lieu bi thay doi sau
    // khi ky" - phai van la 1 PDF hop le doc duoc (chi doi 1 ky tu trong noi dung trang, khong pham
    // vao cau truc PDF/signature dictionary) de PdfSignatureVerifier van chay toi buoc verify().
    private byte[] tamperByteRangeContent(byte[] signedPdf) {
        byte[] copy = signedPdf.clone();
        String content = new String(copy, java.nio.charset.StandardCharsets.ISO_8859_1);
        int markerIndex = content.indexOf("/MediaBox");
        assertThat(markerIndex).isPositive();
        copy[markerIndex + 1] = 'X';
        return copy;
    }
}
