package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.common.models.Result;
import com.td.application.simpki.LocateSimPkiSignaturesRequest;
import com.td.application.simpki.LocateSimPkiSignaturesUseCase;
import com.td.application.simpki.SimPkiSignatureLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class LocateSimPkiSignaturesUseCaseImpl implements LocateSimPkiSignaturesUseCase {

    /** Khớp giới hạn độ dài 1 regex phía TDSimPki ({@code MAX_LOCATOR_PATTERN_LENGTH}). */
    static final int MAX_REGEX_LENGTH = 256;
    /**
     * (30/09/2026) "Vị trí phù hợp" khi tìm theo tên người ký: tên phải đứng riêng 1 dòng VÀ phía trên có
     * ≥ 36pt trống (≈ 2-3 dòng trắng cỡ chữ 13) trong dải rộng 150pt (≈ bề rộng tem chữ ký) — khối chữ ký
     * "CHỨC VỤ … khoảng trống … Họ tên" đạt, tên nằm cuối 1 câu nội dung thì không.
     */
    static final float DEFAULT_MIN_CLEARANCE_ABOVE_PT = 36f;
    static final float DEFAULT_CLEARANCE_WIDTH_PT = 150f;
    private static final int MAX_CUSTOM_LOCATORS = 9; // + 1 regex tên người ký = 10 (giới hạn TDSimPki)
    private static final String REGEX_METACHARS = "\\^$.|?*+()[]{}";
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");
    // Ranh giới "từ" Unicode tường minh (chữ/dấu kết hợp/số) - KHÔNG dùng \b: \b trượt khi tên kết thúc
    // bằng dấu câu ("A.B (C)") và không coi dấu kết hợp (NFD) là một phần của từ.
    private static final String WORD_START = "(?<![\\p{L}\\p{M}\\p{N}])";
    private static final String WORD_END = "(?![\\p{L}\\p{M}\\p{N}])";

    private final TdSimPkiClient client;

    public LocateSimPkiSignaturesUseCaseImpl(TdSimPkiClient client) {
        this.client = client;
    }

    @Override
    public Result<List<SimPkiSignatureLocation>> execute(LocateSimPkiSignaturesRequest request) {
        if (request == null || request.getPdfBytes() == null || request.getPdfBytes().length == 0) {
            return Result.failure("Thieu noi dung PDF de dinh vi");
        }
        List<String> regexes = new ArrayList<>();
        String signerRegex = buildSignerNameRegex(request.getSignerName());
        if (signerRegex != null) {
            regexes.add(signerRegex);
        }
        if (request.getSignatureLocators() != null) {
            request.getSignatureLocators().stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_CUSTOM_LOCATORS)
                .forEach(regexes::add);
        }
        if (regexes.isEmpty()) {
            return Result.failure("Can signerName hoac signatureLocators de dinh vi chu ky");
        }
        String occurrence = request.getOccurrence();
        if (occurrence == null || occurrence.isBlank()) {
            occurrence = signerRegex != null ? "ALL_PAGES" : "FIRST";
        }
        try {
            boolean bySignerName = signerRegex != null;
            Float minClearance = request.getMinClearanceAbovePt() != null
                ? request.getMinClearanceAbovePt()
                : (bySignerName ? DEFAULT_MIN_CLEARANCE_ABOVE_PT : null);
            JsonNode json = client.locateSignatures(request.getPdfBytes(), request.getFileName(), regexes, occurrence,
                minClearance, bySignerName, bySignerName ? DEFAULT_CLEARANCE_WIDTH_PT : null);
            List<SimPkiSignatureLocation> locations = new ArrayList<>();
            if (json != null && json.isArray()) {
                for (JsonNode node : json) {
                    locations.add(new SimPkiSignatureLocation(
                        node.path("regex").asText(null),
                        node.path("matchedText").asText(null),
                        node.path("page").asInt(),
                        (float) node.path("x").asDouble(),
                        (float) node.path("y").asDouble(),
                        (float) node.path("width").asDouble(),
                        (float) node.path("height").asDouble(),
                        node.path("clearanceAbove").isNumber() ? (float) node.path("clearanceAbove").asDouble() : null,
                        node.path("aloneOnLine").isBoolean() ? node.path("aloneOnLine").asBoolean() : null));
                }
            }
            return Result.success(locations);
        } catch (TdSimPkiException ex) {
            log.warn("Dinh vi chu ky qua TDSimPki that bai: {}", ex.getMessage());
            return Result.failure(ex.getMessage());
        }
    }

    /**
     * Regex tìm TÊN NGƯỜI KÝ: mỗi từ được escape (tên là dữ liệu, không phải regex), giữa các từ cho
     * phép nhiều khoảng trắng/xuống dòng ({@code \s+} — TDSimPki đã chuẩn hoá whitespace), bao bởi ranh
     * giới từ Unicode tường minh ({@link #WORD_START}/{@link #WORD_END} — "Văn An" không khớp nhầm trong
     * "Văn Anh", đúng cả chữ có dấu tiếng Việt). Có thêm nhánh IN HOA (một số mẫu văn bản in
     * tên người ký toàn chữ hoa). Chuẩn hoá NFC vì text PDF (Word/OnlyOffice xuất) dùng dạng dựng sẵn.
     * Capture group 1 = đúng phần tên (TDSimPki dùng group 1 khi có, xem docs/25 phía TDSimPki).
     *
     * @return {@code null} nếu tên rỗng hoặc regex vượt giới hạn độ dài của TDSimPki.
     */
    static String buildSignerNameRegex(String signerName) {
        if (signerName == null || signerName.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(signerName.trim(), Normalizer.Form.NFC);
        String[] words = normalized.split("\\s+");
        String exact = joinEscaped(words);
        String upper = joinEscaped(normalized.toUpperCase(VIETNAMESE).split("\\s+"));
        String withUpper = WORD_START + "(" + (upper.equals(exact) ? exact : exact + "|" + upper) + ")" + WORD_END;
        if (withUpper.length() <= MAX_REGEX_LENGTH) {
            return withUpper;
        }
        String exactOnly = WORD_START + "(" + exact + ")" + WORD_END;
        return exactOnly.length() <= MAX_REGEX_LENGTH ? exactOnly : null;
    }

    private static String joinEscaped(String[] words) {
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\\s+");
            }
            for (int i = 0; i < word.length(); i++) {
                char ch = word.charAt(i);
                if (REGEX_METACHARS.indexOf(ch) >= 0) {
                    out.append('\\');
                }
                out.append(ch);
            }
        }
        return out.toString();
    }
}
