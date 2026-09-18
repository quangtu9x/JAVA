package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.vbcddh.CapNhatTienDoUseCase;
import com.td.application.vbcddh.NhacViecItemDto;
import com.td.application.vbcddh.NhacViecResponseDto;
import com.td.application.vbcddh.NhacViecUseCase;
import com.td.application.vbcddh.NhiemVuThongKeItemDto;
import com.td.application.vbcddh.PhanCongNhiemVuUseCase;
import com.td.application.vbcddh.ThongKeNhiemVuUseCase;
import com.td.application.viewquery.ViewQueryService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-250 (VBCDDH Core) — Definition of Done: phân công nhiệm vụ, cập nhật tiến độ, thống kê nhiệm
 * vụ, nhắc việc tự động — tất cả tương thích API legacy {@code qlvb/vbcddh/*}.
 */
@WebMvcTest(controllers = VbcddhController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.td.web.config.GlobalExceptionHandler.class)
class VbcddhControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PhanCongNhiemVuUseCase phanCongNhiemVuUseCase;
    @MockBean
    private CapNhatTienDoUseCase capNhatTienDoUseCase;
    @MockBean
    private ThongKeNhiemVuUseCase thongKeNhiemVuUseCase;
    @MockBean
    private NhacViecUseCase nhacViecUseCase;
    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void phanCongNhiemVu_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID idVanBan = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(phanCongNhiemVuUseCase.execute(any())).thenReturn(Result.success(List.of(childId)));

        mockMvc.perform(post("/api/v1/vbcddh/PhanCongNhiemVu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idVanBan\":\"" + idVanBan + "\",\"assignments\":["
                    + "{\"donViThucHien\":\"Don vi A\",\"donViThucHienHide\":\"donvi_a\"}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data[0]").value(childId.toString()));

        ArgumentCaptor<com.td.application.vbcddh.PhanCongNhiemVuRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbcddh.PhanCongNhiemVuRequest.class);
        verify(phanCongNhiemVuUseCase).execute(captor.capture());
        assertThat(captor.getValue().getIdVanBan()).isEqualTo(idVanBan);
        assertThat(captor.getValue().getAssignments()).hasSize(1);
    }

    @Test
    void phanCongNhiemVu_withoutAssignments_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbcddh/PhanCongNhiemVu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idVanBan\":\"" + UUID.randomUUID() + "\",\"assignments\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void capNhatTienDo_shouldDelegateToUseCaseAndReturn201() throws Exception {
        UUID childId = UUID.randomUUID();
        when(capNhatTienDoUseCase.execute(any())).thenReturn(Result.success(childId));

        mockMvc.perform(post("/api/v1/vbcddh/CapNhatTienDo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idNhiemVu\":\"" + UUID.randomUUID() + "\",\"yKien\":\"Dang xu ly\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(childId.toString()));
    }

    @Test
    void capNhatTienDo_withoutIdNhiemVu_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/vbcddh/CapNhatTienDo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"yKien\":\"Dang xu ly\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void thongKeNhiemVu_shouldDelegateToUseCase() throws Exception {
        Map<String, List<NhiemVuThongKeItemDto>> data = Map.of(
            "donvi_a", List.of(new NhiemVuThongKeItemDto(2, "Chưa thực hiện", "green")));
        when(thongKeNhiemVuUseCase.execute(any())).thenReturn(Result.success(data));

        mockMvc.perform(post("/api/v1/vbcddh/ThongKeNhiemVu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiers\":[\"donvi_a\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.donvi_a[0].count").value(2));
    }

    @Test
    void nhacViec_shouldDelegateToUseCase() throws Exception {
        var response = new NhacViecResponseDto(
            List.of(new NhacViecItemDto("Chưa hoàn thành", 3L)),
            List.of(new NhacViecItemDto("Toàn bộ", 5L)));
        when(nhacViecUseCase.execute()).thenReturn(Result.success(response));

        mockMvc.perform(post("/api/v1/vbcddh/NhacViec"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dangThucHien[0].count").value(3))
            .andExpect(jsonPath("$.data.daHoanThanh[0].count").value(5));
    }
}
