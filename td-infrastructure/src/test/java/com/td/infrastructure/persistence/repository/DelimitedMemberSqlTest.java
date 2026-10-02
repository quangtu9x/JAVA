package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 27/09/2026 (DT-29136, sửa tổng thể): nhánh khớp thành viên chuỗi "a;b" dùng chung mọi repository.
 */
class DelimitedMemberSqlTest {

    @Test
    void appendOrMember_shouldAnchorSeparatorsLowercaseAndEscapeLikeWildcards() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        DelimitedMemberSql.appendOrMember(sql, parameters, "author", " Demo_1 ", "p0");

        assertThat(sql.toString()).isEqualTo(
            " OR (';' || REPLACE(LOWER(COALESCE(d.dynamic_fields->>'author', '')), ' ', '') || ';') LIKE :p0");
        assertThat(parameters).containsEntry("p0", "%;demo\\_1;%");
    }

    @Test
    void appendOrMember_withBlankOrDelimitedValue_shouldAppendNothing() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        DelimitedMemberSql.appendOrMember(sql, parameters, "author", "  ", "p0");
        DelimitedMemberSql.appendOrMember(sql, parameters, "author", null, "p1");
        DelimitedMemberSql.appendOrMember(sql, parameters, "author", "a;b", "p2");

        assertThat(sql.toString()).isEmpty();
        assertThat(parameters).isEmpty();
    }

    // documents (vbden...): author do AuthorAclFieldsService co y ghi "a;b" - view khoa theo author
    // truoc day chi khop khi author co dung 1 gia tri.
    @Test
    void documentRepository_appendAnyFieldContainsPredicate_shouldIncludeMemberBranchInsideFieldGroup() {
        PostgresDocumentRepository repository = new PostgresDocumentRepository(null);
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, List.of("author", "user_process"), "demo1");

        String text = sql.toString();
        assertThat(text).contains("LIKE :containsFieldMember0").contains("LIKE :containsFieldMember1");
        assertThat(text).startsWith(" AND (").endsWith(")");
        assertThat(text.chars().filter(c -> c == '(').count())
            .isEqualTo(text.chars().filter(c -> c == ')').count());
        assertThat(parameters).containsEntry("containsFieldMember0", "%;demo1;%");
    }
}
