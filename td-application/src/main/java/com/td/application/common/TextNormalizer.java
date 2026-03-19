package com.td.application.common;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa văn bản tiếng Việt về Unicode dựng sẵn (NFC - Normalization Form Composed)
 * và loại bỏ nội dung nguy hiểm (XSS, script injection).
 *
 * <p>Lý do cần chuẩn hóa NFC:</p>
 * <ul>
 *   <li>Bộ gõ Unikey/VNI có thể tạo ra cùng ký tự tiếng Việt theo 2 cách:
 *     NFC (dựng sẵn, 1 code point) hoặc NFD (tổ hợp, nhiều code points)</li>
 *   <li>Dữ liệu không đồng nhất gây lỗi tìm kiếm và thống kê</li>
 *   <li>Chuẩn NFC là chuẩn chung được dùng rộng rãi nhất trong hệ thống Việt Nam</li>
 * </ul>
 */
public final class TextNormalizer {

    // ─── Script tag (có hoặc không có nội dung bên trong) ───
    private static final Pattern SCRIPT_WITH_BODY = Pattern.compile(
            "<\\s*script[^>]*>[\\s\\S]*?<\\s*/\\s*script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SCRIPT_OPEN_TAG = Pattern.compile(
            "<\\s*script[^>]*>",
            Pattern.CASE_INSENSITIVE);

    // ─── Các thẻ HTML nguy hiểm khác ───
    private static final Pattern DANGEROUS_TAGS = Pattern.compile(
            "<\\s*/?" +
            "(?:iframe|object|embed|applet|form|input|button|select|textarea" +
            "|link|style|meta|base|frame|frameset|svg|math|video|audio|source)" +
            "(?:\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE);

    // ─── Event handler attributes: onclick=, onload=, ... ───
    private static final Pattern EVENT_ATTRIBUTES = Pattern.compile(
            "(?:^|\\s)on\\w+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]*)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ─── javascript: và vbscript: scheme ───
    private static final Pattern JAVASCRIPT_SCHEME = Pattern.compile(
            "(?:href|src|action|formaction|data|xlink:href)\\s*=\\s*['\"]?\\s*(?:javascript|vbscript)\\s*:",
            Pattern.CASE_INSENSITIVE);

    // ─── data:text/html URI (có thể chứa HTML/JS) ───
    private static final Pattern DATA_URI_HTML = Pattern.compile(
            "(?:href|src)\\s*=\\s*['\"]?\\s*data\\s*:\\s*text/html",
            Pattern.CASE_INSENSITIVE);

    // ─── CSS expression() ───
    private static final Pattern CSS_EXPRESSION = Pattern.compile(
            "expression\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    // ─── CDATA section ───
    private static final Pattern CDATA_SECTION = Pattern.compile(
            "<!\\[CDATA\\[.*?]]>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // ─── HTML comments chứa executable code (IE conditional comments) ───
    private static final Pattern IE_CONDITIONAL = Pattern.compile(
            "<!--\\[if[\\s\\S]*?-->",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // ─── Null bytes ───
    private static final Pattern NULL_BYTES = Pattern.compile("\u0000");

    private TextNormalizer() {}

    // ─────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────

    /**
     * Chuẩn hóa chuỗi về Unicode NFC (dựng sẵn).
     * <p>Đảm bảo các ký tự tiếng Việt từ mọi bộ gõ (Unikey, VNI, VIQR...)
     * đều về cùng một biểu diễn binary, tránh sai sót khi tìm kiếm và thống kê.</p>
     *
     * @param input chuỗi đầu vào (có thể null)
     * @return chuỗi đã chuẩn hóa NFC, hoặc null nếu input là null
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return trimmed;
        return Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    }

    /**
     * Loại bỏ nội dung nguy hiểm XSS: script tags, event handlers,
     * javascript: URIs, CSS expression(), CDATA, null bytes.
     * <p>Giữ nguyên HTML thông thường (thẻ b, i, p, div, span...) nhưng loại bỏ
     * tất cả phần có thể thực thi code phía client.</p>
     *
     * @param input chuỗi đầu vào (có thể null)
     * @return chuỗi đã sanitize, hoặc null nếu input là null
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        if (input.isEmpty()) return input;

        String s = input;

        // 1. Loại bỏ null bytes (bypass trick)
        s = NULL_BYTES.matcher(s).replaceAll("");

        // 2. IE conditional comments
        s = IE_CONDITIONAL.matcher(s).replaceAll("");

        // 3. CDATA sections
        s = CDATA_SECTION.matcher(s).replaceAll("");

        // 4. <script>...</script> — xóa cả nội dung bên trong
        s = SCRIPT_WITH_BODY.matcher(s).replaceAll("");

        // 5. <script> còn sót (không có closing tag)
        s = SCRIPT_OPEN_TAG.matcher(s).replaceAll("");

        // 6. Các thẻ HTML nguy hiểm khác
        s = DANGEROUS_TAGS.matcher(s).replaceAll("");

        // 7. Event handler attributes (onclick=, onload=, onerror=, ...)
        s = EVENT_ATTRIBUTES.matcher(s).replaceAll("");

        // 8. javascript:/vbscript: scheme trong attributes
        s = JAVASCRIPT_SCHEME.matcher(s).replaceAll("href=\"\"");

        // 9. data:text/html URI
        s = DATA_URI_HTML.matcher(s).replaceAll("src=\"\"");

        // 10. CSS expression()
        s = CSS_EXPRESSION.matcher(s).replaceAll("expression-blocked(");

        return s;
    }

    /**
     * Kết hợp chuẩn hóa NFC và loại bỏ XSS trong một bước.
     * <p>Đây là hàm dùng chung để xử lý tất cả chuỗi đầu vào từ HTTP request.</p>
     *
     * @param input chuỗi đầu vào (có thể null)
     * @return chuỗi đã NFC normalize và sanitize XSS, hoặc null nếu input là null
     */
    public static String normalizeAndSanitize(String input) {
        if (input == null) return null;
        // Thứ tự: null-byte → NFC normalize → XSS sanitize
        return sanitize(normalize(input));
    }
}
