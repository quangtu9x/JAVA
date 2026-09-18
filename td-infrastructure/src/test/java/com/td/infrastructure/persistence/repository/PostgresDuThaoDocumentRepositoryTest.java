package com.td.infrastructure.persistence.repository;

import com.td.application.documents.VbdenOrgViewFilter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DT-002: unit test SQL builder cua {@link PostgresDuThaoDocumentRepository} (khong can DB/EntityManager
 * — repository duoc khoi tao voi jpaRepository null, chi goi cac method build predicate).
 *
 * <p>BUGFIX 25/08/2026 (regression lock): {@code vwUserCreator_Process_Handler_Submit} tra RONG du
 * DB co du lieu vi {@code findByVbdenOrgView} goi {@code appendAnyFieldContainsPredicate} VO DIEU
 * KIEN — helper do voi {@code orgFieldNames} null/rong tu gan {@code "AND 1 = 0"} nen nhanh
 * {@code orgListFieldNames} cua view UserCreator khong bao gio co tac dung. Cac test o day khoa 3
 * hanh vi cua composition: list-only (view UserCreator), plain-only (cac view cu), va khong co khoa.
 */
class PostgresDuThaoDocumentRepositoryTest {

    private final PostgresDuThaoDocumentRepository repository = new PostgresDuThaoDocumentRepository(null);

    // View "vwUserCreator_Process_Handler_Submit" di qua nhanh orgListFieldNames - phai sinh member
    // match (LIKE neo bien ";") VA TUYET DOI KHONG duoc chua "1 = 0".
    @Test
    void appendVbdenOrgKeyPredicates_listFieldOnly_shouldAppendMemberMatchWithoutImpossibleCondition() {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM duthao_documents d WHERE d.deleted_on IS NULL");
        Map<String, Object> parameters = new LinkedHashMap<>();
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgListFieldNames(List.of("user_other_processed_handler"))
            .orgValue("SKHCN")
            .includeFieldValues(Map.of("status", List.of("5", "9")))
            .build();

        repository.appendVbdenOrgKeyPredicates(sql, parameters, filter);

        String text = sql.toString();
        assertThat(text).doesNotContain("1 = 0");
        assertThat(text)
            .contains("LOWER(COALESCE(d.dynamic_fields->>'user_other_processed_handler', '')) = :listFieldExact0");
        assertThat(text).contains("LIKE :listFieldPrefix0");
        assertThat(text).contains("LIKE :listFieldSuffix0");
        assertThat(text).contains("LIKE :listFieldMiddle0");
        // gia tri phai duoc normalize lowercase/trim de so khop case-insensitive theo sortnocase Domino
        assertThat(parameters).containsEntry("listFieldExact0", "skhcn");
        assertThat(parameters).containsEntry("listFieldPrefix0", "skhcn;%");
        assertThat(parameters).containsEntry("listFieldSuffix0", "%;skhcn");
        assertThat(parameters).containsEntry("listFieldMiddle0", "%;skhcn;%");
    }

    // Cac view cu (vwUser_Creator/vwCreator_Drafting/vwUser_Other_Process_Handler_Submit...) van di
    // nhanh orgFieldNames (containment + scalar equality) - khong bi anh huong bugfix.
    @Test
    void appendVbdenOrgKeyPredicates_plainFieldsOnly_shouldAppendContainmentWithoutImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder()
            .orgFieldNames(List.of("user_creator"))
            .orgValue("skhcn")
            .build();

        repository.appendVbdenOrgKeyPredicates(sql, parameters, filter);

        String text = sql.toString();
        assertThat(text).doesNotContain("1 = 0");
        assertThat(text).contains("d.dynamic_fields @> CAST(:containsField0 AS jsonb)");
        assertThat(text).contains("COALESCE(d.dynamic_fields->>'user_creator', '') = :containsFieldScalar0");
        assertThat(parameters).containsEntry("containsFieldScalar0", "skhcn");
    }

    // Contract cu giu nguyen: filter khong co bat ky loai khoa nao -> dieu kien vo ly (tra trang),
    // tang service con tu guard blank-key truoc khi build filter.
    @Test
    void appendVbdenOrgKeyPredicates_withoutAnyOrgKey_shouldAppendImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();
        VbdenOrgViewFilter filter = VbdenOrgViewFilter.builder().build();

        repository.appendVbdenOrgKeyPredicates(sql, parameters, filter);

        assertThat(sql.toString()).contains("AND 1 = 0");
    }

    // Nguyen nhan goc cua bugfix: helper containment tu gan "1 = 0" khi danh sach field rong -
    // ghi nhan hanh vi nay de ai doc test hieu vi sao buoc phai goi co dieu kien.
    @Test
    void appendAnyFieldContainsPredicate_withNullFieldNames_shouldAppendImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, null, "skhcn");

        assertThat(sql.toString()).isEqualTo(" AND 1 = 0");
    }

    // Member match phai khop ca truong hop duy nhat / dau / cuoi / giua chuoi ";" - 4 nhanh LIKE
    // phu nhau (vd. "nam" KHONG duoc khop trong "an;nam" vi thieu bien ";" ben trai).
    @Test
    void appendAnyListFieldMemberPredicate_withMultipleFields_shouldGiveEachFieldItsOwnParameterSet() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyListFieldMemberPredicate(
            sql, parameters, List.of("user_other_processed_handler", "user_processed_handler"), "a");

        String text = sql.toString();
        assertThat(text).contains("'user_other_processed_handler'");
        assertThat(text).contains("'user_processed_handler'");
        assertThat(parameters).containsKeys(
            "listFieldExact0", "listFieldExact1", "listFieldPrefix1", "listFieldSuffix1", "listFieldMiddle1");
    }

    // DT-002 (26/08/2026) - vwUser_Coordinated: OR-group giua 2 field/2 dieu kien status khac nhau.
    // Nhanh 1 (fieldWithoutStatus) KHONG duoc co dieu kien status di kem trong cung nhom OR cua no.
    @Test
    void appendFieldOrFieldWithStatusPredicate_shouldOrTwoBranchesWithStatusOnlyOnSecond() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldOrFieldWithStatusPredicate(
            sql, parameters, "user_coordinated", "user_coordinator", List.of("8", "7", "4"), "hueltm");

        String text = sql.toString();
        assertThat(text).contains("COALESCE(d.dynamic_fields->>'user_coordinated', '') = :orGroupScalar0");
        assertThat(text).contains("COALESCE(d.dynamic_fields->>'user_coordinator', '') = :orGroupScalar1");
        assertThat(text).contains("LOWER(COALESCE(d.status, '')) IN (:orGroupDoneStatus0,:orGroupDoneStatus1,:orGroupDoneStatus2)");
        // nhanh 1 (coordinated) phai dung TRUOC dieu kien status - status chi rang buoc nhanh 2.
        int coordinatedIndex = text.indexOf("orGroupScalar0");
        int statusIndex = text.indexOf("d.status");
        assertThat(coordinatedIndex).isLessThan(statusIndex);
        assertThat(parameters).containsEntry("orGroupScalar0", "hueltm");
        assertThat(parameters).containsEntry("orGroupScalar1", "hueltm");
        assertThat(parameters).containsEntry("orGroupDoneStatus0", "8");
        assertThat(parameters).containsEntry("orGroupDoneStatus2", "4");
    }

    // doneStatuses rong -> nhanh 2 (fieldWithStatus) khong bao gio dung ("1 = 0"), TRANH loi SQL
    // "IN ()" rong - nhanh 1 (fieldWithoutStatus) van hoat dong binh thuong qua OR.
    @Test
    void appendFieldOrFieldWithStatusPredicate_withEmptyDoneStatuses_shouldMakeSecondBranchImpossible() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldOrFieldWithStatusPredicate(
            sql, parameters, "user_coordinated", "user_coordinator", List.of(), "hueltm");

        String text = sql.toString();
        assertThat(text).contains("AND 1 = 0");
        assertThat(text).doesNotContain(" IN ()");
    }
}