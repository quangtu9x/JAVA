package com.td.infrastructure.tonghop;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProvinceScopeSqlTest {

    @Test
    void unitScopeAddsTenantConditionAsParameter() {
        List<String> where = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        PostgresProvinceQueryRepository.addScopeTenant(where, p, "H05_01");
        assertThat(where).containsExactly("d.tenant_id = :scopeTenant");
        assertThat(p.getValue("scopeTenant")).isEqualTo("H05_01");
    }

    @Test
    void provinceScopeAddsNothing() {
        List<String> where = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        PostgresProvinceQueryRepository.addScopeTenant(where, p, null);
        PostgresProvinceQueryRepository.addScopeTenant(where, p, " ");
        assertThat(where).isEmpty();
        assertThat(p.hasValue("scopeTenant")).isFalse();
    }
}
