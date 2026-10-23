package com.td.application.search;

import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.search.AdvancedSearchQuery.AllOf;
import com.td.application.search.AdvancedSearchQuery.Condition;
import com.td.application.search.AdvancedSearchQuery.DateRange;
import com.td.application.search.AdvancedSearchQuery.Exists;
import com.td.application.search.AdvancedSearchQuery.Not;
import com.td.application.search.AdvancedSearchQuery.SortField;
import com.td.application.search.AdvancedSearchQuery.Source;
import com.td.application.search.AdvancedSearchQuery.Terms;
import com.td.application.vbdi.VbdiDocumentOutFilter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.td.application.search.AdvancedSearchQuery.anyOf;
import static com.td.application.search.AdvancedSearchQuery.col;
import static com.td.application.search.AdvancedSearchQuery.dyn;

/**
 * 17/10/2026 — chuyển CHÍNH bộ lọc SQL ({@link VbdenOrgViewFilter}, {@link VbdiDocumentOutFilter}) mà
 * {@code ViewQueryService} đã dựng từ request sang {@link AdvancedSearchQuery}: phần đọc tham số tìm kiếm dùng chung
 * với SQL nên 2 nguồn không lệch nhau. Đặc tả ngữ nghĩa SQL: docs/analysis/27 §6 (báo cáo đặc tả 17/10/2026).
 *
 * <p>Trả {@code null} khi bộ lọc dùng tính năng chưa hỗ trợ trên index (sắp theo field động, chỉ lấy bản ghi đã
 * xoá, {@code extraPredicateSql}...) — người gọi chạy SQL.</p>
 *
 * <p>Khác biệt có chủ đích so với SQL (ghi trong docs/analysis/27): so khớp KHÔNG phân biệt hoa/thường; giá trị
 * dạng {@code "a;b"} khớp theo từng phần tử ở mọi phép so; từ khoá tìm theo TỪ (bỏ dấu, đủ mọi từ, từ cuối được gõ
 * dở) thay vì 1 chuỗi con liền mạch.</p>
 */
public final class AdvancedSearchTranslator {

    /** Cột thật (không nằm trong dynamic_fields) mà bộ lọc SQL có thể tham chiếu theo tên. */
    private static final String STATUS_COLUMN = "status";

    private AdvancedSearchTranslator() {
    }

    /**
     * {@code findByVbdenOrgView} của documents (vbden) / duthao_documents / hosocv_documents.
     *
     * @param excludeRevoked chỉ vbden loại {@code is_revoke="1"} (các bảng khác SQL không lọc)
     */
    public static AdvancedSearchQuery fromOrgView(VbdenOrgViewFilter filter, String documentType, boolean excludeRevoked) {
        if (filter == null || filter.isDeletedOnly() || hasText(filter.getPrimarySortField())
                || hasText(filter.getSecondarySortField())) {
            return null;
        }
        AdvancedSearchQuery query = baseQuery(documentType);
        if (excludeRevoked && !filter.isIncludeRevoked()
                && (filter.getIncludeFieldValues() == null || !filter.getIncludeFieldValues().containsKey("is_revoke"))) {
            query.filter(new Not(dyn("is_revoke", "1")));
        }
        if (hasText(filter.getOrgValue())) {
            List<Condition> org = new ArrayList<>();
            names(filter.getOrgFieldNames()).forEach(f -> org.add(dyn(f, filter.getOrgValue().trim())));
            names(filter.getOrgListFieldNames()).forEach(f -> org.add(dyn(f, filter.getOrgValue().trim())));
            if (org.isEmpty()) {
                return null;
            }
            query.filter(anyOf(org));
        } else if (!names(filter.getOrgFieldNames()).isEmpty() || !names(filter.getOrgListFieldNames()).isEmpty()) {
            return null; // có khoá phạm vi mà không có giá trị → SQL trả rỗng; không đoán
        }
        eachValues(filter.getIncludeFieldValues(), (field, values) -> query.filter(termsFor(field, values)));
        eachValues(filter.getExcludeFieldValues(), (field, values) -> query.filter(new Not(termsFor(field, values))));
        eachValues(filter.getContainsAnyFieldValues(), (field, values) -> query.filter(dyn(field, values)));
        eachValues(filter.getExcludeMemberFieldValues(), (field, values) -> query.filter(new Not(dyn(field, values))));
        if (filter.getTextContainsFieldValues() != null && !filter.getTextContainsFieldValues().isEmpty()) {
            return null; // 19/10/2026: điều kiện "chứa chuỗi" chưa dịch sang index -> để SQL xử lý
        }
        dateRange(query, Source.DYN, "received_date", filter.getMinReceivedDate(), filter.getMaxReceivedDate());
        dateRange(query, Source.DYN, "deadline_date", filter.getMinDeadline(), filter.getMaxDeadline());
        dateRange(query, Source.DYN, "end_date", filter.getMinEndDate(), filter.getMaxEndDate());
        dateRange(query, Source.DYN, "storaged_date", filter.getMinStoragedDate(), filter.getMaxStoragedDate());
        dateRange(query, Source.COL, "created_on", filter.getMinCreatedOn(), filter.getMaxCreatedOn());
        if (hasText(filter.getRequireEmptyFieldName())) {
            query.filter(new Not(new Exists(Source.DYN, filter.getRequireEmptyFieldName())));
        }
        if (hasText(filter.getRequireNonEmptyFieldName())) {
            query.filter(new Exists(Source.DYN, filter.getRequireNonEmptyFieldName()));
        }
        if (filter.isRequireDeadlineOrDocumentReply()) {
            query.filter(anyOf(List.of(new Exists(Source.DYN, "deadline_date"), dyn("documentreply", "1"))));
        }
        query.keyword(filter.getKeyword(), filter.getKeywordColumns());
        return query.sortDesc(SortField.CREATED_ON, SortField.LAST_MODIFIED_ON);
    }

    /**
     * {@code vwCreator_DocumentOut_All} của vbdi_documents: trạng thái ISSUED + mã định danh khớp ĐÚNG 1 trong 4
     * trường; sắp ngày ban hành ↓, số ↓, sửa gần nhất ↓.
     */
    public static AdvancedSearchQuery fromVbdiIssuedByIdentifier(VbdiDocumentOutFilter filter, String status,
                                                                 List<String> identifierFields, String identifier) {
        if (!hasText(identifier) || (filter != null && (hasText(filter.getExtraPredicateSql())
                || hasText(filter.getDocumentRegisterId()) || hasText(filter.getExcludeMemberFieldName())))) {
            return null;
        }
        AdvancedSearchQuery query = baseQuery("vbdi");
        if (hasText(status)) {
            query.filter(col(STATUS_COLUMN, status));
        }
        List<Condition> org = new ArrayList<>();
        identifierFields.forEach(f -> org.add(dyn(f, identifier.trim())));
        query.filter(anyOf(org));
        if (filter != null) {
            eachValues(filter.getIncludeFieldValues(), (field, values) -> query.filter(termsFor(field, values)));
            eachValues(filter.getContainsAnyFieldValues(), (field, values) -> query.filter(dyn(field, values)));
            if (filter.getTextContainsFieldValues() != null && !filter.getTextContainsFieldValues().isEmpty()) {
                return null; // 19/10/2026: điều kiện "chứa chuỗi" chưa dịch sang index -> để SQL xử lý
            }
            dateRange(query, Source.DYN, "promulgation_date", filter.getPromulgationDateFrom(), filter.getPromulgationDateTo());
            query.keyword(filter.getKeyword(), filter.getKeywordColumns());
        }
        return query.sortDesc(SortField.PROMULGATION_DATE, SortField.DOCUMENT_NUMBER, SortField.LAST_MODIFIED_ON);
    }

    /** Điều kiện chung: chưa xoá + đúng document_type. */
    static AdvancedSearchQuery baseQuery(String documentType) {
        AdvancedSearchQuery query = new AdvancedSearchQuery();
        query.filter(new Not(new Exists(Source.COL, "deleted_on")));
        if (hasText(documentType)) {
            query.filter(col("document_type", documentType));
        }
        return query;
    }

    private static Terms termsFor(String field, List<String> values) {
        return STATUS_COLUMN.equalsIgnoreCase(field) ? new Terms(Source.COL, STATUS_COLUMN, List.copyOf(values)) : dyn(field, values);
    }

    private static void dateRange(AdvancedSearchQuery query, Source source, String key, LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            query.filter(new DateRange(source, key, from, to));
        }
    }

    private static void eachValues(Map<String, List<String>> map, java.util.function.BiConsumer<String, List<String>> action) {
        if (map == null) {
            return;
        }
        map.forEach((field, values) -> {
            if (hasText(field) && values != null) {
                List<String> cleaned = values.stream().filter(AdvancedSearchTranslator::hasText).toList();
                if (!cleaned.isEmpty()) {
                    action.accept(field.trim(), cleaned);
                }
            }
        });
    }

    private static List<String> names(List<String> names) {
        return names == null ? List.of() : names.stream().filter(AdvancedSearchTranslator::hasText).map(String::trim).toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Dùng trong test/khác: điều kiện AND. */
    static AllOf allOf(List<Condition> conditions) {
        return new AllOf(List.copyOf(conditions));
    }
}
