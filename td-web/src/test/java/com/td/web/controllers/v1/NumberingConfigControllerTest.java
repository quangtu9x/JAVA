package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.numbering.GetNumberingConfigsUseCase;
import com.td.application.numbering.NumberingConfigDto;
import com.td.application.numbering.UpdateNumberingConfigRequest;
import com.td.application.numbering.UpdateNumberingConfigUseCase;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-104 (Document Numbering Service) — Definition of Done (4): API cấu hình format hoạt động.
 */
@WebMvcTest(controllers = NumberingConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NumberingConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetNumberingConfigsUseCase getNumberingConfigsUseCase;
    @MockBean
    private UpdateNumberingConfigUseCase updateNumberingConfigUseCase;

    @Test
    void listConfigs_shouldReturn200WithEnvelope() throws Exception {
        NumberingConfigDto dto = new NumberingConfigDto();
        dto.setDocumentType("vbden");
        dto.setFormat("{seq}/{year}");
        dto.setActive(true);
        when(getNumberingConfigsUseCase.execute()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/numbering-configs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data[0].documentType").value("vbden"))
            .andExpect(jsonPath("$.data[0].format").value("{seq}/{year}"));
    }

    @Test
    void updateConfig_shouldReturn200WithUpdatedConfig() throws Exception {
        NumberingConfigDto updated = new NumberingConfigDto();
        updated.setDocumentType("vbden");
        updated.setFormat("{seq:05d}/{year}");
        updated.setActive(true);
        when(updateNumberingConfigUseCase.execute(eq("vbden"), any())).thenReturn(Result.success(updated));

        UpdateNumberingConfigRequest request = new UpdateNumberingConfigRequest();
        request.setFormat("{seq:05d}/{year}");
        request.setActive(true);

        mockMvc.perform(put("/api/v1/numbering-configs/{documentType}", "vbden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.format").value("{seq:05d}/{year}"));

        verify(updateNumberingConfigUseCase).execute(eq("vbden"), any());
    }

    @Test
    void updateConfig_withUnknownDocumentType_shouldReturn400() throws Exception {
        when(updateNumberingConfigUseCase.execute(eq("khongtontai"), any()))
            .thenReturn(Result.failure("Không tìm thấy cấu hình numbering cho documentType: khongtontai"));

        UpdateNumberingConfigRequest request = new UpdateNumberingConfigRequest();
        request.setFormat("{seq}/{year}");

        mockMvc.perform(put("/api/v1/numbering-configs/{documentType}", "khongtontai")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void updateConfig_withBlankFormat_shouldReturn400ViaValidation() throws Exception {
        UpdateNumberingConfigRequest request = new UpdateNumberingConfigRequest();
        request.setFormat("  ");

        mockMvc.perform(put("/api/v1/numbering-configs/{documentType}", "vbden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
