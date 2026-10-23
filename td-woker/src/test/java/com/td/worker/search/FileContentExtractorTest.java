package com.td.worker.search;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 17/10/2026 — trích chữ bằng Tika (không OCR). */
class FileContentExtractorTest {

    private FileContentExtractor extractor;

    private FileContentExtractor extractor(SearchSyncProperties.FileExtract config) {
        extractor = new FileContentExtractor(config);
        return extractor;
    }

    private static SearchSyncProperties.FileExtract config() {
        SearchSyncProperties.FileExtract config = new SearchSyncProperties.FileExtract();
        config.setEnabled(true);
        return config;
    }

    @AfterEach
    void close() {
        if (extractor != null) {
            extractor.close();
        }
    }

    @Test
    void docx_shouldExtractVietnameseText() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Quyết định về việc triển khai phần mềm quản lý văn bản");
            doc.write(out);
        }

        var result = extractor(config()).extract(new ByteArrayInputStream(out.toByteArray()), "qd.docx", null, out.size());

        assertThat(result.status()).isEqualTo(FileContentExtractor.STATUS_DONE);
        assertThat(result.content()).contains("Quyết định về việc triển khai phần mềm");
        assertThat(result.contentType()).contains("wordprocessingml");
    }

    @Test
    void pdfWithTextLayer_shouldExtractWithoutOcr() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 700);
                stream.showText("Cong van so 12/UBND-VP ve viec bao cao");
                stream.endText();
            }
            pdf.save(out);
        }

        var result = extractor(config()).extract(new ByteArrayInputStream(out.toByteArray()), "cv.pdf", "application/pdf", out.size());

        assertThat(result.status()).isEqualTo(FileContentExtractor.STATUS_DONE);
        assertThat(result.content()).contains("Cong van so 12/UBND-VP");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void tooLarge_shouldBeSkippedWithoutReadingAll() throws Exception {
        SearchSyncProperties.FileExtract config = config();
        config.setMaxFileBytes(10);

        var declared = extractor(config).extract(new ByteArrayInputStream(new byte[5]), "a.txt", null, 100);
        var actual = extractor.extract(new ByteArrayInputStream(new byte[50]), "a.txt", null, 0);

        assertThat(declared.status()).isEqualTo(FileContentExtractor.STATUS_SKIPPED);
        assertThat(actual.status()).isEqualTo(FileContentExtractor.STATUS_SKIPPED);
    }

    @Test
    void skippedContentType_shouldNotParse() throws Exception {
        SearchSyncProperties.FileExtract config = config();
        config.setSkipContentTypes(List.of("text/"));

        var result = extractor(config).extract(
            new ByteArrayInputStream("noi dung".getBytes(StandardCharsets.UTF_8)), "a.txt", null, 8);

        assertThat(result.status()).isEqualTo(FileContentExtractor.STATUS_SKIPPED);
        assertThat(result.error()).contains("text/plain");
    }

    @Test
    void blankFile_shouldBeEmpty() throws Exception {
        var result = extractor(config()).extract(
            new ByteArrayInputStream("   \n  ".getBytes(StandardCharsets.UTF_8)), "rong.txt", null, 6);

        assertThat(result.status()).isEqualTo(FileContentExtractor.STATUS_EMPTY);
        assertThat(result.error()).as("không có lớp chữ → ghi chú bản scan").contains("bản scan");
    }

    // Không OCR: ảnh bị bỏ qua (mặc định skip-content-types có image/).
    @Test
    void image_shouldBeSkippedWithoutOcr() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52};

        var result = extractor(config()).extract(new ByteArrayInputStream(png), "scan.png", "image/png", png.length);

        assertThat(result.status()).isEqualTo(FileContentExtractor.STATUS_SKIPPED);
        assertThat(result.contentType()).startsWith("image/");
    }

    @Test
    void longText_shouldBeTruncatedAtLimit() throws Exception {
        SearchSyncProperties.FileExtract config = config();
        config.setMaxStoredChars(20);

        var result = extractor(config).extract(new ByteArrayInputStream(
            "abcdefghij klmnopqrst uvwxyz 0123456789".getBytes(StandardCharsets.UTF_8)), "dai.txt", null, 40);

        assertThat(result.truncated()).isTrue();
        assertThat(result.content().length()).isLessThanOrEqualTo(20);
    }

    @Test
    void normalize_shouldCollapseWhitespaceKeepParagraphs() {
        assertThat(FileContentExtractor.normalize("a \t b\r\n\n\n\n c  ")).isEqualTo("a b\n\nc");
    }
}
