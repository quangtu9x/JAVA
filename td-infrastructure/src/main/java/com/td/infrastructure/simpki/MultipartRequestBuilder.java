package com.td.infrastructure.simpki;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dựng body {@code multipart/form-data} thủ công — {@code java.net.http.HttpClient} (JDK) không có
 * body publisher multipart sẵn, và td-infrastructure KHÔNG có {@code spring-web}/{@code RestTemplate}
 * (chỉ {@code td-web} mới có — xem javadoc {@link com.td.infrastructure.hosocv.HttpLgspTokenClient}),
 * nên phải tự dựng đúng chuẩn RFC 7578 tối giản (đủ cho field text + 1 tệp, không cần multi-file).
 */
final class MultipartRequestBuilder {
    private final String boundary = "TdSimPkiBoundary" + UUID.randomUUID();
    private final List<byte[]> parts = new ArrayList<>();

    String boundary() {
        return boundary;
    }

    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    MultipartRequestBuilder addFile(String fieldName, String fileName, String contentType, byte[] content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            parts.add(out.toByteArray());
            return this;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Field text (UTF-8) — dùng cho các tham số đi kèm tệp, vd {@code signatureLocators}. */
    MultipartRequestBuilder addField(String fieldName, String value) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            parts.add(out.toByteArray());
            return this;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    byte[] build() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] part : parts) {
                out.write(part);
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
