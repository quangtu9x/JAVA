package com.td.infrastructure.tonghop.national;

import com.td.application.tonghop.national.NationalExchangeSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresNationalExchangeQueryRepositoryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 22);

    @Test
    void whereAlwaysBoundsTimeRangeAndBindsValuesAsParameters() {
        NationalExchangeSearchRequest r = new NationalExchangeSearchRequest();
        r.setDirection("OUTBOUND");
        r.setMessageType("edoc");
        r.setStates(List.of("ERROR", "QUARANTINED"));
        r.setTenantId("H05_01");
        r.setKeyword("H05.01'; DROP TABLE x;--");
        MapSqlParameterSource params = new MapSqlParameterSource();

        String where = PostgresNationalExchangeQueryRepository.where(r, FROM, TO, true, params);

        assertThat(where).startsWith("e.updated_at >= CAST(:fromDate AS date) AND e.updated_at < CAST(:toDate AS date) + 1");
        assertThat(where).contains("e.direction = :direction", "e.message_type = :messageType", "IN (:states)",
            "tr.tenant_id = :tenantId", "ILIKE :kwLike");
        assertThat(where).doesNotContain("DROP TABLE");
        assertThat(params.getValue("fromDate")).isEqualTo("2026-10-01");
        assertThat(params.getValue("toDate")).isEqualTo("2026-10-22");
        assertThat(params.getValue("states")).isEqualTo(List.of("ERROR", "QUARANTINED"));
    }

    @Test
    void summaryWhereIgnoresStateFilter() {
        NationalExchangeSearchRequest r = new NationalExchangeSearchRequest();
        r.setStates(List.of("ERROR"));
        String where = PostgresNationalExchangeQueryRepository.where(r, FROM, TO, false, new MapSqlParameterSource());
        assertThat(where).doesNotContain(":states");
    }

    @Test
    void recipientColumnsOnlyCountOwnTenantWhenScoped() {
        NationalExchangeSearchRequest all = new NationalExchangeSearchRequest();
        assertThat(PostgresNationalExchangeQueryRepository.recipientColumns(all))
            .isEqualTo("e.recipient_codes, e.recipient_count, e.acknowledged_count, e.recipient_summary");

        NationalExchangeSearchRequest scoped = new NationalExchangeSearchRequest();
        scoped.setTenantId("H05_01");
        String columns = PostgresNationalExchangeQueryRepository.recipientColumns(scoped);
        assertThat(columns).contains("sr.tenant_id = :tenantId", "AS recipient_codes", "AS recipient_count",
            "AS acknowledged_count", "AS recipient_summary");
    }

    @Test
    void keywordWildcardsAreEscaped() {
        assertThat(PostgresNationalExchangeQueryRepository.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
    }

    @Test
    void stateSqlCoversEveryDisplayState() {
        assertThat(PostgresNationalExchangeQueryRepository.STATE_SQL)
            .contains("'DONE'", "'BUILT_ONLY'", "'QUARANTINED'", "'ERROR'", "'IN_PROGRESS'");
    }

    @Test
    void dynamicFieldsHideFilesystemPathsAndSecrets() {
        Map<String, Object> visible = PostgresNationalExchangeQueryRepository.visibleDynamicFields(Map.of(
            "packageVariant", "standard", "edxmlFilePath", "/data/outbox/a.edxml", "apiSecret", "x",
            "accessToken", "y"));
        assertThat(visible).containsOnlyKeys("packageVariant");
        assertThat(PostgresNationalExchangeQueryRepository.visibleDynamicFields(null)).isEmpty();
    }
}
