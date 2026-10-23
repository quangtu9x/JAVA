package com.td.application.search;

import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.search.AdvancedSearchQuery.AnyOf;
import com.td.application.search.AdvancedSearchQuery.Condition;
import com.td.application.search.AdvancedSearchQuery.DateRange;
import com.td.application.search.AdvancedSearchQuery.Not;
import com.td.application.search.AdvancedSearchQuery.SortField;
import com.td.application.search.AdvancedSearchQuery.Source;
import com.td.application.search.AdvancedSearchQuery.Terms;
import com.td.application.vbdi.VbdiDocumentOutFilter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 17/10/2026 — bộ lọc SQL → truy vấn index tìm kiếm (docs/analysis/27). */
class AdvancedSearchTranslatorTest {

    @Test
    void fromOrgView_vbdenSearch_shouldKeepScopeFiltersDatesKeywordAndSort() {
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of("identifier_creator", "unit_process"))
            .orgValue(" H05.01 ")
            .includeFieldValues(Map.of("status", List.of("1"), "priority", List.of("Khẩn")))
            .containsAnyFieldValues(Map.of("user_process", List.of("a", "b")))
            .minReceivedDate(LocalDate.of(2026, 10, 1))
            .keyword("cong van")
            .keywordColumns(List.of("subject", "code"))
            .build();

        AdvancedSearchQuery query = AdvancedSearchTranslator.fromOrgView(filter, "vbden", true);

        List<Condition> filters = query.filters();
        assertThat(filters).contains(new Terms(Source.COL, "document_type", List.of("vbden")));
        assertThat(filters).contains(new Not(new Terms(Source.DYN, "is_revoke", List.of("1"))));
        assertThat(filters).contains(new AnyOf(List.of(
            new Terms(Source.DYN, "identifier_creator", List.of("H05.01")),
            new Terms(Source.DYN, "unit_process", List.of("H05.01")))));
        assertThat(filters).as("status là cột thật").contains(new Terms(Source.COL, "status", List.of("1")));
        assertThat(filters).contains(new Terms(Source.DYN, "priority", List.of("Khẩn")));
        assertThat(filters).contains(new Terms(Source.DYN, "user_process", List.of("a", "b")));
        assertThat(filters).contains(new DateRange(Source.DYN, "received_date", LocalDate.of(2026, 10, 1), null));
        assertThat(query.keyword()).isEqualTo("cong van");
        assertThat(query.keywordFields()).containsExactly("subject", "code");
        assertThat(query.sort()).containsExactly(SortField.CREATED_ON, SortField.LAST_MODIFIED_ON);
    }

    @Test
    void fromOrgView_withoutRevokeExclusion_shouldNotFilterRevoked() {
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of("unit_identifier")).orgValue("H05").build();

        AdvancedSearchQuery query = AdvancedSearchTranslator.fromOrgView(filter, "duthao", false);

        assertThat(query.filters()).doesNotContain(new Not(new Terms(Source.DYN, "is_revoke", List.of("1"))));
    }

    // Tính năng chưa hỗ trợ trên index → null (người gọi chạy SQL), không trả kết quả sai.
    @Test
    void fromOrgView_unsupportedFeatures_shouldReturnNull() {
        assertThat(AdvancedSearchTranslator.fromOrgView(
            VbdenOrgViewFilter.builder().orgFieldNames(List.of("x")).orgValue("H05").deletedOnly(true).build(), "vbden", true))
            .isNull();
        assertThat(AdvancedSearchTranslator.fromOrgView(
            VbdenOrgViewFilter.builder().orgFieldNames(List.of("x")).orgValue("H05").primarySortField("document_number").build(),
            "vbden", true)).isNull();
        assertThat(AdvancedSearchTranslator.fromOrgView(
            VbdenOrgViewFilter.builder().orgFieldNames(List.of("x")).build(), "vbden", true))
            .as("có khoá phạm vi mà thiếu giá trị").isNull();
    }

    @Test
    void fromVbdi_shouldScopeByIdentifierStatusAndSortByPromulgation() {
        VbdiDocumentOutFilter filter = VbdiDocumentOutFilter.builder()
            .promulgationDateFrom(LocalDate.of(2026, 1, 1))
            .includeFieldValues(Map.of("document_type", List.of("Công văn")))
            .containsAnyFieldValues(Map.of("code_full", List.of("12/UBND")))
            .build();

        AdvancedSearchQuery query = AdvancedSearchTranslator.fromVbdiIssuedByIdentifier(
            filter, "ISSUED", List.of("unit_publish_identifier", "unit_identifier"), "H05.01");

        assertThat(query.filters()).contains(new Terms(Source.COL, "status", List.of("ISSUED")));
        assertThat(query.filters()).contains(new AnyOf(List.of(
            new Terms(Source.DYN, "unit_publish_identifier", List.of("H05.01")),
            new Terms(Source.DYN, "unit_identifier", List.of("H05.01")))));
        assertThat(query.filters()).contains(new Terms(Source.DYN, "document_type", List.of("Công văn")));
        assertThat(query.filters()).contains(new DateRange(Source.DYN, "promulgation_date", LocalDate.of(2026, 1, 1), null));
        assertThat(query.sort()).containsExactly(SortField.PROMULGATION_DATE, SortField.DOCUMENT_NUMBER, SortField.LAST_MODIFIED_ON);
        assertThat(AdvancedSearchTranslator.fromVbdiIssuedByIdentifier(
            VbdiDocumentOutFilter.builder().extraPredicateSql("x").build(), "ISSUED", List.of("a"), "H05")).isNull();
    }
}
