package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.phieutrinhtd.AddPhieuTrinhTdDynamicDocumentUseCase;
import com.td.application.phieutrinhtd.CapNhatPhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.ChuyenTrangThaiPhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.DeletePhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.GetPhieuTrinhTdChiTietUseCase;
import com.td.application.phieutrinhtd.RestorePhieuTrinhTdUseCase;
import com.td.application.phieutrinhtd.TrinhThamKhaoPhieuTrinhTdUseCase;
import com.td.application.viewquery.ViewQueryService;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Port module "Phiếu trình thẩm định" — smoke test cho các endpoint chính của
 * {@link PhieuTrinhTdController}, đúng khuôn {@code HoSoLuuTruControllerTest}.
 */
@WebMvcTest(controllers = PhieuTrinhTdController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PhieuTrinhTdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddPhieuTrinhTdDynamicDocumentUseCase addPhieuTrinhTdDynamicDocumentUseCase;
    @MockBean
    private GetPhieuTrinhTdChiTietUseCase getPhieuTrinhTdChiTietUseCase;
    @MockBean
    private CapNhatPhieuTrinhTdUseCase capNhatPhieuTrinhTdUseCase;
    @MockBean
    private DeletePhieuTrinhTdUseCase deletePhieuTrinhTdUseCase;
    @MockBean
    private RestorePhieuTrinhTdUseCase restorePhieuTrinhTdUseCase;
    @MockBean
    private TrinhThamKhaoPhieuTrinhTdUseCase trinhThamKhaoPhieuTrinhTdUseCase;
    @MockBean
    private ChuyenTrangThaiPhieuTrinhTdUseCase chuyenTrangThaiPhieuTrinhTdUseCase;
    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void taoPhieuTrinh_shouldDelegateAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(addPhieuTrinhTdDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/phieutrinhtd/TaoPhieuTrinh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Phieu trinh A\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void xemChiTiet_shouldDelegateAndReturn200() throws Exception {
        when(getPhieuTrinhTdChiTietUseCase.execute(any()))
            .thenReturn(Result.success(Map.of("id", UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/phieutrinhtd/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void luuPhieuTrinh_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(capNhatPhieuTrinhTdUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/phieutrinhtd/LuuPhieuTrinh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void luuPhieuTrinh_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/phieutrinhtd/LuuPhieuTrinh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void xoaPhieuTrinh_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(deletePhieuTrinhTdUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/phieutrinhtd/XoaPhieuTrinh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + id + "\"]"))
            .andExpect(status().isOk());
    }

    @Test
    void phucHoiPhieuTrinh_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(restorePhieuTrinhTdUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/phieutrinhtd/PhucHoiPhieuTrinh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + id + "\"]"))
            .andExpect(status().isOk());
    }

    @Test
    void trinhThamKhao_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(trinhThamKhaoPhieuTrinhTdUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/phieutrinhtd/TrinhThamKhao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void chuyenTrangThai_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(chuyenTrangThaiPhieuTrinhTdUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/phieutrinhtd/ChuyenTrangThai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"targetStatus\":\"5\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void chuyenTrangThai_withoutTargetStatus_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/phieutrinhtd/ChuyenTrangThai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getView_shouldDelegateToViewQueryService() throws Exception {
        when(viewQueryService.execute(org.mockito.ArgumentMatchers.eq("phieutrinhtd"), any()))
            .thenReturn(new com.td.application.common.models.PaginationResponse<>(List.of(), 1, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/phieutrinhtd/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"phieutrinhtd_toanbo\",\"keys\":[\"nguyenvana\"]}"))
            .andExpect(status().isOk());
    }
}
