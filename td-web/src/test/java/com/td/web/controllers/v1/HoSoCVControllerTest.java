package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.hosocv.CapNhatHoSoUseCase;
import com.td.application.hosocv.ChuyenLuuTruHoSoUseCase;
import com.td.application.hosocv.DeleteHoSoUseCase;
import com.td.application.hosocv.DownloadFilesHoSoCVUseCase;
import com.td.application.hosocv.DuyetHoSoUseCase;
import com.td.application.hosocv.GanKemVanBanUseCase;
import com.td.application.hosocv.GetHoSoChiTietUseCase;
import com.td.application.hosocv.GetTokenLgspUseCase;
import com.td.application.hosocv.GopYPhoiHopUseCase;
import com.td.application.hosocv.HoSoFileGroupDto;
import com.td.application.hosocv.KetThucHoSoUseCase;
import com.td.application.hosocv.RestoreHoSoUseCase;
import com.td.application.hosocv.TaoHoSoCVRequest;
import com.td.application.hosocv.TaoHoSoCVUseCase;
import com.td.application.hosocv.TrinhDuyetHoSoUseCase;
import com.td.application.hosocv.XuLyLaiHoSoUseCase;
import com.td.application.viewquery.ViewQueryService;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-240 (HoSoCV Core) — Definition of Done: tạo hồ sơ công việc, gắn văn bản đến/đi/dự thảo,
 * danh sách hồ sơ theo view, download file từ hồ sơ — tất cả tương thích khuôn dạng API
 * {@code /api/v1/hosocv/*}.
 */
@WebMvcTest(controllers = HoSoCVController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HoSoCVControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaoHoSoCVUseCase taoHoSoCVUseCase;
    @MockBean
    private GanKemVanBanUseCase ganKemVanBanUseCase;
    @MockBean
    private DownloadFilesHoSoCVUseCase downloadFilesHoSoCVUseCase;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private GetHoSoChiTietUseCase getHoSoChiTietUseCase;
    @MockBean
    private CapNhatHoSoUseCase capNhatHoSoUseCase;
    @MockBean
    private DeleteHoSoUseCase deleteHoSoUseCase;
    @MockBean
    private RestoreHoSoUseCase restoreHoSoUseCase;
    @MockBean
    private TrinhDuyetHoSoUseCase trinhDuyetHoSoUseCase;
    @MockBean
    private DuyetHoSoUseCase duyetHoSoUseCase;
    @MockBean
    private ChuyenLuuTruHoSoUseCase chuyenLuuTruHoSoUseCase;
    @MockBean
    private KetThucHoSoUseCase ketThucHoSoUseCase;
    @MockBean
    private XuLyLaiHoSoUseCase xuLyLaiHoSoUseCase;
    @MockBean
    private GopYPhoiHopUseCase gopYPhoiHopUseCase;
    @MockBean
    private GetTokenLgspUseCase getTokenLgspUseCase;
    @MockBean
    private com.td.application.hosocv.LuuVanBanKhacUseCase luuVanBanKhacUseCase;
    @MockBean
    private com.td.application.hosocv.XemVanBanKhacUseCase xemVanBanKhacUseCase;
    @MockBean
    private com.td.application.hosocv.XoaVanBanKhacUseCase xoaVanBanKhacUseCase;

    @Test
    void taoHoSo_shouldDelegateToTaoHoSoCVUseCaseAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(taoHoSoCVUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/TaoHoSo")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", "Ho so xu ly don thu")
                .param("category", "Hanh chinh"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<TaoHoSoCVRequest> captor = ArgumentCaptor.forClass(TaoHoSoCVRequest.class);
        verify(taoHoSoCVUseCase).execute(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Ho so xu ly don thu");
        assertThat(captor.getValue().getFields()).containsEntry("category", "Hanh chinh");
    }

    @Test
    void taoHoSo_withDocumentTypeField_shouldKeepItAsDynamicField() throws Exception {
        // Bugfix 24/08/2026 (dong bo VbdenController): "document_type" la field nghiep vu "Loai van
        // ban" (Cong van/Bao cao/...), khong phai meta module discriminator - khong duoc strip.
        UUID id = UUID.randomUUID();
        when(taoHoSoCVUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/TaoHoSo")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", "Ho so xu ly don thu")
                .param("document_type", "Công văn"))
            .andExpect(status().isCreated());

        ArgumentCaptor<TaoHoSoCVRequest> captor = ArgumentCaptor.forClass(TaoHoSoCVRequest.class);
        verify(taoHoSoCVUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("document_type", "Công văn");
    }

    @Test
    void ganVanBan_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID hosocvId = UUID.randomUUID();
        when(ganKemVanBanUseCase.execute(any())).thenReturn(Result.success(hosocvId));

        mockMvc.perform(post("/api/v1/hosocv/GanVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + hosocvId + "\",\"vanBanIds\":[\"" + UUID.randomUUID()
                    + "\"],\"actionName\":\"ganvanbandi\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(hosocvId.toString()));
    }

    @Test
    void ganVanBan_withoutHosocvId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/GanVanBan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actionName\":\"ganvanbandi\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getAllFile_shouldDelegateToUseCaseAndReturn200() throws Exception {
        var group = new HoSoFileGroupDto("duthao", UUID.randomUUID(), List.of());
        when(downloadFilesHoSoCVUseCase.execute(any())).thenReturn(Result.success(List.of(group)));

        mockMvc.perform(post("/api/v1/hosocv/GetAllFile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vanBanLienQuan\":[{\"documentType\":\"duthao\",\"documentId\":\""
                    + UUID.randomUUID() + "\"}]}"))
            .andExpect(status().isOk());
    }

    @Test
    void getView_shouldDelegateToViewQueryServiceAndReturn200() throws Exception {
        when(viewQueryService.execute(org.mockito.ArgumentMatchers.eq("hosocv"), any()))
            .thenReturn(new com.td.application.common.models.PaginationResponse<>(List.of(), 1, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/hosocv/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"vwUser_All\",\"keys\":[\"nguyenvana\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    void getView_withUnsupportedViewname_shouldReturn400() throws Exception {
        when(viewQueryService.execute(org.mockito.ArgumentMatchers.eq("hosocv"), any()))
            .thenThrow(new IllegalArgumentException("Khong ho tro viewname: vwKhongTonTai"));

        mockMvc.perform(post("/api/v1/hosocv/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"vwKhongTonTai\"}"))
            .andExpect(status().isBadRequest());
    }

    // Mo rong BL-240 - test cho 10 endpoint moi.

    @Test
    void xemChiTiet_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(getHoSoChiTietUseCase.execute(any())).thenReturn(Result.success(java.util.Map.of("id", id)));

        mockMvc.perform(post("/api/v1/hosocv/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void luuHoSo_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(capNhatHoSoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/LuuHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"fields\":{\"category\":\"Hanh chinh\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(id.toString()));
    }

    @Test
    void luuHoSo_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/LuuHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void xoaHoSo_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(deleteHoSoUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/hosocv/XoaHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + id + "\"]"))
            .andExpect(status().isOk());
    }

    @Test
    void phucHoiHoSo_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(restoreHoSoUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/hosocv/PhucHoiHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + id + "\"]"))
            .andExpect(status().isOk());
    }

    @Test
    void trinhDuyet_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(trinhDuyetHoSoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/TrinhDuyet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void duyetHoSo_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(duyetHoSoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/DuyetHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + id + "\",\"duyet\":true}"))
            .andExpect(status().isOk());
    }

    @Test
    void duyetHoSo_withoutDuyetField_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/DuyetHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void chuyenLuuTru_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(chuyenLuuTruHoSoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/ChuyenLuuTru")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + id + "\",\"dmhsId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    // 08/09/2026 (port module hosoluutru): dmhsId gio bat buoc (@NotNull) - thieu phai tra 400.
    @Test
    void chuyenLuuTru_withoutDmhsId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/ChuyenLuuTru")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ketThucHoSo_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(ketThucHoSoUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/hosocv/KetThucHoSo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvIds\":[\"" + id + "\"],\"summary\":\"Xong\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void xuLyLai_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(xuLyLaiHoSoUseCase.execute(any())).thenReturn(Result.success(List.of(id)));

        mockMvc.perform(post("/api/v1/hosocv/XuLyLai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvIds\":[\"" + id + "\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    void gopYPhoiHop_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(gopYPhoiHopUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/hosocv/GopYPhoiHop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + id + "\",\"opinion\":\"Dong y\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void gopYPhoiHop_withoutOpinion_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/GopYPhoiHop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getTokenLgsp_shouldDelegateToUseCaseAndReturn200() throws Exception {
        when(getTokenLgspUseCase.execute()).thenReturn(Result.success("token-abc"));

        mockMvc.perform(post("/api/v1/hosocv/GetTokenLgsp"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("token-abc"));
    }
}
