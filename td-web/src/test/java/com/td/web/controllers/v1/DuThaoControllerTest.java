package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.duthao.ChuyenThanhVanBanDiUseCase;
import com.td.application.duthao.DuThaoDaBanHanhUseCase;
import com.td.application.duthao.DuThaoVanThuKhongBanHanhUseCase;
import com.td.application.duthao.TraLaiDuThaoChoLanhDaoUseCase;
import com.td.application.duthao.TraLaiDuThaoChoNguoiSoanThaoUseCase;
import com.td.application.duthao.AddDuThaoEditHistoryUseCase;
import com.td.application.duthao.ListDuThaoEditHistoryUseCase;
import com.td.application.duthao.GanKemTepDuThaoUseCase;
import com.td.application.duthao.GanVanBanLienQuanDuThaoUseCase;
import com.td.application.duthao.XoaTraLoiVanBanDenDuThaoUseCase;
import com.td.application.duthao.XoaVanBanLienQuanDuThaoUseCase;
import com.td.application.duthao.GetDuThaoDetailUseCase;
import com.td.application.duthao.LuuDuThaoUseCase;
import com.td.application.duthao.TaoDuThaoUseCase;
import com.td.application.duthao.ThuHoiVanBanDiUseCase;
import com.td.application.duthao.TraLoiVanBanDenDuThaoUseCase;
import com.td.application.duthao.XinYKienPhoiHopUseCase;
import com.td.application.duthao.XuLyDuThaoUseCase;
import com.td.application.viewquery.ViewQueryRequest;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-230 (DuThao Core) — Definition of Done: tạo dự thảo, xin ý kiến phối hợp, chuyển thành văn
 * bản đi, thu hồi văn bản đi chờ ban hành — tất cả tương thích khuôn dạng API
 * {@code /api/v1/duthao/*}.
 */
@WebMvcTest(controllers = DuThaoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DuThaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaoDuThaoUseCase taoDuThaoUseCase;
    @MockBean
    private GetDuThaoDetailUseCase getDuThaoDetailUseCase;
    @MockBean
    private LuuDuThaoUseCase luuDuThaoUseCase;
    @MockBean
    private XinYKienPhoiHopUseCase xinYKienPhoiHopUseCase;
    @MockBean
    private ChuyenThanhVanBanDiUseCase chuyenThanhVanBanDiUseCase;
    @MockBean
    private DuThaoDaBanHanhUseCase duThaoDaBanHanhUseCase;
    @MockBean
    private DuThaoVanThuKhongBanHanhUseCase duThaoVanThuKhongBanHanhUseCase;
    @MockBean
    private TraLaiDuThaoChoLanhDaoUseCase traLaiDuThaoChoLanhDaoUseCase;
    @MockBean
    private TraLaiDuThaoChoNguoiSoanThaoUseCase traLaiDuThaoChoNguoiSoanThaoUseCase;
    @MockBean
    private ThuHoiVanBanDiUseCase thuHoiVanBanDiUseCase;
    @MockBean
    private XuLyDuThaoUseCase xuLyDuThaoUseCase;
    @MockBean
    private GanKemTepDuThaoUseCase ganKemTepDuThaoUseCase;
    @MockBean
    private AddDuThaoEditHistoryUseCase addDuThaoEditHistoryUseCase;
    @MockBean
    private ListDuThaoEditHistoryUseCase listDuThaoEditHistoryUseCase;
    @MockBean
    private TraLoiVanBanDenDuThaoUseCase traLoiVanBanDenDuThaoUseCase;
    @MockBean
    private XoaTraLoiVanBanDenDuThaoUseCase xoaTraLoiVanBanDenDuThaoUseCase;
    @MockBean
    private GanVanBanLienQuanDuThaoUseCase ganVanBanLienQuanDuThaoUseCase;
    @MockBean
    private XoaVanBanLienQuanDuThaoUseCase xoaVanBanLienQuanDuThaoUseCase;
    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void taoDuThao_shouldDelegateToTaoDuThaoUseCaseAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(taoDuThaoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/duthao/TaoDuThao")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Du thao cong van kiem tra")
                .param("unit_in_charge", "So Noi Vu"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<com.td.application.duthao.TaoDuThaoRequest> captor =
            ArgumentCaptor.forClass(com.td.application.duthao.TaoDuThaoRequest.class);
        verify(taoDuThaoUseCase).execute(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Du thao cong van kiem tra");
        assertThat(captor.getValue().getFields()).containsEntry("unit_in_charge", "So Noi Vu");
    }

    @Test
    void taoDuThao_withDocumentTypeField_shouldKeepItAsDynamicField() throws Exception {
        // Bugfix 24/08/2026 (dong bo VbdenController): "document_type" la field nghiep vu "Loai van
        // ban" (Cong van/Bao cao/...), khong phai meta module discriminator - khong duoc strip.
        UUID id = UUID.randomUUID();
        when(taoDuThaoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/duthao/TaoDuThao")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("subject", "Du thao cong van kiem tra")
                .param("document_type", "Công văn"))
            .andExpect(status().isCreated());

        ArgumentCaptor<com.td.application.duthao.TaoDuThaoRequest> captor =
            ArgumentCaptor.forClass(com.td.application.duthao.TaoDuThaoRequest.class);
        verify(taoDuThaoUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("document_type", "Công văn");
    }

    @Test
    void xemChiTiet_shouldDelegateToGetDuThaoDetailUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subject", "Du thao cong van kiem tra");
        when(getDuThaoDetailUseCase.execute(any())).thenReturn(Result.success(data));

        mockMvc.perform(post("/api/v1/duthao/XemChiTiet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.subject").value("Du thao cong van kiem tra"));
    }

    @Test
    void luu_shouldDelegateToLuuDuThaoUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(luuDuThaoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/duthao/Luu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"fields\":{\"subject\":\"Moi\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(id.toString()));
    }

    @Test
    void luu_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/Luu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void xinYKienPhoiHop_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(xinYKienPhoiHopUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/duthao/XinYKienPhoiHop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"nguoiPhoiHop\":[\"nguyenvana\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(id.toString()));
    }

    @Test
    void xinYKienPhoiHop_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/XinYKienPhoiHop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void chuyenThanhVanBanDi_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID vbdiId = UUID.randomUUID();
        when(chuyenThanhVanBanDiUseCase.execute(any())).thenReturn(Result.success(vbdiId));

        mockMvc.perform(post("/api/v1/duthao/ChuyenThanhVanBanDi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(vbdiId.toString()));
    }

    @Test
    void thuHoiVanBanDi_shouldDelegateToUseCaseAndReturn200() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(thuHoiVanBanDiUseCase.execute(any())).thenReturn(Result.success(draftId));

        mockMvc.perform(post("/api/v1/duthao/ThuHoiVanBanDi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + draftId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(draftId.toString()));
    }

    @Test
    void thuHoiVanBanDi_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/ThuHoiVanBanDi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // DT-001 (tai lieu phan tich 04 muc 12) - xem javadoc DuThaoController#xuLyDuThao.
    @Test
    void xuLyDuThao_shouldDelegateToXuLyDuThaoUseCaseAndReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(xuLyDuThaoUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/duthao/XuLyDuThao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"action\":\"chuyenduyet\",\"receiver\":\"Nguyen Van A\",\"receiverUsername\":\"nguyenvana\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(id.toString()));
    }

    @Test
    void xuLyDuThao_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/XuLyDuThao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"chuyenduyet\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void xuLyDuThao_withoutAction_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/XuLyDuThao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    // DT-007 (tiep, 26/08/2026) - xem javadoc DuThaoController#ganKemTep.
    @Test
    void ganKemTep_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        when(ganKemTepDuThaoUseCase.execute(any())).thenReturn(Result.success(processId));

        mockMvc.perform(post("/api/v1/duthao/GanKemTep")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"opinion\":\"Bo sung tep phieu trinh\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(processId.toString()));
    }

    @Test
    void ganKemTep_withoutId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/GanKemTep")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // DT-002 Slice dau (tai lieu phan tich 04 muc 12) - xem javadoc DuThaoController#getView.
    @Test
    void getView_shouldDelegateToViewQueryServiceWithDuThaoDbType() throws Exception {
        when(viewQueryService.execute(eq("duthao"), any()))
            .thenReturn(new PaginationResponse<>(List.of(Map.of("name", "DT-001")), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Creator");
        request.setKeys(List.of("username_test"));

        mockMvc.perform(post("/api/v1/duthao/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"vwUser_Creator\",\"keys\":[\"username_test\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("DT-001"));

        verify(viewQueryService).execute(eq("duthao"), any());
    }
}
