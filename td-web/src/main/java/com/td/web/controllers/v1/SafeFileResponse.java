package com.td.web.controllers.v1;

import org.springframework.http.MediaType;

import java.util.Locale;
import java.util.Set;

/**
 * 28/10/2026 (rà soát bảo mật): tiện ích dùng chung cho các endpoint trả NỘI DUNG TỆP người dùng tải lên.
 *
 * <ul>
 *   <li>MIME lưu trong {@code file_metadata} là giá trị CLIENT tự khai lúc upload (không kiểm tra) — tệp
 *   {@code .html}/{@code .svg} khai {@code text/html}/{@code image/svg+xml} rồi mở qua
 *   {@code /api/v1/files/{d}/{f}/view} (inline) sẽ CHẠY SCRIPT trên origin của API (stored XSS, đọc được
 *   token nếu FE và API chung origin sau reverse proxy). {@link #mediaType} hạ các loại có thể chạy script về
 *   {@code application/octet-stream}; giá trị MIME hỏng cũng về octet-stream thay vì ném lỗi 500.</li>
 *   <li>{@link #headerFileName}: tên tệp bị nối thẳng vào {@code filename="..."} — dấu {@code "}/{@code \}/
 *   xuống dòng làm vỡ header; thay bằng {@code _} (giữ nguyên định dạng header cũ để FE vẫn đọc tên tệp).</li>
 * </ul>
 */
final class SafeFileResponse {

    private static final Set<String> ACTIVE_CONTENT_TYPES = Set.of(
        "text/html", "application/xhtml+xml", "image/svg+xml", "text/xml", "application/xml",
        "text/javascript", "application/javascript", "application/x-javascript", "text/ecmascript",
        "application/ecmascript", "text/xsl", "application/xslt+xml", "multipart/x-mixed-replace");

    private SafeFileResponse() {
    }

    static MediaType mediaType(String storedMimeType) {
        if (storedMimeType == null || storedMimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        MediaType parsed;
        try {
            parsed = MediaType.parseMediaType(storedMimeType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String base = (parsed.getType() + "/" + parsed.getSubtype()).toLowerCase(Locale.ROOT);
        if (ACTIVE_CONTENT_TYPES.contains(base) || base.endsWith("+xml")) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return parsed;
    }

    static String headerFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.replaceAll("[\"\\\\\\r\\n\\u0000-\\u001f]", "_");
    }
}
