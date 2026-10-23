package com.td.application.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 17/10/2026 — mô hình truy vấn TRUNG LẬP (không phụ thuộc Elasticsearch) cho tìm kiếm nâng cao đọc từ index
 * tìm kiếm (docs/analysis/27). Điều kiện tham chiếu các trường CHUẨN HOÁ do td-woker sinh
 * ({@code SearchDocumentMapper#addSearchFields}):
 * <ul>
 *   <li>{@link Source#DYN}: khoá trong {@code dynamic_fields}; {@link Source#COL}: cột thật của bảng.</li>
 *   <li>{@link Terms}: khớp 1 trong các giá trị (so trên token đã chữ thường + tách {@code ;}/{@code ,}) — thay cho
 *   so bằng / {@code @>} / "member" của SQL.</li>
 *   <li>{@link DateRange}: khoảng ngày (bao gồm 2 đầu), null = không giới hạn đầu đó.</li>
 * </ul>
 */
public final class AdvancedSearchQuery {

    public enum Source { DYN, COL }

    public enum SortField { CREATED_ON, LAST_MODIFIED_ON, PROMULGATION_DATE, DOCUMENT_NUMBER }

    public sealed interface Condition permits Terms, DateRange, Exists, AnyOf, AllOf, Not, Match {
    }

    public record Terms(Source source, String key, List<String> values) implements Condition {
    }

    public record DateRange(Source source, String key, LocalDate from, LocalDate to) implements Condition {
    }

    /** Trường có giá trị khác rỗng. */
    public record Exists(Source source, String key) implements Condition {
    }

    public record AnyOf(List<Condition> conditions) implements Condition {
    }

    public record AllOf(List<Condition> conditions) implements Condition {
    }

    public record Not(Condition condition) implements Condition {
    }

    /** Trường chữ (kw.<key>) chứa mọi từ của {@code text} (bỏ dấu, từ cuối có thể gõ dở) — thay cho LIKE "%x%" theo từng trường. */
    public record Match(String key, String text) implements Condition {
    }

    private final List<Condition> filters = new ArrayList<>();
    private String keyword;
    private List<String> keywordFields = List.of();
    private final List<SortField> sort = new ArrayList<>();

    public AdvancedSearchQuery filter(Condition condition) {
        if (condition != null) {
            filters.add(condition);
        }
        return this;
    }

    /** Từ khoá tìm trên các khoá {@code keywordFields} (rỗng = tìm trên toàn văn bản). */
    public AdvancedSearchQuery keyword(String keyword, List<String> keywordFields) {
        this.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        this.keywordFields = keywordFields == null ? List.of() : List.copyOf(keywordFields);
        return this;
    }

    public AdvancedSearchQuery sortDesc(SortField... fields) {
        sort.addAll(List.of(fields));
        return this;
    }

    public List<Condition> filters() {
        return List.copyOf(filters);
    }

    public String keyword() {
        return keyword;
    }

    public List<String> keywordFields() {
        return keywordFields;
    }

    public List<SortField> sort() {
        return List.copyOf(sort);
    }

    // ---- tiện ích dựng điều kiện ----

    public static Terms dyn(String key, String... values) {
        return new Terms(Source.DYN, key, List.of(values));
    }

    public static Terms dyn(String key, List<String> values) {
        return new Terms(Source.DYN, key, List.copyOf(values));
    }

    public static Terms col(String key, String... values) {
        return new Terms(Source.COL, key, List.of(values));
    }

    public static AnyOf anyOf(List<? extends Condition> conditions) {
        return new AnyOf(List.copyOf(conditions));
    }
}
