package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port module "Phiếu trình thẩm định": unit test SQL builder của
 * {@link PostgresPhieuTrinhTdDocumentRepository} (không cần DB/EntityManager — repository khởi tạo
 * với jpaRepository null), đúng khuôn {@code PostgresHoSoLuuTruDocumentRepositoryTest}.
 */
class PostgresPhieuTrinhTdDocumentRepositoryTest {

    private final PostgresPhieuTrinhTdDocumentRepository repository =
        new PostgresPhieuTrinhTdDocumentRepository(null);

    @Test
    void appendAnyFieldContainsMemberOr_withSingleField_shouldBuildCaseInsensitiveMemberMatch() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsMemberOr(sql, parameters, List.of("usercreator"), "hueltm");

        String text = sql.toString();
        assertThat(text).contains("'usercreator'");
        assertThat(text).contains("LIKE '%;' || UPPER(TRIM(:member)) || ';%'");
        assertThat(parameters).containsEntry("member", "hueltm");
    }

    @Test
    void appendAnyFieldContainsMemberOr_withMultipleFields_shouldOrEachFieldPredicate() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsMemberOr(
            sql, parameters, List.of("usercreator", "signerhide", "lanhdao1"), "hueltm");

        String text = sql.toString();
        assertThat(text).contains("'usercreator'");
        assertThat(text).contains("'signerhide'");
        assertThat(text).contains("'lanhdao1'");
        // 3 field -> 2 " OR " noi giua
        assertThat(text.split(" OR ", -1)).hasSize(3);
    }

    @Test
    void appendStatusInFilter_withStatuses_shouldAppendStatusInClauseWithIndexedParameters() {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM phieutrinhtd_documents d WHERE d.deleted_on IS NULL");
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendStatusInFilter(sql, parameters, List.of("5", "25"));

        String text = sql.toString();
        assertThat(text).contains("AND d.status IN (:status0, :status1)");
        assertThat(parameters).containsEntry("status0", "5");
        assertThat(parameters).containsEntry("status1", "25");
    }

    @Test
    void appendStatusInFilter_withEmptyStatuses_shouldAppendNothing() {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM phieutrinhtd_documents d");
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendStatusInFilter(sql, parameters, List.of());

        assertThat(sql.toString()).isEqualTo("SELECT d.* FROM phieutrinhtd_documents d");
        assertThat(parameters).isEmpty();
    }

    // 09/09/2026 (doi chieu DXL that `docs/views_domino/view_phieutrinh.dxl`): MOI view "ca nhan" that
    // cua phieutrinhtd deu co dieu kien "storaged != \"1\"" (vd vwUser_Creator_0 dong 397) - ban port
    // dau KHONG loc field nay, xem javadoc appendNotStoragedFilter.
    @Test
    void appendNotStoragedFilter_shouldAppendStoragedNotEqualOne() {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM phieutrinhtd_documents d WHERE d.deleted_on IS NULL");

        repository.appendNotStoragedFilter(sql);

        assertThat(sql.toString()).contains("AND COALESCE(d.dynamic_fields->>'storaged', '') != '1'");
    }
}
