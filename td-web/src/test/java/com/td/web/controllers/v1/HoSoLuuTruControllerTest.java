package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.hosoluutru.AddHoSoLuuTruDynamicDocumentUseCase;
import com.td.application.hosoluutru.CapNhatHoSoLuuTruUseCase;
import com.td.application.hosoluutru.ChuyenLuuTruLichSuUseCase;
import com.td.application.hosoluutru.ChuyenLuuTruNoiBoUseCase;
import com.td.application.hosoluutru.DangKyMuonTaiLieuUseCase;
import com.td.application.hosoluutru.DeleteHoSoLuuTruUseCase;
import com.td.application.hosoluutru.DuyetPhieuYeuCauUseCase;
import com.td.application.hosoluutru.GetDanhMucLuuTruListUseCase;
import com.td.application.hosoluutru.GetHoSoLuuTruChiTietUseCase;
import com.td.application.hosoluutru.LuuDanhMucLuuTruUseCase;
import com.td.application.hosoluutru.RestoreHoSoLuuTruUseCase;
import com.td.application.hosoluutru.ThemVanBanLuuTruUseCase;
import com.td.application.hosoluutru.ThuHoiHoSoDaChoMuonUseCase;
import com.td.application.hosoluutru.ThuHoiLuuTruLichSuUseCase;
import com.td.application.hosoluutru.ThuHoiYeuCauMuonUseCase;
import com.td.application.hosoluutru.TiepNhanHoSoLuuTruUseCase;
import com.td.application.hosoluutru.TraLaiHoSoLuuTruUseCase;
import com.td.application.hosoluutru.TraTaiLieuUseCase;
import com.td.application.hosoluutru.XoaDanhMucLuuTruUseCase;
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
 * Port module "Hồ sơ lưu trữ" — Definition of Done: smoke test cho các endpoint chính của
 * {@link HoSoLuuTruController} (tiếp nhận/từ chối, CRUD hồ sơ, lifecycle nội bộ, phiếu yêu cầu),
 * đúng khuôn {@code HoSoCVControllerTest}.
 */
@WebMvcTest(controllers = HoSoLuuTruController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HoSoLuuTruControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddHoSoLuuTruDynamicDocumentUseCase addHoSoLuuTruDynamicDocumentUseCase;
    @MockBean
    private GetHoSoLuuTruChiTietUseCase getHoSoLuuTruChiTietUseCase;
    @MockBean
    private CapNhatHoSoLuuTruUseCase capNhatHoSoLuuTruUseCase;
    @MockBean
    private DeleteHoSoLuuTruUseCase deleteHoSoLuuTruUseCase;
    @MockBean
    private RestoreHoSoLuuTruUseCase restoreHoSoLuuTruUseCase;
    @MockBean
    private TiepNhanHoSoLuuTruUseCase tiepNhanHoSoLuuTruUseCase;
    @MockBean
    private TraLaiHoSoLuuTruUseCase traLaiHoSoLuuTruUseCase;
    @MockBean
    private ChuyenLuuTruNoiBoUseCase chuyenLuuTruNoiBoUseCase;
    @MockBean
    private ChuyenLuuTruLichSuUseCase chuyenLuuTruLichSuUseCase;
    @MockBean
    private ThuHoiLuuTruLichSuUseCase thuHoiLuuTruLichSuUseCase;
    @MockBean
    private ThemVanBanLuuTruUseCase themVanBanLuuTruUseCase;
    @MockBean
    private LuuDanhMucLuuTruUseCase luuDanhMucLuuTruUseCase;
    @MockBean
    private GetDanhMucLuuTruListUseCase getDanhMucLuuTruListUseCase;
    @MockBean
    private XoaDanhMucLuuTruUseCase xoaDanhMucLuuTruUseCase;
    @MockBean
    private DangKyMuonTaiLieuUseCase dangKyMuonTaiLieuUseCase;
    @MockBean
    private DuyetPhieuYeuCauUseCase duyetPhieuYeuCauUseCase;
    @MockBean
    private ThuHoiYeuCauMuonUseCase thuHoiYeuCauMuonUseCase;
    @MockBean
    private TraTaiLieuUseCase traTaiLieuUseCase;
    @MockBean
    private ThuHoiHoSoDaChoMuonUseCase thuHoiHoSoDaChoMuonUseCase;
    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void xemChiTiet_shouldDelegateAndReturn200() throws Exception {
        when(getHoSoLuuTruChiTietUseCase.execute(any())).thenReturn(Result.success(Map.of("id", UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/hosoluutru/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void tiepNhan_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(tiepNhanHoSoLuuTruUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosoluutru/TiepNhan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosoLuuTruId\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void traLai_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(traLaiHoSoLuuTruUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosoluutru/TraLai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosoLuuTruId\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void tiepNhan_withoutHosoLuuTruId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/hosoluutru/TiepNhan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void xoaHoSo_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(deleteHoSoLuuTruUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/hosoluutru/XoaHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + id + "\"]"))
            .andExpect(status().isOk());
    }

    @Test
    void duyetPhieuYeuCau_shouldDelegateAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(duyetPhieuYeuCauUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosoluutru/DuyetPhieuYeuCau")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phieuId\":\"" + id + "\",\"duyet\":true}"))
            .andExpect(status().isOk());
    }
}
