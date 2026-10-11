package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Port SoLuongCanXuLy (09/09/2026, xem javadoc GetSoLuongCanXuLyUseCase) - so lieu CA NHAN, 5 nhom
// (vbden/duthao/hosocv/phieutrinhtd/vbdi). Cap nhat 09/09/2026 (2): doi chieu lai voi DXL, port du
// 44/44 field ca nhan. Cap nhat 09/09/2026 (3): them so lieu CAP CO QUAN (gate theo role + identifier).
@ExtendWith(MockitoExtension.class)
class GetSoLuongCanXuLyUseCaseTest {

    private static final String USERNAME = "nguoidung01";
    private static final String IDENTIFIER = "H05.01";

    @Mock
    private ViewQueryService viewQueryService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;

    private GetSoLuongCanXuLyUseCase newUseCase() {
        return new GetSoLuongCanXuLyUseCase(viewQueryService, currentUserProvider, resolveIdentifierByLevelUseCase);
    }

    private PaginationResponse<Map<String, Object>> pageOfSize(long total) {
        return new PaginationResponse<>(List.of(), 1, 1, total, 1, true, true);
    }

    @Test
    void execute_noCurrentUser_returnsEmptyMapWithoutQuerying() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var counts = newUseCase().execute();

        assertThat(counts).isEmpty();
        verify(viewQueryService, never()).execute(any(), any());
    }

    @Test
    void execute_withCurrentUser_queriesEveryPortedViewAndMapsCounts() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(USERNAME));
        // Khong co role nao -> khong tinh so lieu cap co quan (xem test rieng ben duoi).
        when(currentUserProvider.currentRoles()).thenReturn(Set.of());
        when(viewQueryService.execute(any(), any())).thenReturn(pageOfSize(0));
        when(viewQueryService.execute(eq("vbden"), argThatViewname("chutri"))).thenReturn(pageOfSize(3));
        // 09/09/2026 (2): 2 field moi port, xem javadoc lop.
        when(viewQueryService.execute(eq("vbden"), argThatViewname("dennoibo_choxuly"))).thenReturn(pageOfSize(7));
        when(viewQueryService.execute(eq("vbden"), argThatViewname("phutrach_chuaxem"))).thenReturn(pageOfSize(8));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwsigner_submit"))).thenReturn(pageOfSize(1));
        // Bugfix 09/09/2026 (2): view DUNG cua "drafting_creator_documentout_submited" la
        // "vwdocumentout_submited" (KHONG phai "vwcreator_documentout_submited") - xem javadoc lop.
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwdocumentout_submited"))).thenReturn(pageOfSize(6));
        // "vwChoDuyetKyBanHanh" = tong 2 nhanh OR-group (signer + handler).
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwchoduyetkybanhanh_signer_internal")))
            .thenReturn(pageOfSize(2));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwchoduyetkybanhanh_handler_internal")))
            .thenReturn(pageOfSize(3));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwchoduyetkybanhanhmc_signer_internal")))
            .thenReturn(pageOfSize(0));
        when(viewQueryService.execute(eq("duthao"), argThatViewname("vwchoduyetkybanhanhmc_handler_internal")))
            .thenReturn(pageOfSize(1));
        when(viewQueryService.execute(eq("hosocv"), argThatViewname("hoso_chutri_dangxuly"))).thenReturn(pageOfSize(2));
        when(viewQueryService.execute(eq("phieutrinhtd"), argThatViewname("phieutrinhtd_choduyet")))
            .thenReturn(pageOfSize(4));
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwprivateimportant"))).thenReturn(pageOfSize(5));
        // 09/09/2026 (2): 2 field vbdi moi port ("Signer" - khoa scalar user_signer).
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwsigner_documentout_sent"))).thenReturn(pageOfSize(9));
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwsigner_documentout_waitingtosend")))
            .thenReturn(pageOfSize(10));

        var counts = newUseCase().execute();

        assertThat(counts.get("user_process")).isEqualTo(3L);
        assertThat(counts.get("dennoibo_choxuly")).isEqualTo(7L);
        assertThat(counts.get("user_manage")).isEqualTo(8L);
        assertThat(counts.get("drafting_user_signer_waiting")).isEqualTo(1L);
        assertThat(counts.get("drafting_creator_documentout_submited")).isEqualTo(6L);
        assertThat(counts.get("vwChoDuyetKyBanHanh")).isEqualTo(5L); // 2 + 3
        assertThat(counts.get("vwChoDuyetKyBanHanhMC")).isEqualTo(1L); // 0 + 1
        assertThat(counts.get("hoso_chutri")).isEqualTo(2L);
        assertThat(counts.get("phieutrinh_choduyet")).isEqualTo(4L);
        assertThat(counts.get("send_important")).isEqualTo(5L);
        assertThat(counts.get("dakybanhanh")).isEqualTo(9L);
        assertThat(counts.get("dakychuabanhanh")).isEqualTo(10L);
        // Field khong duoc stub rieng - van co mat trong map (gia tri mac dinh 0 tu stub chung).
        assertThat(counts.get("vanbantralai")).isEqualTo(0L);
        assertThat(counts.get("draft_vanthu_tralai")).isEqualTo(0L);
        // duthao 12 + 1 (01/10/2026: draft_vanthu_tralai - route gop "Van thu tra lai").
        assertThat(counts).hasSize(15 + 13 + 7 + 7 + 4); // vbden + duthao + hosocv + phieutrinhtd (7/7 tu 06/10/2026) + vbdi
        verify(resolveIdentifierByLevelUseCase, never()).resolveForCurrentUser();
    }

    // Cac view "Creator" (khoa to chuc) KHONG duoc goi nham cho 2 field "Signer" (khoa username) -
    // bugfix 09/09/2026 (2), xem javadoc lop.
    @Test
    void execute_shouldNotQueryWrongCreatorViewsForSignerFields() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(USERNAME));
        when(currentUserProvider.currentRoles()).thenReturn(Set.of());
        when(viewQueryService.execute(any(), any())).thenReturn(pageOfSize(0));

        newUseCase().execute();

        verify(viewQueryService, never()).execute(eq("duthao"), argThatViewname("vwcreator_documentout_submited"));
        verify(viewQueryService, never()).execute(eq("vbdi"), argThatViewname("vwcreator_documentout_sent"));
        verify(viewQueryService, never()).execute(eq("vbdi"), argThatViewname("vwcreator_documentout_waitingtosend"));
    }

    // ================= Cap nhat 09/09/2026 (3): so lieu CAP CO QUAN =================

    @Test
    void execute_withoutEligibleRole_shouldNotComputeOrgLevelCounts() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(USERNAME));
        when(currentUserProvider.currentRoles()).thenReturn(Set.of("QXEMVBDEN")); // role KHONG du dieu kien
        when(viewQueryService.execute(any(), any())).thenReturn(pageOfSize(0));

        var counts = newUseCase().execute();

        assertThat(counts).doesNotContainKey("captrengiao");
        assertThat(counts).doesNotContainKey("send_reject");
        assertThat(counts).doesNotContainKey("hosoluutru_chotiepnhan");
        verify(resolveIdentifierByLevelUseCase, never()).resolveForCurrentUser();
    }

    @Test
    void execute_withEligibleRoleButNoIdentifier_shouldNotComputeOrgLevelCounts() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(USERNAME));
        when(currentUserProvider.currentRoles()).thenReturn(Set.of("QPHANCONG"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());
        when(viewQueryService.execute(any(), any())).thenReturn(pageOfSize(0));

        var counts = newUseCase().execute();

        assertThat(counts).doesNotContainKey("captrengiao");
    }

    @Test
    void execute_withEligibleRoleAndIdentifier_shouldComputeAllOrgLevelCounts() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(USERNAME));
        when(currentUserProvider.currentRoles()).thenReturn(Set.of("QPHANPHOI"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(IDENTIFIER));
        when(viewQueryService.execute(any(), any())).thenReturn(pageOfSize(0));
        when(viewQueryService.execute(eq("vbden"), argThatViewname("captrengiao"))).thenReturn(pageOfSize(11));
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwcreator_drafting"))).thenReturn(pageOfSize(12));
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwcreator_documentout_refuse"))).thenReturn(pageOfSize(13));
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwcreator_documentout_reply"))).thenReturn(pageOfSize(14));
        when(viewQueryService.execute(eq("hosoluutru"), argThatViewname("phieuyeucau_quahan_by_identifier")))
            .thenReturn(pageOfSize(15));

        var counts = newUseCase().execute();

        assertThat(counts.get("captrengiao")).isEqualTo(11L);
        assertThat(counts.get("send_waiting_for_signature")).isEqualTo(12L);
        assertThat(counts.get("send_reject")).isEqualTo(13L);
        assertThat(counts.get("send_returned")).isEqualTo(14L);
        assertThat(counts.get("hosoluutru_phieuyeucau_quahan")).isEqualTo(15L);
        // 11 vbden_coquan + 6 vbdi_coquan + 7 hosoluutru_coquan.
        long orgLevelKeys = List.of(
            "captrengiao", "lienthong", "dendientu", "quamangnoibo", "dennoibo", "phaixuly", "quahan",
            "daxulyquahan", "daxulydunghan", "saptoihan", "denhan",
            "send_waiting_for_signature", "send_waiting_for_signature_mc", "WaitingToSend", "SendNoResponse",
            "send_reject", "send_returned",
            "hosoluutru_chotiepnhan", "hosoluutru_chochuyenluutru", "hosoluutru_dachuyenluutru",
            "hosoluutru_phieuyeucau0", "hosoluutru_phieuyeucau13", "hosoluutru_phieuyeucau_dangmuon",
            "hosoluutru_phieuyeucau_quahan"
        ).stream().filter(counts::containsKey).count();
        assertThat(orgLevelKeys).isEqualTo(24);
    }

    // 09/10/2026 (bao loi demo10): van thu don vi chi co quyen pham vi don vi (QCapNhatVBDiDV) van phai
    // thay so "Cho gui" (WaitingToSend) o Trang chu/menu.
    @Test
    void execute_withUnitScopedVanThuRole_shouldComputeWaitingToSend() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of(USERNAME));
        when(currentUserProvider.currentRoles()).thenReturn(Set.of("QCAPNHATVBDIDV"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(IDENTIFIER));
        when(viewQueryService.execute(any(), any())).thenReturn(pageOfSize(0));
        when(viewQueryService.execute(eq("vbdi"), argThatViewname("vwcreator_documentout_waitingtosend")))
            .thenReturn(pageOfSize(3));

        var counts = newUseCase().execute();

        assertThat(counts.get("WaitingToSend")).isEqualTo(3L);
    }

    private ViewQueryRequest argThatViewname(String viewname) {
        return ArgumentMatchers.argThat(req -> req != null && viewname.equals(req.getViewname()));
    }
}
