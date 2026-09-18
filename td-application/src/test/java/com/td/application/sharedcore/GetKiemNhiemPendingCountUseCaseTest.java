package com.td.application.sharedcore;

import com.td.application.common.models.PaginationResponse;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Gap #3 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026; cập nhật
// 09/09/2026 sau khi port module phieutrinhtd — xem docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md
// mục 8): port SoLuongCanXuLyKiemNhiem (Domino) — 7/12 field gốc (VBDEN_KHAC + DUTHAO_KHAC + 3/8
// PHIEUTRINH_KHAC ánh xạ thay thế qua view thật của phieutrinhtd), 5/8 field PHIEUTRINH_KHAC còn lại
// vắng mặt có chủ đích (không có khái niệm tương đương trong phieutrinhtd).
@ExtendWith(MockitoExtension.class)
class GetKiemNhiemPendingCountUseCaseTest {

    @Mock
    private ViewQueryService viewQueryService;

    private GetKiemNhiemPendingCountUseCase newUseCase() {
        return new GetKiemNhiemPendingCountUseCase(viewQueryService);
    }

    private PaginationResponse<Map<String, Object>> pageOfSize(long total) {
        return new PaginationResponse<>(List.of(), 1, 1, total, 1, true, true);
    }

    @Test
    void execute_blankUsername_returnsZeroesWithoutQuerying() {
        var counts = newUseCase().execute("  ");

        assertThat(counts.get("user_process")).isEqualTo(0L);
        assertThat(counts.get("drafting_user_signer_waiting")).isEqualTo(0L);
        assertThat(counts.get("drafting_user_process_handler_submit")).isEqualTo(0L);
        assertThat(counts.get("draft_signer_documentout_return")).isEqualTo(0L);
        assertThat(counts.get("pt_chopheduyet")).isEqualTo(0L);
        assertThat(counts.get("pt_chochoykien")).isEqualTo(0L);
        assertThat(counts.get("pt_tralai")).isEqualTo(0L);
        verify(viewQueryService, never()).execute(any(), any());
    }

    @Test
    void execute_validUsername_queriesAllViewsAndMapsCounts() {
        when(viewQueryService.execute(eq("vbden"), argThatViewname("chutri"))).thenReturn(pageOfSize(3));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwsigner_submit"))).thenReturn(pageOfSize(1));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwuser_process_handler_submit")))
            .thenReturn(pageOfSize(2));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwsigner_documentout_return")))
            .thenReturn(pageOfSize(0));
        when(viewQueryService.execute(eq("phieutrinhtd"), argThatViewname("phieutrinhtd_choduyet")))
            .thenReturn(pageOfSize(4));
        when(viewQueryService.execute(eq("phieutrinhtd"), argThatViewname("phieutrinhtd_thamkhao")))
            .thenReturn(pageOfSize(5));
        when(viewQueryService.execute(eq("phieutrinhtd"), argThatViewname("phieutrinhtd_khongduyet")))
            .thenReturn(pageOfSize(1));
        when(viewQueryService.execute(eq("phieutrinhtd"), argThatViewname("phieutrinhtd_khongthongqua")))
            .thenReturn(pageOfSize(2));

        var counts = newUseCase().execute("nhanvien04-kn1");

        assertThat(counts.get("user_process")).isEqualTo(3L);
        assertThat(counts.get("drafting_user_signer_waiting")).isEqualTo(1L);
        assertThat(counts.get("drafting_user_process_handler_submit")).isEqualTo(2L);
        assertThat(counts.get("draft_signer_documentout_return")).isEqualTo(0L);
        assertThat(counts.get("pt_chopheduyet")).isEqualTo(4L);
        assertThat(counts.get("pt_chochoykien")).isEqualTo(5L);
        // pt_tralai = tong khongduyet(1) + khongthongqua(2)
        assertThat(counts.get("pt_tralai")).isEqualTo(3L);
    }

    private ViewQueryRequest argThatViewname(String viewname) {
        return ArgumentMatchers.argThat(req -> req != null && viewname.equals(req.getViewname())
            && req.getKeys().equals(List.of("nhanvien04-kn1")));
    }
}
