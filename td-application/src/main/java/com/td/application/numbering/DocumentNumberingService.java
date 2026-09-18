package com.td.application.numbering;

import com.td.domain.numbering.NumberingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BL-104 (Document Numbering Service): sinh số văn bản tự động theo {@code documentType}, tăng
 * dần theo năm (và theo {@code scopeKey} — vd. đơn vị/tổ chức — khi cần, dùng cho số đi).
 * Document type không có cấu hình (hoặc bị tắt) → không sinh số, trả về rỗng (không lỗi), để
 * không chặn việc tạo document thuộc loại chưa cấu hình numbering.
 */
@Service
@RequiredArgsConstructor
public class DocumentNumberingService {

    public static final String DEFAULT_SCOPE = "_";

    private static final Pattern SEQUENCE_PLACEHOLDER = Pattern.compile("\\{seq(?::(\\d+))?}");

    private final NumberingConfigRepository numberingConfigRepository;
    private final NumberingCounterRepository numberingCounterRepository;

    public Optional<String> generateNumber(String documentType) {
        return generateNumber(documentType, DEFAULT_SCOPE);
    }

    public Optional<String> generateNumber(String documentType, String scopeKey) {
        if (documentType == null || documentType.isBlank()) {
            return Optional.empty();
        }

        String normalizedType = documentType.trim().toLowerCase();
        String effectiveScope = (scopeKey == null || scopeKey.isBlank()) ? DEFAULT_SCOPE : scopeKey.trim();

        return numberingConfigRepository.findByDocumentType(normalizedType)
            .filter(NumberingConfig::isActive)
            .map(config -> {
                int year = LocalDate.now().getYear();
                long sequence = numberingCounterRepository.incrementAndGet(normalizedType, year, effectiveScope);
                return formatNumber(config.getFormat(), sequence, year);
            });
    }

    private String formatNumber(String format, long sequence, int year) {
        String withSequence = replaceSequencePlaceholder(format, sequence);
        return withSequence.replace("{year}", String.valueOf(year));
    }

    private String replaceSequencePlaceholder(String format, long sequence) {
        Matcher matcher = SEQUENCE_PLACEHOLDER.matcher(format);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String widthGroup = matcher.group(1);
            String replacement = widthGroup == null
                ? String.valueOf(sequence)
                : String.format("%0" + Integer.parseInt(widthGroup) + "d", sequence);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
