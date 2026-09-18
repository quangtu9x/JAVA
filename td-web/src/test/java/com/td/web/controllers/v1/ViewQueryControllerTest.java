package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-102 (View Query Engine) — Definition of Done (1)/(3)/(4): {@code POST /api/v1/{dbType}/query}
 * hoạt động, phân trang và filter theo {@code keys} hoạt động.
 */
@WebMvcTest(controllers = ViewQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ViewQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void postQuery_choXuLy_shouldReturn200WithEnvelope() throws Exception {
        Map<String, Object> item = Map.of("id", "doc-1", "title", "Van ban cho xu ly");
        when(viewQueryService.execute(eq("vbden"), any())).thenReturn(
            new PaginationResponse<>(List.of(item), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        mockMvc.perform(post("/api/v1/vbden/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Van ban cho xu ly"))
            .andExpect(jsonPath("$.properties.pageNumber").value(0))
            .andExpect(jsonPath("$.properties.pageSize").value(20));

        verify(viewQueryService).execute(eq("vbden"), any());
    }

    @Test
    void postQuery_withUnsupportedViewname_shouldReturn400() throws Exception {
        when(viewQueryService.execute(any(), any()))
            .thenThrow(new IllegalArgumentException("Khong ho tro viewname: vwKhongTonTai"));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwKhongTonTai");

        mockMvc.perform(post("/api/v1/qlvb/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void postQuery_withBlankViewname_shouldReturn400ViaValidation() throws Exception {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("  ");

        mockMvc.perform(post("/api/v1/qlvb/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postQuery_userByUsername_shouldPassDbTypeAndRequestThrough() throws Exception {
        Map<String, Object> item = Map.of("username", "alice", "full_name", "Alice Nguyen");
        when(viewQueryService.execute(eq("qlvb"), any())).thenReturn(
            new PaginationResponse<>(List.of(item), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyusername");
        request.setKeys(List.of("alice"));

        mockMvc.perform(post("/api/v1/qlvb/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].username").value("alice"));
    }
}
