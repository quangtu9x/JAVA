package com.td.application.duthao;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** 22/10/2026 — tra cứu nhóm / thống kê dự thảo (xem javadoc {@link DuThaoGroupViewQuery}). */
@ExtendWith(MockitoExtension.class)
class DuThaoGroupViewQueryTest {

    @Mock
    private ViewQueryService viewQueryService;

    private DuThaoGroupViewQuery query;

    @BeforeEach
    void setUp() {
        query = new DuThaoGroupViewQuery(viewQueryService, new DynamicDocumentFieldsService());
        lenient().when(viewQueryService.toDuThaoListFields(any(), any()))
            .thenAnswer(invocation -> Map.of("id", ((DuThaoDocument) invocation.getArgument(0)).getId()));
    }

    private DuThaoDocument draft(String status, String json) {
        DuThaoDocument document = new DuThaoDocument("Du thao", "duthao", status, null, null, null, null);
        return document.replaceDynamicFields(json);
    }

    private ViewQueryRequest request(String viewname, Boolean ischild, String nav) {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname(viewname);
        request.setIschild(ischild);
        request.setNavposition(nav);
        request.setPageNumber(1);
        request.setPageSize(10);
        return request;
    }

    @Test
    void handles_shouldRecognizeGroupAndStatisticsViewsOnly() {
        assertThat(DuThaoGroupViewQuery.handles("vwGroup_by_Signer")).isTrue();
        assertThat(DuThaoGroupViewQuery.handles("thongke_donvi")).isTrue();
        assertThat(DuThaoGroupViewQuery.handles("vwUser_Creator")).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void signerView_shouldSkipDraftsWithoutSignerAndNestSignerYearMonth() {
        DuThaoDocument a = draft("1", "{\"user_signer\":\"ld1\",\"signer\":\"Lãnh đạo B\",\"create_date\":\"05/03/2026\"}");
        DuThaoDocument b = draft("1", "{\"user_signer\":\"ld2\",\"signer\":\"Lãnh đạo A\",\"create_date\":\"10/11/2025\"}");
        DuThaoDocument c = draft("0", "{\"signer\":\"\"}");
        when(viewQueryService.findDuThaoOrgDocuments(any(), anyInt())).thenReturn(List.of(a, b, c));

        var result = query.execute(request("vwGroup_by_Signer", false, ""));

        assertThat(result.getItems()).hasSize(2);
        Map<String, Object> first = result.getItems().get(0);
        assertThat(first).containsEntry("key", "1").containsEntry("name", "Lãnh đạo A").containsEntry("count", 1);
        Map<String, Object> year = ((List<Map<String, Object>>) first.get("children")).get(0);
        assertThat(year).containsEntry("key", "1.1").containsEntry("name", "2025");
        Map<String, Object> month = ((List<Map<String, Object>>) year.get("children")).get(0);
        assertThat(month).containsEntry("key", "1.1.1").containsEntry("name", "11");
        assertThat((List<?>) month.get("children")).isEmpty();
    }

    @Test
    void child_shouldReturnDraftsOfLeafGroup() {
        DuThaoDocument a = draft("1", "{\"user_signer\":\"ld1\",\"signer\":\"Lãnh đạo B\",\"create_date\":\"05/03/2026\"}");
        DuThaoDocument b = draft("1", "{\"user_signer\":\"ld2\",\"signer\":\"Lãnh đạo A\",\"create_date\":\"10/11/2025\"}");
        when(viewQueryService.findDuThaoOrgDocuments(any(), anyInt())).thenReturn(List.of(a, b));

        var result = query.execute(request("vwGroup_by_Signer", true, "2.1.1"));

        assertThat(result.getTotalItems()).isEqualTo(1);
        assertThat(result.getItems().get(0)).containsEntry("id", a.getId());
    }

    @Test
    void statisticsByUnitAndSigner_shouldCountStatusesInColumns() {
        DuThaoDocument a = draft("", "{\"unit_in_charge\":\"Phòng A\",\"signer\":\"LĐ\"}");
        DuThaoDocument b = draft("8", "{\"unit_in_charge\":\"Phòng A\",\"signer\":\"LĐ\"}");
        when(viewQueryService.findDuThaoOrgDocuments(any(), anyInt())).thenReturn(List.of(a, b));

        var result = query.execute(request("thongke_theodonvinguoixuly", false, ""));

        Map<String, Object> unit = result.getItems().get(0);
        assertThat(unit).containsEntry("name", "Phòng A").containsEntry("count", 2);
        assertThat(unit.get("col_11")).isEqualTo(Map.of("value", 1L));
        assertThat(unit.get("col_19")).isEqualTo(Map.of("value", 1L));
        assertThat(unit.get("col_12")).isEqualTo(Map.of("value", 0L));
    }

    @Test
    void parseDate_shouldReadDominoAndIsoFormats() {
        assertThat(DuThaoGroupViewQuery.parseDate("05/03/2026 10:00:00")).containsExactly(2026, 3, 5);
        assertThat(DuThaoGroupViewQuery.parseDate("2026-03-05")).containsExactly(2026, 3, 5);
        assertThat(DuThaoGroupViewQuery.parseDate("")).isNull();
    }
}
