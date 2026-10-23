package com.td.worker.search;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 17/10/2026 — trích chữ từ tệp đính kèm bằng Apache Tika (PDF có lớp chữ, Word, Excel, PowerPoint, ODF, RTF, HTML,
 * TXT...) — docs/architecture/14-trich-noi-dung-tep.md.
 *
 * <p><b>KHÔNG OCR</b> (quyết định người dùng 17/10/2026: chưa dùng Tesseract, sẽ tích hợp OCR phù hợp sau): bộ đọc OCR
 * của Tika bị LOẠI hẳn khỏi parser và PDF chạy {@code NO_OCR} — kể cả khi máy có cài Tesseract. PDF scan → {@code EMPTY}
 * (ghi chú "có thể là bản scan" để sau này lọc ra OCR); ảnh → {@code SKIPPED} (bỏ qua theo {@code skipContentTypes}).</p>
 *
 * <ul>
 *   <li>Đọc tệp vào bộ nhớ CÓ GIỚI HẠN ({@code maxFileBytes}) — quá giới hạn → {@code SKIPPED}, không đọc tiếp.</li>
 *   <li>Mỗi tệp chạy trên luồng riêng có thời hạn ({@code fileTimeout}); quá hạn → lỗi, worker thử lại sau.</li>
 * </ul>
 */
@Slf4j
public class FileContentExtractor implements AutoCloseable {

    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";
    static final String EXTRACTOR = "tika-2.9.2";
    /** Ghi chú cho tệp không có lớp chữ — dùng để lọc ra khi tích hợp OCR sau này. */
    static final String NO_TEXT_LAYER_NOTE = "Không có lớp chữ (có thể là bản scan — cần OCR, chưa tích hợp)";

    /** Kết quả trích 1 tệp. */
    public record Result(String status, String content, boolean truncated, String contentType, String error) {
        static Result skipped(String contentType, String reason) {
            return new Result(STATUS_SKIPPED, null, false, contentType, reason);
        }
    }

    private final SearchSyncProperties.FileExtract config;
    private final Tika detector = new Tika();
    private final AutoDetectParser parser;
    private final ExecutorService executor;

    public FileContentExtractor(SearchSyncProperties.FileExtract config) {
        this.config = config;
        // Loại bộ đọc OCR (Tesseract) — không bao giờ OCR, kể cả khi máy có cài Tesseract.
        DefaultParser defaults = new DefaultParser(
            org.apache.tika.mime.MediaTypeRegistry.getDefaultRegistry(),
            new org.apache.tika.config.ServiceLoader(),
            List.of(TesseractOCRParser.class));
        this.parser = new AutoDetectParser(defaults);
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "file-extract-parse");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * @param input        luồng tệp (người gọi đóng)
     * @param declaredSize kích thước theo file_metadata (≤0 = không rõ)
     */
    public Result extract(InputStream input, String filename, String declaredContentType, long declaredSize) throws Exception {
        if (declaredSize > config.getMaxFileBytes()) {
            return Result.skipped(declaredContentType, "Tệp lớn hơn giới hạn " + config.getMaxFileBytes() + " byte");
        }
        byte[] bytes = readLimited(input, config.getMaxFileBytes());
        if (bytes == null) {
            return Result.skipped(declaredContentType, "Tệp lớn hơn giới hạn " + config.getMaxFileBytes() + " byte");
        }
        String contentType = detector.detect(bytes, filename);
        if (isSkippedType(contentType)) {
            return Result.skipped(contentType, "Loại tệp không trích nội dung: " + contentType);
        }
        Future<Result> future = executor.submit(() -> parse(bytes, filename, contentType));
        try {
            return future.get(config.getFileTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalStateException("Quá thời gian trích nội dung (" + config.getFileTimeout() + ")", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw cause instanceof Exception e ? e : new IllegalStateException(cause);
        }
    }

    private Result parse(byte[] bytes, String filename, String contentType) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(config.getMaxStoredChars());
        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        context.set(PDFParserConfig.class, pdfConfig);

        boolean truncated = false;
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            parser.parse(stream, handler, metadata, context);
        } catch (Exception ex) {
            if (!WriteLimitReachedException.isWriteLimitReached(ex)) {
                throw ex;
            }
            truncated = true;
        }
        String content = normalize(handler.toString());
        if (content.isBlank()) {
            return new Result(STATUS_EMPTY, null, false, contentType, NO_TEXT_LAYER_NOTE);
        }
        return new Result(STATUS_DONE, content, truncated, contentType, null);
    }

    private boolean isSkippedType(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return config.getSkipContentTypes() != null && config.getSkipContentTypes().stream()
            .filter(prefix -> prefix != null && !prefix.isBlank())
            .anyMatch(prefix -> type.startsWith(prefix.trim().toLowerCase(Locale.ROOT)));
    }

    /** Đọc tối đa {@code limit} byte; vượt → null. */
    static byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Gộp khoảng trắng thừa, giữ xuống dòng (đủ đọc, không phình index). */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(' ', ' ')
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
